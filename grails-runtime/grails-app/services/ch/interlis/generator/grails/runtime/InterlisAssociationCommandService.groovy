package ch.interlis.generator.grails.runtime

import groovy.util.logging.Slf4j

import grails.gorm.transactions.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException



/**
 * Transactional command service for the association quick-link UX.
 *
 * <p>Only associations that the planner classified as {@code createMode == QUICK}
 * (binary {@code LINK_ENTITY} associations without own attributes) can be created
 * or deleted here. Every class-, property- and table name originates exclusively
 * from the generated {@link InterlisAssociationRegistry}; the client only supplies
 * the context id, the participant id, the target role name and the target/association id.
 *
 * <p>All outcomes are reported through structured result maps
 * ({@code [success, status, code, message, messageCode, associationId, fieldErrors]}).
 *
 * <p><strong>Concurrency:</strong> Cardinality checks use a separate count query
 * before the insert/delete. This introduces a time-of-check-to-time-of-use window.
 * {@code lockOrGet} uses best-effort pessimistic locking (falls back to {@code get()}
 * when {@code lock()} is not available). In case of a concurrent violation, the
 * database-level constraints (unique, foreign key, NOT NULL) serve as a safety net
 * and are surfaced as {@code DATA_INTEGRITY} errors.
 */
@Slf4j
@Transactional
class InterlisAssociationCommandService {

    def grailsApplication

    Map<String, Object> createQuickLink(Class participantType,
                                        Serializable participantId,
                                        String contextId,
                                        String targetRoleName,
                                        Serializable targetId) {
        Map<String, Object> context
        try {
            context = InterlisAssociationRegistrySupport.requireContext(participantType, contextId)
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            return failure(404, "OWNERSHIP_MISMATCH", "Die Zuordnung gehört nicht zu diesem Datensatz.")
        } catch (InterlisAssociationRegistrySupport.AssociationContextNotFoundException e) {
            return failure(400, "CONTEXT_INVALID", "Ungültiger Zuordnungskontext.")
        }

        Map<String, Object> association = InterlisAssociationRegistrySupport.requireAssociation(context.associationName)

        if (context.createMode != "QUICK" || context.writable != true) {
            return failure(409, "READ_ONLY", "Diese Assoziation ist in der generischen Oberfläche nur lesbar.")
        }
        if (association.storageKind != "LINK_ENTITY") {
            return failure(409, "READ_ONLY", "Diese Assoziation ist in der generischen Oberfläche nur lesbar.")
        }

        List<String> editableRoles = (context.editableRoles ?: []) as List<String>
        if (targetRoleName == null || !editableRoles.contains(targetRoleName)) {
            return failure(400, "TARGET_ROLE_INVALID", "Die Zielrolle ist für diesen Kontext nicht zulässig.")
        }
        Map<String, Object> targetRole = InterlisAssociationRegistrySupport.role(association, targetRoleName)
        if (targetRole == null) {
            return failure(400, "TARGET_ROLE_INVALID", "Die Zielrolle ist für diesen Kontext nicht zulässig.")
        }

        String fixedProperty = context.fixedProperty
        String targetProperty = targetRole.property
        if (fixedProperty == null || targetProperty == null || fixedProperty == targetProperty) {
            return failure(400, "TARGET_ROLE_INVALID", "Die Zuordnung konnte nicht erstellt werden.")
        }

        Object participant = lockOrGet(participantType, participantId)
        if (participant == null) {
            return failure(404, "OWNER_NOT_FOUND", "Der Datensatz wurde nicht gefunden.")
        }
        if (!canCreateAssociation(participant, context)) {
            return failure(403, "FORBIDDEN", "Die Zuordnung konnte nicht erstellt werden.")
        }

        Class associationType = InterlisAssociationRegistrySupport.resolveAssociationClass(grailsApplication, context)
        Class targetType = InterlisAssociationRegistrySupport.resolveDomainClass(grailsApplication, targetRole.targetDomainClass)
        if (associationType == null || targetType == null) {
            return failure(409, "READ_ONLY", "Diese Assoziation ist in der generischen Oberfläche nur lesbar.")
        }

        Object target = targetType.get(targetId)
        if (target == null) {
            return failure(404, "TARGET_NOT_FOUND", "Das ausgewählte Objekt wurde nicht gefunden.")
        }

        if (isDuplicate(associationType, fixedProperty, participantId, targetProperty, targetId)) {
            return failure(409, "DUPLICATE_LINK", "Diese Zuordnung besteht bereits.")
        }

        Map<String, Object> cardinalityError = validateCreateCardinality(
            associationType, context, fixedProperty, participantId)
        if (cardinalityError != null) {
            return cardinalityError
        }

        Object instance = associationType.newInstance()
        assignRole(instance, fixedProperty, participant)
        assignRole(instance, targetProperty, target)

        if (!instance.validate()) {
            return validationFailure(instance)
        }

        try {
            instance.save(flush: true, failOnError: false)
        } catch (DataIntegrityViolationException e) {
            log.warn("Quick-link create failed for association ${context.associationName} context ${contextId}: ${e.message}")
            return failure(409, "DATA_INTEGRITY", "Die Zuordnung konnte nicht erstellt werden.")
        } catch (OptimisticLockingFailureException e) {
            log.warn("Quick-link create optimistic lock failure for association ${context.associationName} context ${contextId}")
            return failure(409, "CONCURRENT_MODIFICATION",
                "Die Zuordnung konnte aufgrund einer gleichzeitigen Änderung nicht erstellt werden.")
        }

        if (instance.hasErrors() || instance.id == null) {
            return validationFailure(instance)
        }

        return [
            success: true,
            status: 201,
            code: "CREATED",
            messageCode: "interlis.association.created",
            message: InterlisMessageSupport.text(grailsApplication, "ili2grails.association.created", "Die Zuordnung wurde erstellt."),
            associationId: instance.id?.toString(),
            fieldErrors: [:]
        ]
    }

    Map<String, Object> deleteLink(Class participantType,
                                   Serializable participantId,
                                   String contextId,
                                   Serializable associationId) {
        Map<String, Object> context
        try {
            context = InterlisAssociationRegistrySupport.requireContext(participantType, contextId)
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            return failure(404, "OWNERSHIP_MISMATCH", "Die Zuordnung gehört nicht zu diesem Datensatz.")
        } catch (InterlisAssociationRegistrySupport.AssociationContextNotFoundException e) {
            return failure(400, "CONTEXT_INVALID", "Ungültiger Zuordnungskontext.")
        }

        Map<String, Object> association = InterlisAssociationRegistrySupport.requireAssociation(context.associationName)

        if (context.removable != true || context.writable != true) {
            return failure(409, "READ_ONLY", "Diese Zuordnung kann nicht entfernt werden.")
        }
        if (hasCompositionRole(association)) {
            return failure(409, "COMPOSITION_DELETE_BLOCKED",
                "Diese Zuordnung kann in der generischen Oberfläche nicht entfernt werden.")
        }
        if (hasExternalRole(association)) {
            return failure(409, "EXTERNAL_DELETE_BLOCKED",
                "Diese externe Zuordnung kann in der generischen Oberfläche nicht entfernt werden.")
        }

        Object participant = lockOrGet(participantType, participantId)
        if (participant == null) {
            return failure(404, "OWNER_NOT_FOUND", "Der Datensatz wurde nicht gefunden.")
        }

        Class associationType = InterlisAssociationRegistrySupport.resolveAssociationClass(grailsApplication, context)
        if (associationType == null) {
            return failure(409, "READ_ONLY", "Diese Zuordnung kann nicht entfernt werden.")
        }

        Object instance = associationType.get(associationId)
        if (instance == null) {
            return failure(404, "ASSOCIATION_NOT_FOUND", "Die Zuordnung wurde nicht gefunden.")
        }

        try {
            verifyAssociationBelongsToParticipant(instance, context, participant)
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            log.warn("Blocked association delete manipulation attempt: association ${associationId} does not belong " +
                "to ${participantType.simpleName}#${participantId} (context ${contextId})")
            return failure(404, "OWNERSHIP_MISMATCH", "Die Zuordnung gehört nicht zu diesem Datensatz.")
        }

        if (!canDeleteAssociation(participant, instance, context)) {
            return failure(403, "FORBIDDEN", "Die Zuordnung kann nicht entfernt werden.")
        }

        Map<String, Object> cardinalityError = validateDeleteCardinality(
            associationType, context, context.fixedProperty, participantId)
        if (cardinalityError != null) {
            return cardinalityError
        }

        try {
            instance.delete(flush: true)
        } catch (DataIntegrityViolationException e) {
            log.warn("Quick-link delete failed for association ${context.associationName} id ${associationId}: ${e.message}")
            return failure(409, "DATA_INTEGRITY", "Die Zuordnung kann nicht entfernt werden, weil abhängige Daten vorhanden sind.")
        } catch (OptimisticLockingFailureException e) {
            log.warn("Quick-link delete optimistic lock failure for association ${context.associationName} id ${associationId}")
            return failure(409, "CONCURRENT_MODIFICATION",
                "Die Zuordnung kann aufgrund einer gleichzeitigen Änderung nicht entfernt werden.")
        }

        return [
            success: true,
            status: 204,
            code: "DELETED",
            messageCode: "interlis.association.deleted",
            message: InterlisMessageSupport.text(grailsApplication, "ili2grails.association.deleted", "Die Zuordnung wurde entfernt."),
            fieldErrors: [:]
        ]
    }

    protected Map<String, Object> validateCreateCardinality(Class associationType,
                                                            Map<String, Object> context,
                                                            String fixedProperty,
                                                            Serializable participantId) {
        Integer max = asInteger(context.perspectiveMax)
        if (max == null || max == -1) {
            return null
        }
        long current = countLinks(associationType, fixedProperty, participantId)
        if (current + 1 > max) {
            return failure(409, "CARDINALITY_MAX_EXCEEDED",
                "Für dieses Objekt ist bereits die maximal zulässige Anzahl Zuordnungen vorhanden.")
        }
        return null
    }

    protected Map<String, Object> validateDeleteCardinality(Class associationType,
                                                            Map<String, Object> context,
                                                            String fixedProperty,
                                                            Serializable participantId) {
        Integer min = asInteger(context.perspectiveMin)
        if (min == null || min <= 0) {
            return null
        }
        long current = countLinks(associationType, fixedProperty, participantId)
        if (current - 1 < min) {
            return failure(409, "CARDINALITY_MIN_VIOLATED",
                "Die Zuordnung kann nicht entfernt werden, weil mindestens eine Beziehung bestehen muss.")
        }
        return null
    }

    protected void verifyAssociationBelongsToParticipant(Object associationInstance,
                                                         Map<String, Object> context,
                                                         Object participant) {
        String fixedProperty = context.fixedProperty
        Object fixedValue = associationInstance."${fixedProperty}"
        String fixedId = fixedValue?.id?.toString()
        String participantId = participant?.id?.toString()
        if (fixedId == null || participantId == null || fixedId != participantId) {
            throw new InterlisAssociationRegistrySupport.AssociationOwnershipException(
                "Association ${associationInstance?.id} does not belong to participant ${participantId}")
        }
    }

    protected void assignRole(Object associationInstance, String propertyName, Object value) {
        associationInstance."${propertyName}" = value
    }

    protected Object loadRequired(Class type, Serializable id, String fieldLabel) {
        if (type == null || id == null) {
            return null
        }
        return type.get(id)
    }

    protected boolean canCreateAssociation(Object participant, Map<String, Object> context) {
        return true
    }

    protected boolean canDeleteAssociation(Object participant, Object associationInstance, Map<String, Object> context) {
        return true
    }

    private Object lockOrGet(Class type, Serializable id) {
        if (type == null || id == null) {
            return null
        }
        try {
            Object locked = type.lock(id)
            if (locked != null) {
                return locked
            }
        } catch (Exception ignored) {
            // Locking is best-effort; fall back to a plain get and rely on DB constraints.
        }
        return type.get(id)
    }

    private boolean isDuplicate(Class associationType, String fixedProperty, Serializable participantId,
                                String targetProperty, Serializable targetId) {
        long count = associationType.createCriteria().get {
            eq(fixedProperty + ".id", participantId)
            eq(targetProperty + ".id", targetId)
            projections {
                count("id")
            }
        } as Long ?: 0L
        return count > 0
    }

    private long countLinks(Class associationType, String fixedProperty, Serializable participantId) {
        return associationType.createCriteria().get {
            eq(fixedProperty + ".id", participantId)
            projections {
                count("id")
            }
        } as Long ?: 0L
    }

    private boolean hasCompositionRole(Map<String, Object> association) {
        List<Map<String, Object>> roles = association.roles as List<Map<String, Object>>
        return roles != null && roles.any { it.composition == true }
    }

    private boolean hasExternalRole(Map<String, Object> association) {
        List<Map<String, Object>> roles = association.roles as List<Map<String, Object>>
        return roles != null && roles.any { it.external == true }
    }

    private Map<String, Object> validationFailure(Object instance) {
        Map<String, String> fieldErrors = [:]
        instance.errors?.fieldErrors?.each { error ->
            fieldErrors[error.field] = error.defaultMessage ?: error.code
        }
        return [
            success: false,
            status: 422,
            code: "VALIDATION_FAILED",
            messageCode: "interlis.association.validationFailed",
            message: InterlisMessageSupport.text(grailsApplication, "ili2grails.association.validationFailed", "Die Zuordnung konnte nicht gespeichert werden."),
            fieldErrors: fieldErrors
        ]
    }

    private Map<String, Object> failure(int status, String code, String message) {
        return [
            success: false,
            status: status,
            code: code,
            message: InterlisMessageSupport.text(grailsApplication, "ili2grails.association.error.${code}", message),
            fieldErrors: [:]
        ]
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null
        }
        if (value instanceof Number) {
            return ((Number) value).intValue()
        }
        try {
            return Integer.valueOf(value.toString())
        } catch (NumberFormatException ignored) {
            return null
        }
    }
}

package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.command.AssociationCommandResult
import ch.interlis.generator.grails.runtime.api.command.CommandCode
import ch.interlis.generator.grails.runtime.api.command.CommandStatus
import ch.interlis.generator.grails.runtime.api.command.FieldError
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationCreateMode
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind
import ch.interlis.generator.grails.runtime.api.lifecycle.InterlisLifecycleHooks
import ch.interlis.generator.grails.runtime.api.persistence.LockResult
import ch.interlis.generator.grails.runtime.api.persistence.LockStatus
import ch.interlis.generator.grails.runtime.api.persistence.RuntimeRecordLoader
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry
import ch.interlis.generator.grails.runtime.api.security.AssociationOperationContext
import ch.interlis.generator.grails.runtime.api.security.InterlisAuthorizationPolicy
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException

/**
 * Transactional command service for the association quick-link UX.
 *
 * <p>Only associations that the planner classified as {@code createMode == QUICK}
 * (binary {@code LINK_ENTITY} associations without own attributes) can be created
 * or deleted here. Every class-, property- and table name originates exclusively
 * from the validated typed registry descriptors; the client only supplies the
 * context id, the participant id, the target role name and the target/association id.</p>
 *
 * <p>All outcomes are reported through the typed {@link AssociationCommandResult}.</p>
 *
 * <p><strong>Concurrency:</strong> Cardinality checks use a separate count query
 * before the insert/delete. {@link RuntimeRecordLoader} provides pessimistic
 * locking with explicit lock semantics: only {@link LockStatus#LOCK_UNSUPPORTED}
 * falls back to a plain read, unexpected lock failures are never swallowed.</p>
 */
@Slf4j
@Transactional
class InterlisAssociationCommandService {

    def grailsApplication
    InterlisRuntimeRegistry runtimeRegistry
    InterlisAuthorizationPolicy authorizationPolicy
    InterlisLifecycleHooks lifecycleHooks
    RuntimeRecordLoader recordLoader

    AssociationCommandResult createQuickLink(Class participantType,
                                             Serializable participantId,
                                             String contextId,
                                             String targetRoleName,
                                             Serializable targetId) {
        AssociationContextDescriptor context = requireContextOrFail(participantType, contextId)
        if (context == null) {
            return null
        }

        AssociationDescriptor association = requireAssociationOrFail(context)
        if (association == null) {
            return null
        }

        if (context.createMode() != AssociationCreateMode.QUICK || !context.writable()) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.READ_ONLY,
                "Diese Assoziation ist in der generischen Oberfläche nur lesbar.")
        }
        if (association.storageKind() != AssociationStorageKind.LINK_ENTITY) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.READ_ONLY,
                "Diese Assoziation ist in der generischen Oberfläche nur lesbar.")
        }

        List<String> editableRoles = context.editableRoleNames()
        if (targetRoleName == null || !editableRoles.contains(targetRoleName)) {
            return failure(400, CommandStatus.CLIENT_ERROR, CommandCode.TARGET_ROLE_INVALID,
                "Die Zielrolle ist für diesen Kontext nicht zulässig.")
        }
        AssociationRoleDescriptor targetRole = association.role(targetRoleName).orElse(null)
        if (targetRole == null) {
            return failure(400, CommandStatus.CLIENT_ERROR, CommandCode.TARGET_ROLE_INVALID,
                "Die Zielrolle ist für diesen Kontext nicht zulässig.")
        }

        String fixedProperty = context.fixedPropertyName()
        String targetProperty = targetRole.propertyName()
        if (fixedProperty == null || targetProperty == null || fixedProperty == targetProperty) {
            return failure(400, CommandStatus.CLIENT_ERROR, CommandCode.TARGET_ROLE_INVALID,
                "Die Zuordnung konnte nicht erstellt werden.")
        }

        LoadOutcome participantOutcome = loadForWrite(participantType, participantId)
        if (participantOutcome.failureCode() != null) {
            return failure(409, CommandStatus.CONFLICT, participantOutcome.failureCode(),
                "Der Datensatz wurde gleichzeitig geändert. Bitte erneut versuchen.")
        }
        Object participant = participantOutcome.record()
        if (participant == null) {
            return failure(404, CommandStatus.NOT_FOUND, CommandCode.OWNER_NOT_FOUND,
                "Der Datensatz wurde nicht gefunden.")
        }

        Class associationType = runtimeRegistry.resolveDomainClass(association.domainClassName())
        Class targetType = runtimeRegistry.resolveDomainClass(targetRole.targetDomainClassName())
        if (associationType == null || targetType == null) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.READ_ONLY,
                "Diese Assoziation ist in der generischen Oberfläche nur lesbar.")
        }

        Object target = recordLoader.get(targetType, targetId)
        if (target == null) {
            return failure(404, CommandStatus.NOT_FOUND, CommandCode.TARGET_NOT_FOUND,
                "Das ausgewählte Objekt wurde nicht gefunden.")
        }

        AssociationOperationContext operationContext = new AssociationOperationContext(
            association.associationName(), context.id(), participantType.name,
            association.domainClassName())
        if (!authorizationPolicy.canCreateAssociation(operationContext, participant, target)) {
            return failure(403, CommandStatus.FORBIDDEN, CommandCode.FORBIDDEN,
                "Die Zuordnung konnte nicht erstellt werden.")
        }

        if (isDuplicate(associationType, fixedProperty, participantId, targetProperty, targetId)) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.DUPLICATE_LINK,
                "Diese Zuordnung besteht bereits.")
        }

        AssociationCommandResult cardinalityError = validateCreateCardinality(
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
            lifecycleHooks.beforeAssociationCreate(operationContext, participant, target)
            instance.save(flush: true, failOnError: false)
        } catch (DataIntegrityViolationException e) {
            log.warn("Quick-link create failed for association ${context.associationName()} " +
                "context ${context.id()}: ${e.message}")
            return failure(409, CommandStatus.CONFLICT, CommandCode.DATA_INTEGRITY,
                "Die Zuordnung konnte nicht erstellt werden.")
        } catch (OptimisticLockingFailureException e) {
            log.warn("Quick-link create optimistic lock failure for association " +
                "${context.associationName()} context ${context.id()}")
            return failure(409, CommandStatus.CONFLICT, CommandCode.CONCURRENT_MODIFICATION,
                "Die Zuordnung konnte aufgrund einer gleichzeitigen Änderung nicht erstellt werden.")
        }

        if (instance.hasErrors() || instance.id == null) {
            return validationFailure(instance)
        }
        lifecycleHooks.afterAssociationCreate(operationContext, instance)

        return AssociationCommandResult.created(
            instance.id?.toString(),
            "interlis.association.created",
            InterlisMessageSupport.text(grailsApplication,
                "ili2grails.association.created", "Die Zuordnung wurde erstellt.")
        )
    }

    AssociationCommandResult deleteLink(Class participantType,
                                        Serializable participantId,
                                        String contextId,
                                        Serializable associationId) {
        AssociationContextDescriptor context = requireContextOrFail(participantType, contextId)
        if (context == null) {
            return null
        }
        AssociationDescriptor association = requireAssociationOrFail(context)
        if (association == null) {
            return null
        }

        if (!context.removable() || !context.writable()) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.READ_ONLY,
                "Diese Zuordnung kann nicht entfernt werden.")
        }
        if (hasCompositionRole(association)) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.COMPOSITION_DELETE_BLOCKED,
                "Diese Zuordnung kann in der generischen Oberfläche nicht entfernt werden.")
        }
        if (hasExternalRole(association)) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.EXTERNAL_DELETE_BLOCKED,
                "Diese externe Zuordnung kann in der generischen Oberfläche nicht entfernt werden.")
        }

        LoadOutcome participantOutcome = loadForWrite(participantType, participantId)
        if (participantOutcome.failureCode() != null) {
            return failure(409, CommandStatus.CONFLICT, participantOutcome.failureCode(),
                "Der Datensatz wurde gleichzeitig geändert. Bitte erneut versuchen.")
        }
        Object participant = participantOutcome.record()
        if (participant == null) {
            return failure(404, CommandStatus.NOT_FOUND, CommandCode.OWNER_NOT_FOUND,
                "Der Datensatz wurde nicht gefunden.")
        }

        Class associationType = runtimeRegistry.resolveDomainClass(association.domainClassName())
        if (associationType == null) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.READ_ONLY,
                "Diese Zuordnung kann nicht entfernt werden.")
        }

        Object instance = associationType.get(associationId)
        if (instance == null) {
            return failure(404, CommandStatus.NOT_FOUND, CommandCode.ASSOCIATION_NOT_FOUND,
                "Die Zuordnung wurde nicht gefunden.")
        }

        try {
            verifyAssociationBelongsToParticipant(instance, context, participant)
        } catch (AssociationOwnershipException e) {
            log.warn("Blocked association delete manipulation attempt: association ${associationId} " +
                "does not belong to ${participantType.simpleName}#${participantId} (context ${context.id()})")
            return failure(404, CommandStatus.NOT_FOUND, CommandCode.OWNERSHIP_MISMATCH,
                "Die Zuordnung gehört nicht zu diesem Datensatz.")
        }

        AssociationOperationContext operationContext = new AssociationOperationContext(
            association.associationName(), context.id(), participantType.name,
            association.domainClassName())
        if (!authorizationPolicy.canDeleteAssociation(operationContext, participant, instance)) {
            return failure(403, CommandStatus.FORBIDDEN, CommandCode.FORBIDDEN,
                "Die Zuordnung kann nicht entfernt werden.")
        }

        AssociationCommandResult cardinalityError = validateDeleteCardinality(
            associationType, context, context.fixedPropertyName(), participantId)
        if (cardinalityError != null) {
            return cardinalityError
        }

        try {
            instance.delete(flush: true)
        } catch (DataIntegrityViolationException e) {
            log.warn("Quick-link delete failed for association ${context.associationName()} " +
                "id ${associationId}: ${e.message}")
            return failure(409, CommandStatus.CONFLICT, CommandCode.DATA_INTEGRITY,
                "Die Zuordnung kann nicht entfernt werden, weil abhängige Daten vorhanden sind.")
        } catch (OptimisticLockingFailureException e) {
            log.warn("Quick-link delete optimistic lock failure for association " +
                "${context.associationName()} id ${associationId}")
            return failure(409, CommandStatus.CONFLICT, CommandCode.CONCURRENT_MODIFICATION,
                "Die Zuordnung kann aufgrund einer gleichzeitigen Änderung nicht entfernt werden.")
        }

        return AssociationCommandResult.deleted(
            "interlis.association.deleted",
            InterlisMessageSupport.text(grailsApplication,
                "ili2grails.association.deleted", "Die Zuordnung wurde entfernt.")
        )
    }

    /**
     * Resolves the context; on failure responds directly and returns null.
     */
    private AssociationContextDescriptor requireContextOrFail(Class participantType, String contextId) {
        if (participantType == null || contextId == null || contextId.isBlank()) {
            failure(400, CommandStatus.CLIENT_ERROR, CommandCode.CONTEXT_INVALID,
                "Ungültiger Zuordnungskontext.")
            return null
        }
        try {
            return runtimeRegistry.requireContext(participantType, contextId)
        } catch (IllegalArgumentException unknownOrForeign) {
            boolean belongsToParticipant = runtimeRegistry.contextsForParticipant(participantType.name)
                .any { it.id() == contextId }
            if (belongsToParticipant) {
                failure(404, CommandStatus.NOT_FOUND, CommandCode.OWNERSHIP_MISMATCH,
                    "Die Zuordnung gehört nicht zu diesem Datensatz.")
            } else {
                failure(400, CommandStatus.CLIENT_ERROR, CommandCode.CONTEXT_INVALID,
                    "Ungültiger Zuordnungskontext.")
            }
            return null
        }
    }

    private AssociationDescriptor requireAssociationOrFail(AssociationContextDescriptor context) {
        try {
            return runtimeRegistry.requireAssociation(context.associationName())
        } catch (IllegalArgumentException unknown) {
            failure(400, CommandStatus.CLIENT_ERROR, CommandCode.CONTEXT_INVALID,
                "Ungültiger Zuordnungskontext.")
            return null
        }
    }

    /**
     * Loads a record for a write operation with explicit lock semantics.
     * Only expected {@link LockStatus#LOCK_UNSUPPORTED} falls back to a plain
     * read; unexpected lock failures are never swallowed silently. Stale-state
     * lock failures are surfaced as {@link CommandCode#CONCURRENT_MODIFICATION}.
     */
    private LoadOutcome loadForWrite(Class type, Serializable id) {
        LockResult lockResult = recordLoader.lock(type, id)
        switch (lockResult.status()) {
            case LockStatus.LOCKED:
                return new LoadOutcome(lockResult.record(), null)
            case LockStatus.NOT_FOUND:
                return new LoadOutcome(null, null)
            case LockStatus.LOCK_UNSUPPORTED:
                return new LoadOutcome(recordLoader.get(type, id), null)
            case LockStatus.LOCK_FAILED:
            default:
                if (isConcurrentModification(lockResult.failure())) {
                    log.warn("Lock failed for ${type.simpleName}#${id} due to concurrent modification")
                    return new LoadOutcome(null, CommandCode.CONCURRENT_MODIFICATION)
                }
                log.warn("Lock failed for ${type.simpleName}#${id}: " +
                    "${lockResult.failure()?.message ?: 'unknown lock failure'}")
                return new LoadOutcome(null, null)
        }
    }

    private static boolean isConcurrentModification(Throwable failure) {
        Throwable cause = failure
        while (cause != null) {
            if (cause instanceof OptimisticLockingFailureException
                || cause instanceof org.hibernate.StaleObjectStateException
                || cause instanceof org.hibernate.StaleStateException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private static final class LoadOutcome {
        private final Object record
        private final CommandCode failureCode

        LoadOutcome(Object record, CommandCode failureCode) {
            this.record = record
            this.failureCode = failureCode
        }

        Object record() { record }
        CommandCode failureCode() { failureCode }
    }

    private AssociationCommandResult validateCreateCardinality(Class associationType,
                                                               AssociationContextDescriptor context,
                                                               String fixedProperty,
                                                               Serializable participantId) {
        Integer max = context.perspectiveMax()
        if (max == null || max == -1) {
            return null
        }
        long current = countLinks(associationType, fixedProperty, participantId)
        if (current + 1 > max) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.CARDINALITY_MAX_EXCEEDED,
                "Für dieses Objekt ist bereits die maximal zulässige Anzahl Zuordnungen vorhanden.")
        }
        return null
    }

    private AssociationCommandResult validateDeleteCardinality(Class associationType,
                                                               AssociationContextDescriptor context,
                                                               String fixedProperty,
                                                               Serializable participantId) {
        Integer min = context.perspectiveMin()
        if (min == null || min <= 0) {
            return null
        }
        long current = countLinks(associationType, fixedProperty, participantId)
        if (current - 1 < min) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.CARDINALITY_MIN_VIOLATED,
                "Die Zuordnung kann nicht entfernt werden, weil mindestens eine Beziehung bestehen muss.")
        }
        return null
    }

    private void verifyAssociationBelongsToParticipant(Object associationInstance,
                                                       AssociationContextDescriptor context,
                                                       Object participant) {
        String fixedProperty = context.fixedPropertyName()
        Object fixedValue = associationInstance."${fixedProperty}"
        String fixedId = fixedValue?.id?.toString()
        String participantId = participant?.id?.toString()
        if (fixedId == null || participantId == null || fixedId != participantId) {
            throw new AssociationOwnershipException(
                "Association ${associationInstance?.id} does not belong to participant ${participantId}")
        }
    }

    private void assignRole(Object associationInstance, String propertyName, Object value) {
        associationInstance."${propertyName}" = value
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

    private boolean hasCompositionRole(AssociationDescriptor association) {
        return association.roles().any { it.composition() }
    }

    private boolean hasExternalRole(AssociationDescriptor association) {
        return association.roles().any { it.external() }
    }

    private AssociationCommandResult validationFailure(Object instance) {
        List<FieldError> fieldErrors = (instance.errors?.fieldErrors ?: []).collect { error ->
            new FieldError(error.field?.toString(), error.code?.toString(),
                error.defaultMessage ?: error.code?.toString())
        }
        return new AssociationCommandResult(
            false, 422, CommandStatus.VALIDATION_ERROR, CommandCode.VALIDATION_FAILED,
            "interlis.association.validationFailed",
            InterlisMessageSupport.text(grailsApplication,
                "ili2grails.association.validationFailed",
                "Die Zuordnung konnte nicht gespeichert werden."),
            null,
            fieldErrors
        )
    }

    private AssociationCommandResult failure(int status, CommandStatus commandStatus,
                                             CommandCode code, String message) {
        return AssociationCommandResult.failure(status, commandStatus, code,
            InterlisMessageSupport.text(grailsApplication,
                "ili2grails.association.error.${code.name()}", message))
    }

    static class AssociationOwnershipException extends RuntimeException {
        AssociationOwnershipException(String message) {
            super(message)
        }
    }
}

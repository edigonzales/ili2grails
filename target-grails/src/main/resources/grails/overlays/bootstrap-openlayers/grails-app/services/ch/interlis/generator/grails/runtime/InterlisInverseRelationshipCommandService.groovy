package ch.interlis.generator.grails.runtime

import grails.gorm.transactions.Transactional
import jakarta.persistence.OptimisticLockException
import org.hibernate.StaleObjectStateException
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException

@Transactional
class InterlisInverseRelationshipCommandService {

    def grailsApplication

    Map<String, Object> assign(Class ownerType,
                               Serializable ownerId,
                               String relationshipName,
                               Serializable relatedId,
                               boolean confirmReassignment) {
        Map<String, Object> descriptor
        try {
            descriptor = InterlisInverseRelationshipSupport.requireDescriptor(
                grailsApplication,
                ownerType,
                relationshipName
            )
        } catch (InterlisInverseRelationshipSupport.InverseRelationshipNotFoundException ignored) {
            return failure(400, "RELATIONSHIP_INVALID", "Die Beziehung ist ungültig.")
        } catch (IllegalArgumentException invalidConfiguration) {
            return failure(
                500,
                "CONFIGURATION_INVALID",
                "Ungültige Beziehungs-Konfiguration: ${invalidConfiguration.message}"
            )
        }
        if (descriptor.visible != true || descriptor.writable != true) {
            return failure(409, "READ_ONLY", "Diese Beziehung ist nur lesbar.")
        }
        if (relatedId == null) {
            return failure(400, "TARGET_REQUIRED", "Es muss ein Datensatz ausgewählt werden.")
        }

        Object owner = lockOrGet(ownerType, ownerId)
        if (owner == null) {
            return failure(404, "OWNER_NOT_FOUND", "Der aktuelle Datensatz wurde nicht gefunden.")
        }
        Class relatedType = InterlisInverseRelationshipSupport.resolveRelatedClass(
            grailsApplication,
            descriptor
        )
        if (relatedType == null) {
            return failure(409, "READ_ONLY", "Diese Beziehung kann nicht bearbeitet werden.")
        }
        Object related = lockOrGet(relatedType, relatedId)
        if (related == null) {
            return failure(404, "TARGET_NOT_FOUND", "Der ausgewählte Datensatz wurde nicht gefunden.")
        }
        if (!canAssignRelationship(owner, related, descriptor)) {
            return failure(403, "FORBIDDEN", "Diese Zuordnung ist nicht erlaubt.")
        }

        String relatedProperty = descriptor.relatedProperty?.toString()
        if (relatedProperty == null
            || relatedProperty.isBlank()
            || related.metaClass.hasProperty(related, relatedProperty) == null) {
            return failure(409, "READ_ONLY", "Diese Beziehung kann nicht bearbeitet werden.")
        }
        Object previousOwner = InterlisInverseRelationshipSupport.readProperty(related, relatedProperty)
        if (sameRecord(previousOwner, owner)) {
            return success("ALREADY_ASSIGNED", "Der Datensatz ist bereits zugeordnet.", related, owner)
        }
        if (previousOwner != null && !confirmReassignment) {
            return [
                success: false,
                status: 409,
                code: "REASSIGNMENT_CONFIRMATION_REQUIRED",
                message: "Der Datensatz ist bereits einem anderen Objekt zugeordnet.",
                relatedId: related.id?.toString(),
                relatedLabel: InterlisRelationshipOptions.optionLabel(grailsApplication, related),
                previousOwnerId: previousOwner.id?.toString(),
                previousOwnerLabel: InterlisRelationshipOptions.optionLabel(grailsApplication, previousOwner),
                newOwnerId: owner.id?.toString(),
                newOwnerLabel: InterlisRelationshipOptions.optionLabel(grailsApplication, owner),
                targetTypeLabel: descriptor.relatedLabel?.toString()
            ]
        }
        if (previousOwner != null && !canReassignRelationship(owner, previousOwner, related, descriptor)) {
            return failure(403, "FORBIDDEN", "Diese Umteilung ist nicht erlaubt.")
        }

        related."${relatedProperty}" = owner
        if (!related.validate()) {
            return validationFailure(related)
        }
        try {
            related.save(flush: true, failOnError: false)
        } catch (DataIntegrityViolationException | ConstraintViolationException e) {
            log.warn("Inverse relationship assignment failed for ${ownerType.simpleName}#${ownerId}: ${e.message}")
            return failure(409, "DATA_INTEGRITY", "Die Zuordnung konnte nicht gespeichert werden.")
        } catch (OptimisticLockingFailureException | OptimisticLockException | StaleObjectStateException e) {
            log.warn("Inverse relationship assignment was modified concurrently for ${ownerType.simpleName}#${ownerId}")
            return failure(
                409,
                "CONCURRENT_MODIFICATION",
                "Der Datensatz wurde gleichzeitig geändert. Bitte erneut versuchen."
            )
        }
        if (related.hasErrors()) {
            return validationFailure(related)
        }
        return success(
            previousOwner == null ? "ASSIGNED" : "REASSIGNED",
            previousOwner == null ? "Der Datensatz wurde zugeordnet." : "Der Datensatz wurde umgeteilt.",
            related,
            owner
        )
    }

    protected boolean canAssignRelationship(Object owner,
                                            Object related,
                                            Map<String, Object> descriptor) {
        return true
    }

    protected boolean canReassignRelationship(Object owner,
                                              Object previousOwner,
                                              Object related,
                                              Map<String, Object> descriptor) {
        return true
    }

    private Object lockOrGet(Class domainType, Serializable id) {
        if (domainType == null || id == null) {
            return null
        }
        try {
            return domainType.lock(id)
        } catch (Exception ignored) {
            return domainType.get(id)
        }
    }

    private boolean sameRecord(Object left, Object right) {
        if (left == null || right == null || left.id == null || right.id == null) {
            return false
        }
        // The relationship property already constrains both values to the owner
        // domain type. Comparing the stable identifier also works for Hibernate
        // proxies whose runtime class differs from the generated domain class.
        return left.id.toString() == right.id.toString()
    }

    private Map<String, Object> validationFailure(Object related) {
        Map<String, String> fieldErrors = [:]
        related?.errors?.fieldErrors?.each { error ->
            fieldErrors[error.field] = error.defaultMessage ?: error.code
        }
        return [
            success: false,
            status: 422,
            code: "VALIDATION_FAILED",
            message: "Die Zuordnung ist nicht gültig.",
            fieldErrors: fieldErrors
        ]
    }

    private Map<String, Object> success(String code,
                                        String message,
                                        Object related,
                                        Object owner) {
        return [
            success: true,
            status: 200,
            code: code,
            message: message,
            relatedId: related.id?.toString(),
            ownerId: owner.id?.toString()
        ]
    }

    private Map<String, Object> failure(int status, String code, String message) {
        return [
            success: false,
            status: status,
            code: code,
            message: message,
            fieldErrors: [:]
        ]
    }
}

package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.command.CommandCode
import ch.interlis.generator.grails.runtime.api.command.CommandStatus
import ch.interlis.generator.grails.runtime.api.command.FieldError
import ch.interlis.generator.grails.runtime.api.command.InverseRelationshipCommandResult
import ch.interlis.generator.grails.runtime.api.command.ReassignmentConfirmation
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipDescriptor
import ch.interlis.generator.grails.runtime.api.lifecycle.InterlisLifecycleHooks
import ch.interlis.generator.grails.runtime.api.persistence.LockResult
import ch.interlis.generator.grails.runtime.api.persistence.LockStatus
import ch.interlis.generator.grails.runtime.api.persistence.RuntimeRecordLoader
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry
import ch.interlis.generator.grails.runtime.api.security.InterlisAuthorizationPolicy
import ch.interlis.generator.grails.runtime.api.security.InverseRelationshipOperationContext
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import jakarta.persistence.OptimisticLockException
import org.hibernate.StaleObjectStateException
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException

/**
 * Transactional command service for inverse relationship assignment.
 *
 * <p>All names originate from the validated typed descriptor of the generated
 * registry. Locking uses {@link RuntimeRecordLoader}: only expected
 * {@link LockStatus#LOCK_UNSUPPORTED} outcomes fall back to a plain read;
 * unexpected lock failures are surfaced as {@link CommandCode#CONCURRENT_MODIFICATION}.</p>
 */
@Slf4j
@Transactional
class InterlisInverseRelationshipCommandService {

    def grailsApplication
    InterlisRuntimeRegistry runtimeRegistry
    InterlisAuthorizationPolicy authorizationPolicy
    InterlisLifecycleHooks lifecycleHooks
    RuntimeRecordLoader recordLoader
    ch.interlis.generator.grails.runtime.config.InterlisRuntimeOverridesService overridesService

    InverseRelationshipCommandResult assign(Class ownerType,
                                            Serializable ownerId,
                                            String relationshipName,
                                            Serializable relatedId,
                                            boolean confirmReassignment) {
        InverseRelationshipDescriptor descriptor
        try {
            def domain = runtimeRegistry.requireDomain(ownerType)
            InverseRelationshipDescriptor generated = domain.inverseRelationships().get(relationshipName)
            if (generated == null) {
                return failure(400, CommandStatus.CLIENT_ERROR, CommandCode.RELATIONSHIP_INVALID,
                    "Die Beziehung ist ungültig.")
            }
            descriptor = overridesService.applyInverseRelationshipOverrides(
                generated,
                overridesService.overridesFor(domain)
            )
        } catch (IllegalArgumentException invalidConfiguration) {
            return failure(500, CommandStatus.SERVER_ERROR, CommandCode.CONFIGURATION_INVALID,
                "Ungültige Beziehungs-Konfiguration: ${invalidConfiguration.message}")
        }
        if (descriptor == null) {
            return failure(400, CommandStatus.CLIENT_ERROR, CommandCode.RELATIONSHIP_INVALID,
                "Die Beziehung ist ungültig.")
        }
        if (!descriptor.visible() || !descriptor.writable()) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.READ_ONLY,
                "Diese Beziehung ist nur lesbar.")
        }
        if (relatedId == null) {
            return failure(400, CommandStatus.CLIENT_ERROR, CommandCode.TARGET_REQUIRED,
                "Es muss ein Datensatz ausgewählt werden.")
        }

        LoadOutcome ownerOutcome = loadForWrite(ownerType, ownerId)
        if (ownerOutcome.failureCode() != null) {
            return failure(409, CommandStatus.CONFLICT, ownerOutcome.failureCode(),
                "Der Datensatz wurde gleichzeitig geändert. Bitte erneut versuchen.")
        }
        Object owner = ownerOutcome.record()
        if (owner == null) {
            return failure(404, CommandStatus.NOT_FOUND, CommandCode.OWNER_NOT_FOUND,
                "Der aktuelle Datensatz wurde nicht gefunden.")
        }
        Class relatedType = runtimeRegistry.resolveDomainClass(descriptor.relatedDomainClassName())
        if (relatedType == null) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.READ_ONLY,
                "Diese Beziehung kann nicht bearbeitet werden.")
        }
        LoadOutcome relatedOutcome = loadForWrite(relatedType, relatedId)
        if (relatedOutcome.failureCode() != null) {
            return failure(409, CommandStatus.CONFLICT, relatedOutcome.failureCode(),
                "Der Datensatz wurde gleichzeitig geändert. Bitte erneut versuchen.")
        }
        Object related = relatedOutcome.record()
        if (related == null) {
            return failure(404, CommandStatus.NOT_FOUND, CommandCode.TARGET_NOT_FOUND,
                "Der ausgewählte Datensatz wurde nicht gefunden.")
        }

        InverseRelationshipOperationContext operationContext =
            new InverseRelationshipOperationContext(
                descriptor.name(), ownerType.name, relatedType.name,
                descriptor.relatedPropertyName())
        if (!authorizationPolicy.canAssignInverseRelationship(operationContext, owner, related)) {
            return failure(403, CommandStatus.FORBIDDEN, CommandCode.FORBIDDEN,
                "Diese Zuordnung ist nicht erlaubt.")
        }

        String relatedProperty = descriptor.relatedPropertyName()
        if (relatedProperty == null
            || relatedProperty.isBlank()
            || related.metaClass.hasProperty(related, relatedProperty) == null) {
            return failure(409, CommandStatus.CONFLICT, CommandCode.READ_ONLY,
                "Diese Beziehung kann nicht bearbeitet werden.")
        }
        Object previousOwner = InterlisInverseRelationshipSupport.readProperty(related, relatedProperty)
        if (sameRecord(previousOwner, owner)) {
            return success(CommandCode.ALREADY_ASSIGNED,
                "Der Datensatz ist bereits zugeordnet.", related, owner)
        }
        if (previousOwner != null && !confirmReassignment) {
            return InverseRelationshipCommandResult.reassignmentRequired(
                new ReassignmentConfirmation(
                    related.id?.toString(),
                    InterlisRelationshipOptions.optionLabel(grailsApplication, related),
                    previousOwner.id?.toString(),
                    InterlisRelationshipOptions.optionLabel(grailsApplication, previousOwner),
                    owner.id?.toString(),
                    InterlisRelationshipOptions.optionLabel(grailsApplication, owner),
                    descriptor.relatedLabel()
                )
            )
        }
        if (previousOwner != null
            && !authorizationPolicy.canReassignInverseRelationship(
                operationContext, owner, previousOwner, related)) {
            return failure(403, CommandStatus.FORBIDDEN, CommandCode.FORBIDDEN,
                "Diese Umteilung ist nicht erlaubt.")
        }

        lifecycleHooks.beforeUpdate(
            ch.interlis.generator.grails.runtime.api.security.DomainOperationContext.of(
                ch.interlis.generator.grails.runtime.api.security.DomainOperation.UPDATE,
                runtimeRegistry.requireDomain(ownerType)),
            owner)
        related."${relatedProperty}" = owner
        if (!related.validate()) {
            return validationFailure(related)
        }
        try {
            related.save(flush: true, failOnError: false)
        } catch (DataIntegrityViolationException | ConstraintViolationException e) {
            log.warn("Inverse relationship assignment failed for ${ownerType.simpleName}#${ownerId}: ${e.message}")
            return failure(409, CommandStatus.CONFLICT, CommandCode.DATA_INTEGRITY,
                "Die Zuordnung konnte nicht gespeichert werden.")
        } catch (OptimisticLockingFailureException | OptimisticLockException | StaleObjectStateException e) {
            log.warn("Inverse relationship assignment was modified concurrently for " +
                "${ownerType.simpleName}#${ownerId}")
            return failure(409, CommandStatus.CONFLICT, CommandCode.CONCURRENT_MODIFICATION,
                "Der Datensatz wurde gleichzeitig geändert. Bitte erneut versuchen.")
        }
        if (related.hasErrors()) {
            return validationFailure(related)
        }
        return success(
            previousOwner == null ? CommandCode.ASSIGNED : CommandCode.REASSIGNED,
            previousOwner == null ? "Der Datensatz wurde zugeordnet." : "Der Datensatz wurde umgeteilt.",
            related,
            owner
        )
    }

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

    private boolean sameRecord(Object left, Object right) {
        if (left == null || right == null || left.id == null || right.id == null) {
            return false
        }
        // The relationship property already constrains both values to the owner
        // domain type. Comparing the stable identifier also works for Hibernate
        // proxies whose runtime class differs from the generated domain class.
        return left.id.toString() == right.id.toString()
    }

    private InverseRelationshipCommandResult validationFailure(Object related) {
        List<FieldError> fieldErrors = (related?.errors?.fieldErrors ?: []).collect { error ->
            new FieldError(error.field?.toString(), error.code?.toString(),
                error.defaultMessage ?: error.code?.toString())
        }
        return new InverseRelationshipCommandResult(
            false, 422, CommandStatus.VALIDATION_ERROR, CommandCode.VALIDATION_FAILED,
            null, "Die Zuordnung ist nicht gültig.",
            null, null, fieldErrors, null
        )
    }

    private InverseRelationshipCommandResult success(CommandCode code,
                                                     String message,
                                                     Object related,
                                                     Object owner) {
        return InverseRelationshipCommandResult.success(
            code, message, related.id?.toString(), owner.id?.toString())
    }

    private InverseRelationshipCommandResult failure(int status, CommandStatus commandStatus,
                                                     CommandCode code, String message) {
        return InverseRelationshipCommandResult.failure(status, commandStatus, code, message)
    }
}

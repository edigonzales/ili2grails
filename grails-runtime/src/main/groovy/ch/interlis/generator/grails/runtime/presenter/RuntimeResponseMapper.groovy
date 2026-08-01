package ch.interlis.generator.grails.runtime.presenter

import ch.interlis.generator.grails.runtime.api.command.AssociationCommandResult
import ch.interlis.generator.grails.runtime.api.command.FieldError
import ch.interlis.generator.grails.runtime.api.command.InverseRelationshipCommandResult
import ch.interlis.generator.grails.runtime.api.command.ReassignmentConfirmation

/**
 * Converts typed command results into the legacy JSON/response map shape at
 * the web boundary. Runtime services never produce these maps themselves.
 */
final class RuntimeResponseMapper {

    private RuntimeResponseMapper() {
    }

    static Map<String, Object> toLegacyMap(AssociationCommandResult result) {
        if (result == null) {
            return [success: false, status: 500, code: 'INTERNAL_ERROR',
                    message: 'Die Zuordnung konnte nicht verarbeitet werden.',
                    fieldErrors: [:]]
        }
        Map<String, Object> map = [
            success: result.success(),
            status: result.httpStatus(),
            code: result.code()?.name(),
            message: result.message(),
            fieldErrors: toLegacyFieldErrors(result.fieldErrors())
        ]
        if (result.messageCode() != null) {
            map.messageCode = result.messageCode()
        }
        if (result.associationId() != null) {
            map.associationId = result.associationId()
        }
        return map
    }

    static Map<String, Object> toLegacyMap(InverseRelationshipCommandResult result) {
        if (result == null) {
            return [success: false, status: 500, code: 'INTERNAL_ERROR',
                    message: 'Die Zuordnung konnte nicht verarbeitet werden.',
                    fieldErrors: [:]]
        }
        Map<String, Object> map = [
            success: result.success(),
            status: result.httpStatus(),
            code: result.code()?.name(),
            message: result.message(),
            fieldErrors: toLegacyFieldErrors(result.fieldErrors())
        ]
        if (result.messageCode() != null) {
            map.messageCode = result.messageCode()
        }
        if (result.relatedId() != null) {
            map.relatedId = result.relatedId()
        }
        if (result.ownerId() != null) {
            map.ownerId = result.ownerId()
        }
        ReassignmentConfirmation confirmation = result.reassignmentConfirmation()
        if (confirmation != null) {
            map.relatedId = confirmation.relatedId()
            map.relatedLabel = confirmation.relatedLabel()
            map.previousOwnerId = confirmation.previousOwnerId()
            map.previousOwnerLabel = confirmation.previousOwnerLabel()
            map.newOwnerId = confirmation.newOwnerId()
            map.newOwnerLabel = confirmation.newOwnerLabel()
            map.targetTypeLabel = confirmation.targetTypeLabel()
        }
        return map
    }

    private static Map<String, String> toLegacyFieldErrors(List<FieldError> fieldErrors) {
        Map<String, String> errors = [:]
        (fieldErrors ?: []).each { FieldError error ->
            if (error.field() != null && !error.field().isBlank()) {
                errors[error.field()] = error.message() ?: error.code()
            }
        }
        return errors
    }
}

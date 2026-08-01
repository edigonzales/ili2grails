package ch.interlis.generator.grails.runtime.api.command;

import java.util.List;

/**
 * Typed result of an inverse relationship assignment command.
 */
public record InverseRelationshipCommandResult(
    boolean success,
    int httpStatus,
    CommandStatus status,
    CommandCode code,
    String messageCode,
    String message,
    String relatedId,
    String ownerId,
    List<FieldError> fieldErrors,
    ReassignmentConfirmation reassignmentConfirmation
) {

    public InverseRelationshipCommandResult {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static InverseRelationshipCommandResult success(CommandCode code,
                                                           String message,
                                                           String relatedId,
                                                           String ownerId) {
        return new InverseRelationshipCommandResult(
            true, 200, CommandStatus.SUCCESS, code, null, message,
            relatedId, ownerId, List.of(), null);
    }

    public static InverseRelationshipCommandResult reassignmentRequired(
        ReassignmentConfirmation confirmation) {
        return new InverseRelationshipCommandResult(
            false, 409, CommandStatus.CONFLICT, CommandCode.REASSIGNMENT_CONFIRMATION_REQUIRED,
            null, "Der Datensatz ist bereits einem anderen Objekt zugeordnet.",
            confirmation.relatedId(), confirmation.newOwnerId(), List.of(), confirmation);
    }

    public static InverseRelationshipCommandResult failure(int httpStatus,
                                                           CommandStatus status,
                                                           CommandCode code,
                                                           String message) {
        return new InverseRelationshipCommandResult(
            false, httpStatus, status, code, null, message, null, null, List.of(), null);
    }
}

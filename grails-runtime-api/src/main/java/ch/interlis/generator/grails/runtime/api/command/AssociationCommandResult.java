package ch.interlis.generator.grails.runtime.api.command;

import java.util.List;

/**
 * Typed result of an association command (quick-link create or delete).
 *
 * <p>Controllers convert this record into the legacy map/JSON shape at the
 * web boundary.</p>
 */
public record AssociationCommandResult(
    boolean success,
    int httpStatus,
    CommandStatus status,
    CommandCode code,
    String messageCode,
    String message,
    String associationId,
    List<FieldError> fieldErrors
) {

    public AssociationCommandResult {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static AssociationCommandResult created(String associationId,
                                                   String messageCode,
                                                   String message) {
        return new AssociationCommandResult(
            true, 201, CommandStatus.SUCCESS, CommandCode.CREATED,
            messageCode, message, associationId, List.of());
    }

    public static AssociationCommandResult deleted(String messageCode, String message) {
        return new AssociationCommandResult(
            true, 204, CommandStatus.SUCCESS, CommandCode.DELETED,
            messageCode, message, null, List.of());
    }

    public static AssociationCommandResult failure(int httpStatus,
                                                   CommandStatus status,
                                                   CommandCode code,
                                                   String message) {
        return new AssociationCommandResult(
            false, httpStatus, status, code, null, message, null, List.of());
    }
}

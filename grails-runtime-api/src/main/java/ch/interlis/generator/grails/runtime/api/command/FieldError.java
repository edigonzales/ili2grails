package ch.interlis.generator.grails.runtime.api.command;

/**
 * Typed field validation error.
 */
public record FieldError(
    String field,
    String code,
    String message
) {

    public FieldError {
        field = field == null ? "" : field;
        code = code == null ? "" : code;
    }
}

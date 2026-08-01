package ch.interlis.generator.grails.runtime.api.command;

/**
 * Typed outcome status of a runtime command.
 */
public enum CommandStatus {
    SUCCESS,
    CLIENT_ERROR,
    CONFLICT,
    FORBIDDEN,
    NOT_FOUND,
    VALIDATION_ERROR,
    SERVER_ERROR
}

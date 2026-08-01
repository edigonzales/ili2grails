package ch.interlis.generator.grails.project;

import java.util.List;

/**
 * Structured diagnostic of the project customization.
 */
public record ProjectCustomizationDiagnostic(
    Level level,
    String code,
    String message,
    String filePath
) {

    public enum Level {
        INFO,
        WARNING,
        ERROR
    }

    public boolean isBlocking() {
        return level == Level.ERROR;
    }
}

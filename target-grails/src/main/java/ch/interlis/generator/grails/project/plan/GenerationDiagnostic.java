package ch.interlis.generator.grails.project.plan;

import ch.interlis.generator.grails.project.ProjectCustomizationDiagnostic;

import java.nio.file.Path;
import java.util.Map;

/**
 * Generations-Diagnostic (Spezifikation §38.6).
 */
public record GenerationDiagnostic(
    ProjectCustomizationDiagnostic.Level level,
    GenerationDiagnosticCode code,
    Path relativePath,
    String message,
    Map<String, String> details
) {

    public GenerationDiagnostic {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public boolean blocking() {
        return level == ProjectCustomizationDiagnostic.Level.ERROR;
    }
}

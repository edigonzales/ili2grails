package ch.interlis.generator.grails.project;

import java.util.List;

/**
 * Result of a project customization run.
 *
 * @param diagnostics structured diagnostics of the run
 * @param deletedLegacyRuntimeFiles files deleted by the safe legacy migration
 */
public record ProjectCustomizationResult(
    List<ProjectCustomizationDiagnostic> diagnostics,
    List<String> deletedLegacyRuntimeFiles
) {

    public ProjectCustomizationResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        deletedLegacyRuntimeFiles = deletedLegacyRuntimeFiles == null
            ? List.of() : List.copyOf(deletedLegacyRuntimeFiles);
    }

    public boolean hasBlockingDiagnostics() {
        return diagnostics.stream().anyMatch(ProjectCustomizationDiagnostic::isBlocking);
    }

    public List<ProjectCustomizationDiagnostic> blockingDiagnostics() {
        return diagnostics.stream().filter(ProjectCustomizationDiagnostic::isBlocking).toList();
    }
}

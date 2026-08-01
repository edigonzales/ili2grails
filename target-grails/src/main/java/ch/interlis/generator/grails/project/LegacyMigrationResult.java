package ch.interlis.generator.grails.project;

import java.util.List;

/**
 * Result of the legacy runtime migration.
 *
 * @param deletedFiles project-relative paths of deleted files
 * @param diagnostics  diagnostics of the migration run
 */
public record LegacyMigrationResult(
    List<String> deletedFiles,
    List<ProjectCustomizationDiagnostic> diagnostics
) {

    public LegacyMigrationResult {
        deletedFiles = deletedFiles == null ? List.of() : List.copyOf(deletedFiles);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}

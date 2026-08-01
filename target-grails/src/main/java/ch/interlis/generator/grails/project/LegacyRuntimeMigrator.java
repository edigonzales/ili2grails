package ch.interlis.generator.grails.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Migrates a pre-P1 application to the plugin runtime.
 *
 * <p>Rules: a missing file does nothing; a file matching a known generator
 * state exactly is deleted; a modified file is never deleted or overwritten
 * and produces a blocking diagnostic with the path and migration hint.</p>
 */
public final class LegacyRuntimeMigrator {

    public LegacyMigrationResult migrate(Path projectDir,
                                         LegacyRuntimeScanResult scanResult,
                                         LegacyMigrationPolicy policy) throws IOException {
        Objects.requireNonNull(projectDir, "projectDir");
        Objects.requireNonNull(scanResult, "scanResult");
        Objects.requireNonNull(policy, "policy");

        List<ProjectCustomizationDiagnostic> diagnostics = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();

        for (LegacyFileMatch modified : scanResult.modifiedFiles()) {
            diagnostics.add(new ProjectCustomizationDiagnostic(
                ProjectCustomizationDiagnostic.Level.ERROR,
                "LEGACY_RUNTIME_MODIFIED",
                "Modified legacy runtime file '" + modified.relativePath()
                    + "' was not deleted. Move customizations into the "
                    + "ili2grails-runtime plugin or an application-owned "
                    + "override, then remove the file manually.",
                modified.relativePath().toString()));
        }

        if (policy == LegacyMigrationPolicy.STRICT) {
            for (LegacyFileMatch unmodified : scanResult.knownUnmodifiedFiles()) {
                Path file = projectDir.resolve(unmodified.relativePath());
                Files.deleteIfExists(file);
                deletedFiles.add(unmodified.relativePath().toString());
            }
        }

        for (Path unknown : scanResult.unknownRuntimeFiles()) {
            diagnostics.add(new ProjectCustomizationDiagnostic(
                ProjectCustomizationDiagnostic.Level.WARNING,
                "LEGACY_RUNTIME_UNKNOWN_FILE",
                "Unknown file in a runtime package: '" + unknown
                    + "'. It is not part of any known generator state and was "
                    + "left untouched.",
                unknown.toString()));
        }

        return new LegacyMigrationResult(List.copyOf(deletedFiles), diagnostics);
    }
}

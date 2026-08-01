package ch.interlis.generator.grails.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Result of a legacy runtime scan.
 *
 * @param knownUnmodifiedFiles files matching a known generator state exactly
 * @param modifiedFiles        files that differ from every known generator state
 * @param unknownRuntimeFiles  files in runtime packages that are not part of
 *                             any known legacy state (user-created)
 */
public record LegacyRuntimeScanResult(
    List<LegacyFileMatch> knownUnmodifiedFiles,
    List<LegacyFileMatch> modifiedFiles,
    List<Path> unknownRuntimeFiles
) {

    public LegacyRuntimeScanResult {
        knownUnmodifiedFiles = knownUnmodifiedFiles == null
            ? List.of() : List.copyOf(knownUnmodifiedFiles);
        modifiedFiles = modifiedFiles == null ? List.of() : List.copyOf(modifiedFiles);
        unknownRuntimeFiles = unknownRuntimeFiles == null
            ? List.of() : List.copyOf(unknownRuntimeFiles);
    }

    public boolean requiresManualIntervention() {
        return !modifiedFiles.isEmpty();
    }
}

package ch.interlis.generator.grails.project;

import java.nio.file.Path;
import java.util.Set;

/**
 * Single legacy file match.
 *
 * @param relativePath    project-relative path (forward slashes)
 * @param actualSha256    SHA-256 of the file on disk
 * @param knownSha256Values SHA-256 values of known generator states
 */
public record LegacyFileMatch(
    Path relativePath,
    String actualSha256,
    Set<String> knownSha256Values
) {
}

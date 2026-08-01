package ch.interlis.generator.grails.project.plan;

import ch.interlis.generator.grails.project.GrailsProjectFileOwner;

/**
 * Manifest-Eintrag einer verwalteten Datei (Spezifikation §39.2).
 */
public record ManagedFileManifestEntry(
    String path,
    GrailsProjectFileOwner owner,
    String sha256
) {
}

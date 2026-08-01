package ch.interlis.generator.grails.project.plan;

import java.util.List;

/**
 * Deterministisches Generations-Manifest (Spezifikation §39.2).
 *
 * <p>Enthält keine volatile Information: keine Timestamps, keine
 * Benutzernamen, keine absoluten Pfade, keine Hostnamen, keine
 * JDBC-Credentials.</p>
 */
public record GeneratedProjectManifest(
    int schemaVersion,
    String generatorVersion,
    String runtimeApiVersion,
    String modelName,
    String modelFingerprint,
    String configurationFingerprint,
    List<ManagedFileManifestEntry> files
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public GeneratedProjectManifest {
        files = files == null ? List.of() : List.copyOf(files);
    }
}

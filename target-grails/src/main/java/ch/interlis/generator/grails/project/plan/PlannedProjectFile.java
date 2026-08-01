package ch.interlis.generator.grails.project.plan;

import ch.interlis.generator.model.ModelMetadataFingerprint;
import ch.interlis.generator.grails.project.GrailsProjectFileOwner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Eine geplante Projektdatei (Spezifikation §38.3). Immutable; der Inhalt
 * wird erst im Executor geschrieben.
 */
public record PlannedProjectFile(
    Path relativePath,
    GrailsProjectFileOwner owner,
    byte[] content,
    String reason
) {

    public String sha256() {
        return ModelMetadataFingerprint.sha256(content);
    }

    public boolean textFile() {
        return true;
    }

    public static PlannedProjectFile text(Path relativePath,
                                          GrailsProjectFileOwner owner,
                                          String content,
                                          String reason) {
        return new PlannedProjectFile(relativePath, owner,
            content.getBytes(StandardCharsets.UTF_8), reason);
    }
}

package ch.interlis.generator.grails.verification.corpus;

import java.nio.file.Path;

/**
 * Entry point für den Gradle-Task :target-grails:verifyModelCorpusManifest.
 * Wird in der Corpus-Phase vollständig implementiert.
 */
public final class ModelCorpusManifestVerifier {

    private ModelCorpusManifestVerifier() {
    }

    public static void main(String[] args) {
        String corpusFile = System.getProperty("corpusFile");
        if (corpusFile == null) {
            throw new IllegalStateException("System property corpusFile is required");
        }
        System.out.println("verifyModelCorpusManifest: corpus file=" + Path.of(corpusFile).toAbsolutePath());
    }
}

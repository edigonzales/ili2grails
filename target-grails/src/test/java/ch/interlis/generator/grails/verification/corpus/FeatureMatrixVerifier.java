package ch.interlis.generator.grails.verification.corpus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point für :target-grails:verifyFeatureMatrixUpToDate: schreibt die
 * generierte Matrix als Evidence nach build/reports und vergleicht sie mit
 * der committeden Datei (Spezifikation §27).
 */
public final class FeatureMatrixVerifier {

    private FeatureMatrixVerifier() {
    }

    public static void main(String[] args) throws Exception {
        String corpusFile = System.getProperty("corpusFile");
        if (corpusFile == null) {
            throw new IllegalStateException("System property corpusFile is required");
        }
        Path corpusPath = Path.of(corpusFile);
        Path repositoryRoot = corpusPath.getParent().getParent();
        ModelCorpus corpus = new ModelCorpusLoader().load(corpusPath);
        new ModelCorpusValidator().validate(corpus, repositoryRoot).throwIfInvalid();

        FeatureMatrixGenerator generator = new FeatureMatrixGenerator();
        String generated = generator.generateMarkdown(corpus);
        Path report = repositoryRoot.resolve("build/reports/model-corpus/feature-matrix.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, generated, StandardCharsets.UTF_8);

        String diff = generator.diffAgainstCommitted(corpus, repositoryRoot);
        if (diff != null) {
            throw new IllegalStateException(diff
                + "\nGenerated report: " + report.toAbsolutePath());
        }
        System.out.println("verifyFeatureMatrixUpToDate: feature matrix is up to date ("
            + corpus.features().size() + " features)");
    }
}

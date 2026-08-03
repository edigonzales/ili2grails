package ch.interlis.generator.grails.verification.corpus;

import ch.interlis.generator.grails.verification.report.VerificationCheckResult;
import ch.interlis.generator.grails.verification.report.VerificationStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point für den Gradle-Task :target-grails:verifyModelCorpusManifest
 * (Spezifikation §24.5): lädt und validiert den Korpus, prüft die
 * Feature-Matrix und schreibt die Corpus-Reports.
 */
public final class ModelCorpusManifestVerifier {

    private ModelCorpusManifestVerifier() {
    }

    public static void main(String[] args) throws Exception {
        String corpusFile = System.getProperty("corpusFile");
        if (corpusFile == null) {
            throw new IllegalStateException("System property corpusFile is required");
        }
        Path corpusPath = Path.of(corpusFile);
        Path repositoryRoot = corpusPath.getParent().getParent();
        Path reportDir = repositoryRoot.resolve("build/reports/model-corpus");

        ModelCorpus corpus = new ModelCorpusLoader().load(corpusPath);
        CorpusValidationResult validation = new ModelCorpusValidator().validate(corpus, repositoryRoot);
        validation.throwIfInvalid();

        String diff = new FeatureMatrixGenerator().diffAgainstCommitted(corpus, repositoryRoot);
        if (diff != null) {
            throw new IllegalStateException("Feature matrix is outdated: " + diff
                + "\nRegenerate via :target-grails:verifyFeatureMatrixUpToDate and commit the result.");
        }

        writeCorpusResults(corpus, validation, reportDir);
        System.out.println("verifyModelCorpusManifest: corpus valid (" + corpus.scenarios().size()
            + " scenarios, " + corpus.features().size() + " features), feature matrix up to date");
    }

    static void writeCorpusResults(ModelCorpus corpus, CorpusValidationResult validation,
                                   Path reportDir) throws Exception {
        Files.createDirectories(reportDir);
        List<String> scenarioLines = new ArrayList<>();
        for (CorpusScenario scenario : corpus.scenarios()) {
            String db = scenario.database() != null && scenario.database().required()
                ? "db-required" : "semantic";
            scenarioLines.add("{\"id\": \"" + scenario.id() + "\", \"modelName\": \""
                + scenario.modelName() + "\", \"mode\": \"" + db + "\"}");
        }
        String json = "{\n"
            + "  \"schemaVersion\": " + corpus.schemaVersion() + ",\n"
            + "  \"features\": " + corpus.features().size() + ",\n"
            + "  \"scenarios\": " + corpus.scenarios().size() + ",\n"
            + "  \"validation\": \"" + (validation.isValid() ? "VALID" : "INVALID") + "\",\n"
            + "  \"scenarioList\": [" + String.join(", ", scenarioLines) + "]\n"
            + "}\n";
        Files.writeString(reportDir.resolve("corpus-results.json"), json, StandardCharsets.UTF_8);

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Modellkorpus\n\n");
        markdown.append("- Schema-Version: ").append(corpus.schemaVersion()).append("\n");
        markdown.append("- Features: ").append(corpus.features().size()).append("\n");
        markdown.append("- Szenarien: ").append(corpus.scenarios().size()).append("\n");
        markdown.append("- Validierung: ").append(validation.isValid() ? "VALID" : "INVALID").append("\n\n");
        markdown.append("| Szenario | Modell | Modus | Features |\n");
        markdown.append("|---|---|---|---|\n");
        for (CorpusScenario scenario : corpus.scenarios()) {
            String db = scenario.database() != null && scenario.database().required()
                ? "db-required" : "semantic";
            markdown.append("| `").append(scenario.id()).append("` | `")
                .append(scenario.modelName()).append("` | ")
                .append(db).append(" | ")
                .append(String.join(", ", scenario.features().stream().sorted().toList()))
                .append(" |\n");
        }
        Files.writeString(reportDir.resolve("corpus-results.md"), markdown.toString(), StandardCharsets.UTF_8);
    }
}
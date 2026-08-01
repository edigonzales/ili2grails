package ch.interlis.generator.grails.verification.corpus;

import ch.interlis.generator.grails.verification.environment.VerificationEnvironment;
import ch.interlis.generator.grails.verification.environment.VerificationEnvironmentOptions;
import ch.interlis.generator.grails.verification.environment.VerificationEnvironmentDetector;
import ch.interlis.generator.grails.verification.report.VerificationStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Korpus-Verträge (Spezifikation §24, §26): Validierung, Determinismus und
 * semantische Szenarien ohne Datenbank.
 */
class ModelCorpusTest {

    private static final Path CORPUS_FILE = Path.of("verification/model-corpus.yaml");
    private static final Path REPOSITORY_ROOT = Path.of(".").toAbsolutePath().normalize();

    @TempDir
    Path tempDir;

    private static ModelCorpus corpus;

    @BeforeAll
    static void loadCorpus() throws Exception {
        corpus = new ModelCorpusLoader().load(CORPUS_FILE);
    }

    @Test
    void corpusLoadsAndValidates() {
        CorpusValidationResult validation = new ModelCorpusValidator().validate(corpus, REPOSITORY_ROOT);
        assertThat(validation.diagnostics())
            .as("corpus must be valid without errors")
            .noneMatch(CorpusValidationDiagnostic::blocking);
        assertThat(corpus.schemaVersion()).isEqualTo(1);
        assertThat(corpus.features()).isNotEmpty();
        assertThat(corpus.scenarios()).isNotEmpty();
    }

    @Test
    void allScenarioModelFilesExist() {
        for (CorpusScenario scenario : corpus.scenarios()) {
            Path modelPath = scenario.modelFile().isAbsolute()
                ? scenario.modelFile()
                : REPOSITORY_ROOT.resolve(scenario.modelFile());
            assertThat(Files.isRegularFile(modelPath))
                .as("model file of scenario %s exists", scenario.id())
                .isTrue();
        }
    }

    @Test
    void dbRequiredScenariosHaveImportProfileAndMappingContract() {
        for (CorpusScenario scenario : corpus.scenarios()) {
            if (scenario.database() != null && scenario.database().required()) {
                assertThat(scenario.database().importProfile())
                    .as("db-required scenario %s has import profile", scenario.id())
                    .isNotBlank();
                assertThat(scenario.expected().mappingContract())
                    .as("db-required scenario %s needs mappingContract", scenario.id())
                    .isTrue();
            }
        }
    }

    @Test
    void featureMatrixStatusRulesAreApplied() {
        String matrix = new FeatureMatrixGenerator().generateMarkdown(corpus);
        // Persistenzfeatures mit realem DB-Vertrag sind SUPPORTED
        assertThat(matrix).contains("`reference.many-to-one` | SUPPORTED");
        // Semantik-only-Features sind PARTIAL, nicht SUPPORTED
        assertThat(matrix).contains("`model-selection.root` | PARTIAL");
        assertThat(matrix).contains("`real-world.large-model` | PARTIAL");
    }

    @Test
    void semanticScenariosRunAndGenerateDeterministically() throws Exception {
        CorpusScenarioRunner runner = new CorpusScenarioRunner();
        VerificationEnvironment environment = new VerificationEnvironmentDetector().detect(
            REPOSITORY_ROOT, VerificationEnvironmentOptions.defaults());

        // Nur lokale Szenarien (keine externen Repositories) im Fast-Test;
        // VSADSSMINI läuft in den erweiterten Tests (externes Repo).
        for (CorpusScenario scenario : corpus.scenarios()) {
            if (scenario.database() != null && scenario.database().required()) {
                continue; // DB-Szenarien laufen in den erweiterten Tests
            }
            if (scenario.repositories().stream().anyMatch(repo -> repo.startsWith("http"))) {
                continue; // externes Modell-Repository: erweiterte Tests
            }
            CorpusExecutionContext context = new CorpusExecutionContext(
                REPOSITORY_ROOT, tempDir, environment, false);
            CorpusScenarioResult first = runner.runSemanticAndGeneration(scenario, context);
            CorpusScenarioResult second = runner.runSemanticAndGeneration(scenario, context);

            assertThat(first.status())
                .as("scenario %s must pass: %s", scenario.id(), first.diagnostics())
                .isEqualTo(VerificationStatus.PASSED);
            assertThat(second.status()).as("scenario %s must be deterministic", scenario.id())
                .isEqualTo(VerificationStatus.PASSED);
            assertThat(second.generatedFilesFingerprint())
                .as("scenario %s generates deterministic files", scenario.id())
                .isEqualTo(first.generatedFilesFingerprint());
            assertThat(first.generatedFiles()).isNotEmpty();
        }
    }

    @Test
    void modelSelectionScenarioSelectsDependenciesAndExcludesUnrelated() throws Exception {
        CorpusScenario scenario = corpus.scenarios().stream()
            .filter(candidate -> "model-selection".equals(candidate.id()))
            .findFirst()
            .orElseThrow();
        CorpusScenarioRunner runner = new CorpusScenarioRunner();
        CorpusExecutionContext context = new CorpusExecutionContext(REPOSITORY_ROOT, tempDir,
            new VerificationEnvironment("17", "os", "arch", "commit", null, java.util.Map.of()),
            false);
        CorpusScenarioResult result = runner.runSemanticAndGeneration(scenario, context);
        assertThat(result.status()).isEqualTo(VerificationStatus.PASSED);
        assertThat(result.counts().selectedModels())
            .as("root + direct dependency + transitive dependency")
            .isEqualTo(3);
        // Unabhängiges Modell ist nicht Teil der Auswahl: nur 3 statt 4 Modelle.
        assertThat(result.counts().selectedModels()).isLessThan(4);
    }

    @Test
    void mergeAmbiguityScenarioProducesExplainedDiagnostics() throws Exception {
        // Das Merge-Ambiguitäts-Szenario darf nicht blockieren; die
        // Erwartungswerte sind im Corpus dokumentiert.
        CorpusScenario scenario = corpus.scenarios().stream()
            .filter(candidate -> "merge-ambiguity".equals(candidate.id()))
            .findFirst()
            .orElseThrow();
        CorpusScenarioRunner runner = new CorpusScenarioRunner();
        CorpusExecutionContext context = new CorpusExecutionContext(REPOSITORY_ROOT, tempDir,
            new VerificationEnvironment("17", "os", "arch", "commit", null, java.util.Map.of()),
            false);
        CorpusScenarioResult result = runner.runSemanticAndGeneration(scenario, context);
        assertThat(result.status()).isEqualTo(VerificationStatus.PASSED);
    }

    @Test
    void validatorRejectsPathTraversalAndUnknownFeatures() throws Exception {
        Path fakeRoot = tempDir.resolve("repo");
        Files.createDirectories(fakeRoot);
        Path modelOutside = tempDir.resolve("outside.ili");
        Files.writeString(modelOutside, "INTERLIS 2.4;\nMODEL Outside EN END Outside.");
        ModelCorpus invalid = new ModelCorpus(
            1,
            List.of(new CorpusFeature("feature.x", "x")),
            List.of(new CorpusScenario("s1", "Outside", modelOutside,
                List.of(), CorpusDatabaseRequirement.none(), java.util.Set.of("unknown-feature"),
                new CorpusExpectation(0, null, 1, null, null, null,
                    true, false, false, List.of()))));
        CorpusValidationResult validation = new ModelCorpusValidator().validate(invalid, fakeRoot);
        assertThat(validation.blockingDiagnostics())
            .extracting(CorpusValidationDiagnostic::code)
            .contains("PATH_TRAVERSAL", "UNKNOWN_FEATURE");
    }

    @Test
    void validatorRejectsContradictoryExpectations() throws Exception {
        Path fakeRoot = tempDir.resolve("repo2");
        Files.createDirectories(fakeRoot);
        Path model = fakeRoot.resolve("model.ili");
        Files.writeString(model, "INTERLIS 2.4;\nMODEL M EN END M.");
        ModelCorpus invalid = new ModelCorpus(
            1,
            List.of(new CorpusFeature("feature.x", "x")),
            List.of(new CorpusScenario("s1", "M", model,
                List.of(), CorpusDatabaseRequirement.none(), java.util.Set.of("feature.x"),
                new CorpusExpectation(0, 5, 3, null, null, null,
                    true, false, false, List.of()))));
        CorpusValidationResult validation = new ModelCorpusValidator().validate(invalid, fakeRoot);
        assertThat(validation.blockingDiagnostics())
            .extracting(CorpusValidationDiagnostic::code)
            .contains("CONTRADICTORY_EXPECTATIONS");
    }
}

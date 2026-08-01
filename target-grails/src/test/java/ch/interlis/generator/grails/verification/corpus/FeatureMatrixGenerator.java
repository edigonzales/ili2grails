package ch.interlis.generator.grails.verification.corpus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Erzeugt die INTERLIS-Feature-Matrix aus dem Corpus (Spezifikation §27).
 *
 * <p>Regeln: Ein Persistenzfeature ist nur {@code SUPPORTED}, wenn ein
 * Szenario mit {@code database.required=true} und {@code mappingContract=true}
 * es belegt. Reine Snapshot-/Generierungs-Abdeckung ergibt {@code PARTIAL}
 * mit konkreter Einschränkung.</p>
 */
public final class FeatureMatrixGenerator {

    public static final String COMMITTED_PATH = "docs/verification/interlis-feature-matrix.md";

    public String generateMarkdown(ModelCorpus corpus) {
        Map<String, List<CorpusScenario>> scenariosByFeature = new LinkedHashMap<>();
        corpus.features().forEach(feature -> scenariosByFeature.put(feature.id(), new ArrayList<>()));
        for (CorpusScenario scenario : corpus.scenarios()) {
            for (String feature : scenario.features()) {
                scenariosByFeature.computeIfAbsent(feature, ignored -> new ArrayList<>()).add(scenario);
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# INTERLIS Feature-Matrix\n\n");
        builder.append("Generiert aus `verification/model-corpus.yaml` (Schema-Version ")
            .append(corpus.schemaVersion()).append(").\n\n");
        builder.append("| Feature | Status | Szenarien | Core-Test | Real-DB-Test | Browser-Test | Bemerkung |\n");
        builder.append("|---|---|---|---|---|---|---|\n");

        List<CorpusFeature> sortedFeatures = corpus.features().stream()
            .sorted(Comparator.comparing(CorpusFeature::id))
            .toList();
        for (CorpusFeature feature : sortedFeatures) {
            List<CorpusScenario> scenarios = scenariosByFeature.getOrDefault(feature.id(), List.of());
            boolean realDb = scenarios.stream()
                .anyMatch(scenario -> scenario.database() != null && scenario.database().required()
                    && scenario.expected() != null && scenario.expected().mappingContract());
            boolean generated = !scenarios.isEmpty();
            FeatureStatus status = realDb
                ? FeatureStatus.SUPPORTED
                : generated ? FeatureStatus.PARTIAL : FeatureStatus.UNSUPPORTED;
            String remark = switch (status) {
                case SUPPORTED -> "belegt durch realen PostgreSQL/ili2pg-Vertrag";
                case PARTIAL -> "semantische Generierung belegt; kein realer DB-Vertrag";
                case UNSUPPORTED -> "kein Szenario belegt dieses Feature";
                case EXPERIMENTAL -> "experimentell";
            };
            String scenarioNames = scenarios.stream()
                .map(CorpusScenario::id)
                .sorted()
                .collect(Collectors.joining(", "));
            String coreTest = scenarios.isEmpty() ? "-" : scenarioNames;
            String realDbTest = scenarios.stream()
                .filter(scenario -> scenario.database() != null && scenario.database().required())
                .map(CorpusScenario::id)
                .sorted()
                .collect(Collectors.joining(", "));
            builder.append("| `").append(feature.id()).append("` | ")
                .append(status).append(" | ")
                .append(scenarioNames.isBlank() ? "-" : scenarioNames).append(" | ")
                .append(coreTest.isBlank() ? "-" : coreTest).append(" | ")
                .append(realDbTest.isBlank() ? "-" : realDbTest).append(" | ")
                .append("- | ")
                .append(remark).append(" |\n");
        }
        builder.append("\n");
        builder.append("Statuswerte: SUPPORTED = realer Datenbank-/Mapping-Contract vorhanden; ")
            .append("PARTIAL = konkrete Einschränkung dokumentiert; ")
            .append("EXPERIMENTAL = experimentell; ")
            .append("UNSUPPORTED = nicht belegt.\n");
        return builder.toString();
    }

    /**
     * Vergleicht die generierte Matrix mit der committeden Datei.
     *
     * @return die Abweichung oder {@code null}, wenn die Matrix aktuell ist
     */
    public String diffAgainstCommitted(ModelCorpus corpus, Path repositoryRoot) throws java.io.IOException {
        String generated = generateMarkdown(corpus);
        Path committed = repositoryRoot.resolve(COMMITTED_PATH);
        if (!Files.exists(committed)) {
            return "committed feature matrix is missing: " + COMMITTED_PATH;
        }
        String existing = Files.readString(committed);
        if (!existing.equals(generated)) {
            return "committed feature matrix is outdated; regenerate " + COMMITTED_PATH;
        }
        return null;
    }
}

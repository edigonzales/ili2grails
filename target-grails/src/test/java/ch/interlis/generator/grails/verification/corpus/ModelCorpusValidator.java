package ch.interlis.generator.grails.verification.corpus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Statische Validierung des Modellkorpus (Spezifikation §24.2).
 *
 * <p>Prüft Schema-Version, Eindeutigkeit, Feature-Referenzen, Modelldateien,
 * DB-Anforderungen, Erwartungs-Konsistenz und Pfadtraversal.</p>
 */
public final class ModelCorpusValidator {

    public CorpusValidationResult validate(ModelCorpus corpus, Path repositoryRoot) {
        List<CorpusValidationDiagnostic> diagnostics = new ArrayList<>();
        if (corpus == null) {
            diagnostics.add(error("NULL_CORPUS", null, "corpus is null"));
            return new CorpusValidationResult(diagnostics);
        }
        if (corpus.schemaVersion() != 1) {
            diagnostics.add(error("SCHEMA_VERSION", null,
                "schemaVersion must be 1, found " + corpus.schemaVersion()));
        }

        Set<String> featureIds = new HashSet<>();
        for (CorpusFeature feature : corpus.features()) {
            if (feature.id() == null || feature.id().isBlank()) {
                diagnostics.add(error("FEATURE_ID_MISSING", null, "feature without id"));
                continue;
            }
            if (!featureIds.add(feature.id())) {
                diagnostics.add(error("DUPLICATE_FEATURE_ID", null,
                    "duplicate feature id " + feature.id()));
            }
        }

        Set<String> scenarioIds = new HashSet<>();
        Set<String> referencedFeatures = new HashSet<>();
        Path normalizedRoot = repositoryRoot.toAbsolutePath().normalize();
        for (CorpusScenario scenario : corpus.scenarios()) {
            String id = scenario.id();
            if (id == null || id.isBlank()) {
                diagnostics.add(error("SCENARIO_ID_MISSING", null, "scenario without id"));
                continue;
            }
            if (!scenarioIds.add(id)) {
                diagnostics.add(error("DUPLICATE_SCENARIO_ID", id,
                    "duplicate scenario id " + id));
            }
            if (scenario.modelName() == null || scenario.modelName().isBlank()) {
                diagnostics.add(error("MODEL_NAME_MISSING", id, "modelName must not be empty"));
            }
            if (scenario.features() == null || scenario.features().isEmpty()) {
                diagnostics.add(error("NO_FEATURES", id,
                    "scenario must reference at least one feature"));
            } else {
                for (String feature : scenario.features()) {
                    if (feature == null || !featureIds.contains(feature)) {
                        diagnostics.add(error("UNKNOWN_FEATURE", id,
                            "unknown feature " + feature));
                    }
                    referencedFeatures.add(feature);
                }
            }
            if (scenario.modelFile() == null) {
                diagnostics.add(error("MODEL_FILE_MISSING", id, "modelFile must not be empty"));
            } else {
                Path modelPath = scenario.modelFile().isAbsolute()
                    ? scenario.modelFile()
                    : normalizedRoot.resolve(scenario.modelFile());
                if (!modelPath.normalize().startsWith(normalizedRoot)) {
                    diagnostics.add(error("PATH_TRAVERSAL", id,
                        "modelFile escapes the repository: " + scenario.modelFile()));
                } else if (!Files.isRegularFile(modelPath)) {
                    diagnostics.add(error("MODEL_FILE_MISSING", id,
                        "model file does not exist: " + scenario.modelFile()));
                }
            }
            CorpusDatabaseRequirement database = scenario.database();
            if (database != null && database.required()) {
                if (database.importProfile() == null || database.importProfile().isBlank()) {
                    diagnostics.add(error("DB_PROFILE_MISSING", id,
                        "db-required scenario needs an import profile"));
                }
                if (scenario.expected() == null || !scenario.expected().mappingContract()) {
                    diagnostics.add(error("MAPPING_CONTRACT_REQUIRED", id,
                        "mappingContract=true is only allowed for database.required=true"));
                }
            } else if (scenario.expected() != null && scenario.expected().mappingContract()) {
                diagnostics.add(error("MAPPING_CONTRACT_REQUIRES_DB", id,
                    "mappingContract=true requires database.required=true"));
            }
            validateExpectations(scenario, diagnostics);
        }

        for (String feature : featureIds) {
            if (!referencedFeatures.contains(feature)) {
                diagnostics.add(warning("UNREFERENCED_FEATURE", null,
                    "feature is not referenced by any scenario: " + feature));
            }
        }
        return new CorpusValidationResult(diagnostics);
    }

    private void validateExpectations(CorpusScenario scenario,
                                      List<CorpusValidationDiagnostic> diagnostics) {
        String id = scenario.id();
        CorpusExpectation expected = scenario.expected();
        if (expected == null) {
            diagnostics.add(error("EXPECTATION_MISSING", id, "scenario needs an expected block"));
            return;
        }
        if (expected.exactClasses() != null && expected.minimumClasses() != null) {
            diagnostics.add(error("CONTRADICTORY_EXPECTATIONS", id,
                "exactClasses and minimumClasses are mutually exclusive"));
        }

        if (expected.blockingDiagnostics() < 0) {
            diagnostics.add(error("INVALID_EXPECTATION", id,
                "blockingDiagnostics must be >= 0"));
        }
        if (expected.mappingContract() && scenario.database() == null) {
            diagnostics.add(error("MAPPING_CONTRACT_REQUIRES_DB", id,
                "mappingContract=true requires database.required=true"));
        }
        for (ExpectedDiagnostic expectedDiagnostic : expected.diagnostics()) {
            if (expectedDiagnostic.code() == null || expectedDiagnostic.code().isBlank()) {
                diagnostics.add(error("EXPECTED_DIAGNOSTIC_INVALID", id,
                    "expected diagnostic without code"));
            }
            if (expectedDiagnostic.minimumCount() != null
                && expectedDiagnostic.maximumCount() != null
                && expectedDiagnostic.minimumCount() > expectedDiagnostic.maximumCount()) {
                diagnostics.add(error("EXPECTED_DIAGNOSTIC_INVALID", id,
                    "minimumCount exceeds maximumCount for " + expectedDiagnostic.code()));
            }
        }
    }

    private static CorpusValidationDiagnostic error(String code, String scenarioId, String message) {
        return new CorpusValidationDiagnostic(CorpusValidationSeverity.ERROR, code, scenarioId, message);
    }

    private static CorpusValidationDiagnostic warning(String code, String scenarioId, String message) {
        return new CorpusValidationDiagnostic(CorpusValidationSeverity.WARNING, code, scenarioId, message);
    }
}

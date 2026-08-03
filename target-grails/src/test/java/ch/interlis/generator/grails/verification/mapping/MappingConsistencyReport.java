package ch.interlis.generator.grails.verification.mapping;

import java.util.List;

/**
 * Mapping-Consistency-Report (Spezifikation §34.4).
 */
public record MappingConsistencyReport(
    String scenarioId,
    List<MappingMismatch> mismatches,
    List<MappingMismatch> documentedDifferences
) {

    public MappingConsistencyReport {
        mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
        documentedDifferences = documentedDifferences == null
            ? List.of() : List.copyOf(documentedDifferences);
    }

    public MappingConsistencyReport(String scenarioId, List<MappingMismatch> mismatches) {
        this(scenarioId, mismatches, List.of());
    }

    public boolean isConsistent() {
        return mismatches.isEmpty();
    }
}

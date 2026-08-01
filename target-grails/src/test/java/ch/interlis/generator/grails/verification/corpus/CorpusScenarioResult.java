package ch.interlis.generator.grails.verification.corpus;

import ch.interlis.generator.grails.verification.report.VerificationStatus;

import java.util.List;

/**
 * Ergebnis eines Corpus-Szenario-Laufs (Spezifikation §26.3).
 */
public record CorpusScenarioResult(
    String scenarioId,
    VerificationStatus status,
    CorpusObservedCounts counts,
    List<ObservedDiagnostic> diagnostics,
    List<String> generatedFiles,
    String generatedFilesFingerprint,
    List<String> evidenceFiles
) {

    public CorpusScenarioResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        generatedFiles = generatedFiles == null ? List.of() : List.copyOf(generatedFiles);
        evidenceFiles = evidenceFiles == null ? List.of() : List.copyOf(evidenceFiles);
    }
}

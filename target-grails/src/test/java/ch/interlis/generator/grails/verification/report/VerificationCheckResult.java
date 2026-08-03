package ch.interlis.generator.grails.verification.report;

import java.util.List;

/**
 * Ergebnis einer einzelnen Verifikations-Prüfung (Spezifikation §12.1).
 */
public record VerificationCheckResult(
    String id,
    VerificationStatus status,
    String summary,
    List<String> evidenceFiles,
    List<String> diagnostics
) {

    public VerificationCheckResult {
        evidenceFiles = evidenceFiles == null ? List.of() : List.copyOf(evidenceFiles);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}

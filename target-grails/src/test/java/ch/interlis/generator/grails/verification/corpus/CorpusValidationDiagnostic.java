package ch.interlis.generator.grails.verification.corpus;

/**
 * Validierungs-Diagnostic des Corpus (Spezifikation §24.3).
 */
public record CorpusValidationDiagnostic(
    CorpusValidationSeverity severity,
    String code,
    String scenarioId,
    String message
) {

    public boolean blocking() {
        return severity == CorpusValidationSeverity.ERROR;
    }
}

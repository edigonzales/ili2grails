package ch.interlis.generator.grails.verification.corpus;

/**
 * Erwartete Diagnostic innerhalb eines Corpus-Szenarios (Spezifikation §23.5).
 */
public record ExpectedDiagnostic(
    String source,
    String code,
    String severity,
    Integer minimumCount,
    Integer maximumCount
) {
}

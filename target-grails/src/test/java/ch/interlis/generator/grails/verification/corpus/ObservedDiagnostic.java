package ch.interlis.generator.grails.verification.corpus;

/**
 * Beobachtete Diagnostic eines Corpus-Laufs.
 */
public record ObservedDiagnostic(
    String source,
    String code,
    String severity,
    String message,
    String subject
) {
}

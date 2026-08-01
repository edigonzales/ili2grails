package ch.interlis.generator.grails.verification.report;

/**
 * Status einer Verifikations-Prüfung (Spezifikation §12.1).
 */
public enum VerificationStatus {
    PASSED,
    FAILED,
    SKIPPED_INFRASTRUCTURE,
    FAILED_INFRASTRUCTURE
}

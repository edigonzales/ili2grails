package ch.interlis.generator.grails.verification.report;

import ch.interlis.generator.grails.verification.environment.VerificationEnvironment;

import java.util.List;

/**
 * Gemeinsame Verification-Summary (Spezifikation §12.1).
 */
public record VerificationSummary(
    int schemaVersion,
    String commit,
    VerificationEnvironment environment,
    List<VerificationCheckResult> checks
) {

    public VerificationSummary {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    /**
     * Keine Prüfung fehlgeschlagen (FAILED oder FAILED_INFRASTRUCTURE).
     */
    public boolean passed() {
        return checks.stream().noneMatch(check -> check.status() == VerificationStatus.FAILED
            || check.status() == VerificationStatus.FAILED_INFRASTRUCTURE);
    }

    /**
     * Keine Prüfung übersprungen; alle Required-Tests liefen.
     */
    public boolean complete() {
        return checks.stream().noneMatch(check -> check.status() == VerificationStatus.SKIPPED_INFRASTRUCTURE);
    }
}

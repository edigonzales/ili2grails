package ch.interlis.generator.grails.verification.report;

import java.nio.file.Path;

/**
 * Entry point für den Gradle-Task :target-grails:writeVerificationSummary.
 * Wird in der Report-Phase vollständig implementiert.
 */
public final class VerificationSummaryTask {

    private VerificationSummaryTask() {
    }

    public static void main(String[] args) {
        String reportDir = System.getProperty("reportDir");
        if (reportDir == null) {
            throw new IllegalStateException("System property reportDir is required");
        }
        System.out.println("writeVerificationSummary: report dir=" + Path.of(reportDir).toAbsolutePath());
    }
}

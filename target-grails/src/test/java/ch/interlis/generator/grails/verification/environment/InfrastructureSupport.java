package ch.interlis.generator.grails.verification.environment;

import org.opentest4j.TestAbortedException;

/**
 * Required-Modus-Semantik für erweiterte Tests (Spezifikation §10.3).
 *
 * <p>Im Required-Modus ist ein fehlendes Werkzeug ein Testfehler
 * ({@code FAILED_INFRASTRUCTURE}), im optionalen Modus ein sauberer Skip
 * ({@code SKIPPED_INFRASTRUCTURE}). Die Marker-Texte landen so in den
 * JUnit-Reports und in der Verification-Summary.
 */
public final class InfrastructureSupport {

    private InfrastructureSupport() {
    }

    public static ExternalToolStatus requireTool(ExternalToolStatus status, boolean required,
                                                 String operation) {
        if (status.available()) {
            return status;
        }
        String message = operation + " requires " + status.tool() + ": " + status.diagnostic();
        if (required) {
            throw new AssertionError("FAILED_INFRASTRUCTURE " + message);
        }
        throw new TestAbortedException("SKIPPED_INFRASTRUCTURE " + message);
    }

    public static boolean required(String propertyName) {
        return Boolean.parseBoolean(System.getProperty(propertyName, "false"));
    }
}

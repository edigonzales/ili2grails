package ch.interlis.generator.grails.verification.environment;

/**
 * Verfügbarkeitszustand eines externen Werkzeugs (Spezifikation §10.1).
 */
public enum ToolAvailability {
    AVAILABLE,
    MISSING,
    INVALID,
    NOT_CHECKED
}

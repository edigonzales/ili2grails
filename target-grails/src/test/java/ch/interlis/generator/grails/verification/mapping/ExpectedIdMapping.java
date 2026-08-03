package ch.interlis.generator.grails.verification.mapping;

/**
 * Erwartete ID-Abbildung (Spezifikation §31.5).
 */
public record ExpectedIdMapping(
    String propertyName,
    String columnName,
    String generator
) {
}

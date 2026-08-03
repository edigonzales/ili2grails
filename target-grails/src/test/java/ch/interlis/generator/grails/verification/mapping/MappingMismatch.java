package ch.interlis.generator.grails.verification.mapping;

/**
 * Eine erklärte Mapping-Abweichung (Spezifikation §34.2).
 */
public record MappingMismatch(
    MappingMismatchCode code,
    String entity,
    String property,
    String expected,
    String actual,
    String explanation
) {
}

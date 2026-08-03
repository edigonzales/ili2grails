package ch.interlis.generator.grails.verification.corpus;

/**
 * Dokumentierte, konkrete Abweichung im Mapping-Vertrag (Spezifikation §34.6):
 * keine Wildcards, keine automatische Toleranz. Die Abweichung muss exakt
 * (Code, Entity, Property) übereinstimmen und eine Erklärung besitzen.
 */
public record AllowedDifference(
    String code,
    String entity,
    String property,
    String reason
) {
}

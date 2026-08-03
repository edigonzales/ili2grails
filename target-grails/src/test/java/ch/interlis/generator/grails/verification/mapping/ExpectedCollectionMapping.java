package ch.interlis.generator.grails.verification.mapping;

/**
 * Erwartete Collection-Abbildung (Spezifikation §31.4).
 */
public record ExpectedCollectionMapping(
    String propertyName,
    String elementDomainClass,
    String mappedByProperty,
    boolean persistent
) {
}

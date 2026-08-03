package ch.interlis.generator.grails.verification.mapping;

/**
 * Erwartete Association-Abbildung (Spezifikation §34.5).
 */
public record ExpectedAssociationMapping(
    String associationName,
    String linkDomainClass,
    String linkTable,
    String storageKind
) {
}

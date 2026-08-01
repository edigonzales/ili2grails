package ch.interlis.generator.grails;

/**
 * Beschreibt eine inverse/navigationale Related-Section: eine query-basierte
 * Navigation von der Owner-Klasse zu Related-Records, deren FK auf der
 * Related-Klasse liegt.
 *
 * <p>Eine inverse Related-Section ist keine GORM-Collection. Normale inverse
 * Referenzen haben {@code persistentCollectionBacked = false}.</p>
 */
public record GrailsInverseRelationshipPlan(
    String ownerIliClassName,
    String collectionPropertyName,
    String relatedIliClassName,
    String relatedDomainQualifiedName,
    String relatedPropertyName,
    String relationshipName,
    String label,
    String relatedLabel,
    boolean mandatory,
    boolean visible,
    boolean writable,
    boolean persistentCollectionBacked
) {
}

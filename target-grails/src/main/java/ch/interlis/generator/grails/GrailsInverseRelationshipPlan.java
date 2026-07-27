package ch.interlis.generator.grails;

/**
 * Describes a generated to-many collection whose persisted foreign key lives
 * on the related domain class.
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
    boolean writable
) {
}

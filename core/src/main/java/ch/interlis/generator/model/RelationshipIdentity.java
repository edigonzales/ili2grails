package ch.interlis.generator.model;

import java.util.Objects;

/**
 * Fachliche Identität einer Relationship.
 *
 * <p>Die Identität ist bewusst nicht nur der Relationship-Name: dieselbe
 * physische FK-Spalte kann in mehreren Merge-Schritten unterschiedliche
 * semantische Namen tragen.</p>
 */
public record RelationshipIdentity(
    String sourceClass,
    String targetClass,
    String sourceAttribute,
    String physicalName,
    String associationName,
    String targetRoleName,
    RelationshipMetadata.SemanticKind semanticKind
) {

    public RelationshipIdentity {
        Objects.requireNonNull(sourceClass, "sourceClass");
        Objects.requireNonNull(targetClass, "targetClass");
    }

    public static RelationshipIdentity of(RelationshipMetadata relationship) {
        return new RelationshipIdentity(
            relationship.getSourceClass(),
            relationship.getTargetClass(),
            relationship.getSourceAttribute(),
            relationship.getPhysicalName(),
            relationship.getAssociationName(),
            relationship.getTargetRoleName(),
            relationship.getSemanticKind()
        );
    }
}

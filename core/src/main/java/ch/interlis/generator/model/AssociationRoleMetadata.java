package ch.interlis.generator.model;

import java.util.Objects;

/**
 * Immutable Rolle einer INTERLIS-Association.
 */
public final class AssociationRoleMetadata {

    private final String name;
    private final String targetClass;
    private final String oppositeRoleName;
    private final Cardinality cardinality;
    private final boolean mandatory;
    private final boolean ordered;
    private final boolean external;
    private final boolean composition;
    private final String sourceAttribute;
    private final String targetAttribute;
    private final String physicalName;
    private final String semanticName;
    private final String source;
    private final RelationshipMetadata.MergeReason mergeReason;
    private final RelationshipMetadata.MergeConfidence mergeConfidence;
    private final String mergeToken;

    public AssociationRoleMetadata(String name,
                            String targetClass,
                            String oppositeRoleName,
                            Cardinality cardinality,
                            boolean mandatory,
                            boolean ordered,
                            boolean external,
                            boolean composition,
                            String sourceAttribute,
                            String targetAttribute,
                            String physicalName,
                            String semanticName,
                            String source,
                            RelationshipMetadata.MergeReason mergeReason,
                            RelationshipMetadata.MergeConfidence mergeConfidence,
                            String mergeToken) {
        this.name = Objects.requireNonNull(name, "name");
        this.targetClass = targetClass;
        this.oppositeRoleName = oppositeRoleName;
        this.cardinality = cardinality;
        this.mandatory = mandatory;
        this.ordered = ordered;
        this.external = external;
        this.composition = composition;
        this.sourceAttribute = sourceAttribute;
        this.targetAttribute = targetAttribute;
        this.physicalName = physicalName;
        this.semanticName = semanticName;
        this.source = source;
        this.mergeReason = mergeReason;
        this.mergeConfidence = mergeConfidence;
        this.mergeToken = mergeToken;
    }

    public static ch.interlis.generator.model.builder.AssociationRoleMetadataBuilder builder(String name) {
        return new ch.interlis.generator.model.builder.AssociationRoleMetadataBuilder(name);
    }

    public ch.interlis.generator.model.builder.AssociationRoleMetadataBuilder toBuilder() {
        return ch.interlis.generator.model.builder.AssociationRoleMetadataBuilder.from(this);
    }

    public String getName() {
        return name;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public String getOppositeRoleName() {
        return oppositeRoleName;
    }

    public Cardinality getCardinality() {
        return cardinality;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public boolean isExternal() {
        return external;
    }

    public boolean isComposition() {
        return composition;
    }

    public String getSourceAttribute() {
        return sourceAttribute;
    }

    public String getTargetAttribute() {
        return targetAttribute;
    }

    public String getPhysicalName() {
        return physicalName;
    }

    public String getSemanticName() {
        return semanticName;
    }

    public String getSource() {
        return source;
    }

    public RelationshipMetadata.MergeReason getMergeReason() {
        return mergeReason;
    }

    public RelationshipMetadata.MergeConfidence getMergeConfidence() {
        return mergeConfidence;
    }

    public String getMergeToken() {
        return mergeToken;
    }

    @Override
    public String toString() {
        return "AssociationRoleMetadata{" +
            "name='" + name + '\'' +
            ", targetClass='" + targetClass + '\'' +
            '}';
    }
}

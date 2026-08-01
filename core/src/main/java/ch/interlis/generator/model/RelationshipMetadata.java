package ch.interlis.generator.model;

import java.util.Objects;

/**
 * Immutable Metadaten einer Beziehung.
 */
public final class RelationshipMetadata {

    public enum RelationType {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY,
        ASSOCIATION
    }

    public enum SemanticKind {
        ILI2DB_FK,
        REFERENCE_ATTRIBUTE,
        COMPOSITION_ATTRIBUTE,
        ASSOCIATION_ROLE
    }

    public enum MergeReason {
        ILI2DB_ONLY,
        ILI2C_ONLY,
        EXACT_NAME,
        EXACT_SOURCE_ATTRIBUTE,
        EXACT_TARGET_ROLE,
        NORMALIZED_TOKEN
    }

    public enum MergeConfidence {
        NONE,
        EXACT,
        MEDIUM
    }

    private final String name;
    private final String sourceClass;
    private final String targetClass;
    private final RelationType type;
    private final SemanticKind semanticKind;
    private final String sourceAttribute;
    private final String targetAttribute;
    private final String associationName;
    private final String sourceRoleName;
    private final String targetRoleName;
    private final String oppositeRoleName;
    private final Cardinality cardinality;
    private final boolean mandatory;
    private final boolean ordered;
    private final boolean external;
    private final boolean composition;
    private final String source;
    private final String physicalName;
    private final String semanticName;
    private final MergeReason mergeReason;
    private final MergeConfidence mergeConfidence;
    private final String mergeToken;

    public RelationshipMetadata(String name,
                         String sourceClass,
                         String targetClass,
                         RelationType type,
                         SemanticKind semanticKind,
                         String sourceAttribute,
                         String targetAttribute,
                         String associationName,
                         String sourceRoleName,
                         String targetRoleName,
                         String oppositeRoleName,
                         Cardinality cardinality,
                         boolean mandatory,
                         boolean ordered,
                         boolean external,
                         boolean composition,
                         String source,
                         String physicalName,
                         String semanticName,
                         MergeReason mergeReason,
                         MergeConfidence mergeConfidence,
                         String mergeToken) {
        this.name = Objects.requireNonNull(name, "name");
        this.sourceClass = Objects.requireNonNull(sourceClass, "sourceClass");
        this.targetClass = Objects.requireNonNull(targetClass, "targetClass");
        this.type = type;
        this.semanticKind = semanticKind;
        this.sourceAttribute = sourceAttribute;
        this.targetAttribute = targetAttribute;
        this.associationName = associationName;
        this.sourceRoleName = sourceRoleName;
        this.targetRoleName = targetRoleName;
        this.oppositeRoleName = oppositeRoleName;
        this.cardinality = cardinality;
        this.mandatory = mandatory;
        this.ordered = ordered;
        this.external = external;
        this.composition = composition;
        this.source = source;
        this.physicalName = physicalName;
        this.semanticName = semanticName;
        this.mergeReason = mergeReason;
        this.mergeConfidence = mergeConfidence;
        this.mergeToken = mergeToken;
    }

    public static ch.interlis.generator.model.builder.RelationshipMetadataBuilder builder(String name) {
        return new ch.interlis.generator.model.builder.RelationshipMetadataBuilder(name);
    }

    public ch.interlis.generator.model.builder.RelationshipMetadataBuilder toBuilder() {
        return ch.interlis.generator.model.builder.RelationshipMetadataBuilder.from(this);
    }

    public RelationshipIdentity identity() {
        return RelationshipIdentity.of(this);
    }

    public String getName() {
        return name;
    }

    public String getSourceClass() {
        return sourceClass;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public RelationType getType() {
        return type;
    }

    public SemanticKind getSemanticKind() {
        return semanticKind;
    }

    public String getSourceAttribute() {
        return sourceAttribute;
    }

    public String getTargetAttribute() {
        return targetAttribute;
    }

    public String getAssociationName() {
        return associationName;
    }

    public String getSourceRoleName() {
        return sourceRoleName;
    }

    public String getTargetRoleName() {
        return targetRoleName;
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

    public String getSource() {
        return source;
    }

    public String getPhysicalName() {
        return physicalName;
    }

    public String getSemanticName() {
        return semanticName;
    }

    public MergeReason getMergeReason() {
        return mergeReason;
    }

    public MergeConfidence getMergeConfidence() {
        return mergeConfidence;
    }

    public String getMergeToken() {
        return mergeToken;
    }

    @Override
    public String toString() {
        return "RelationshipMetadata{" +
            "name='" + name + '\'' +
            ", sourceClass='" + sourceClass + '\'' +
            ", targetClass='" + targetClass + '\'' +
            ", type=" + type +
            ", semanticKind=" + semanticKind +
            '}';
    }

}


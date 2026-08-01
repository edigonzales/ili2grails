package ch.interlis.generator.model.builder;

import ch.interlis.generator.model.Cardinality;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.Objects;

/**
 * Mutable Builder für {@link RelationshipMetadata}.
 */
public final class RelationshipMetadataBuilder {

    private String name;
    private String sourceClass;
    private String targetClass;
    private RelationshipMetadata.RelationType type;
    private RelationshipMetadata.SemanticKind semanticKind;
    private String sourceAttribute;
    private String targetAttribute;
    private String associationName;
    private String sourceRoleName;
    private String targetRoleName;
    private String oppositeRoleName;
    private Cardinality cardinality;
    private boolean mandatory;
    private boolean ordered;
    private boolean external;
    private boolean composition;
    private String source;
    private String physicalName;
    private String semanticName;
    private RelationshipMetadata.MergeReason mergeReason;
    private RelationshipMetadata.MergeConfidence mergeConfidence;
    private String mergeToken;

    public RelationshipMetadataBuilder(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public static RelationshipMetadataBuilder from(RelationshipMetadata relationship) {
        RelationshipMetadataBuilder builder = new RelationshipMetadataBuilder(relationship.getName());
        builder.sourceClass = relationship.getSourceClass();
        builder.targetClass = relationship.getTargetClass();
        builder.type = relationship.getType();
        builder.semanticKind = relationship.getSemanticKind();
        builder.sourceAttribute = relationship.getSourceAttribute();
        builder.targetAttribute = relationship.getTargetAttribute();
        builder.associationName = relationship.getAssociationName();
        builder.sourceRoleName = relationship.getSourceRoleName();
        builder.targetRoleName = relationship.getTargetRoleName();
        builder.oppositeRoleName = relationship.getOppositeRoleName();
        builder.cardinality = relationship.getCardinality();
        builder.mandatory = relationship.isMandatory();
        builder.ordered = relationship.isOrdered();
        builder.external = relationship.isExternal();
        builder.composition = relationship.isComposition();
        builder.source = relationship.getSource();
        builder.physicalName = relationship.getPhysicalName();
        builder.semanticName = relationship.getSemanticName();
        builder.mergeReason = relationship.getMergeReason();
        builder.mergeConfidence = relationship.getMergeConfidence();
        builder.mergeToken = relationship.getMergeToken();
        return builder;
    }

    public String name() {
        return name;
    }

    public String sourceClass() {
        return sourceClass;
    }

    public String targetClass() {
        return targetClass;
    }

    public RelationshipMetadata.RelationType type() {
        return type;
    }

    public RelationshipMetadata.SemanticKind semanticKind() {
        return semanticKind;
    }

    public String sourceAttribute() {
        return sourceAttribute;
    }

    public String targetAttribute() {
        return targetAttribute;
    }

    public String associationName() {
        return associationName;
    }

    public String sourceRoleName() {
        return sourceRoleName;
    }

    public String targetRoleName() {
        return targetRoleName;
    }

    public String oppositeRoleName() {
        return oppositeRoleName;
    }

    public Cardinality cardinality() {
        return cardinality;
    }

    public boolean mandatory() {
        return mandatory;
    }

    public boolean ordered() {
        return ordered;
    }

    public boolean external() {
        return external;
    }

    public boolean composition() {
        return composition;
    }

    public String source() {
        return source;
    }

    public String physicalName() {
        return physicalName;
    }

    public String semanticName() {
        return semanticName;
    }

    public RelationshipMetadata.MergeReason mergeReason() {
        return mergeReason;
    }

    public RelationshipMetadata.MergeConfidence mergeConfidence() {
        return mergeConfidence;
    }

    public String mergeToken() {
        return mergeToken;
    }

    public RelationshipMetadataBuilder sourceClass(String sourceClass) {
        this.sourceClass = sourceClass;
        return this;
    }

    public RelationshipMetadataBuilder targetClass(String targetClass) {
        this.targetClass = targetClass;
        return this;
    }

    public RelationshipMetadataBuilder type(RelationshipMetadata.RelationType type) {
        this.type = type;
        return this;
    }

    public RelationshipMetadataBuilder semanticKind(RelationshipMetadata.SemanticKind semanticKind) {
        this.semanticKind = semanticKind;
        return this;
    }

    public RelationshipMetadataBuilder sourceAttribute(String sourceAttribute) {
        this.sourceAttribute = sourceAttribute;
        return this;
    }

    public RelationshipMetadataBuilder targetAttribute(String targetAttribute) {
        this.targetAttribute = targetAttribute;
        return this;
    }

    public RelationshipMetadataBuilder associationName(String associationName) {
        this.associationName = associationName;
        return this;
    }

    public RelationshipMetadataBuilder sourceRoleName(String sourceRoleName) {
        this.sourceRoleName = sourceRoleName;
        return this;
    }

    public RelationshipMetadataBuilder targetRoleName(String targetRoleName) {
        this.targetRoleName = targetRoleName;
        return this;
    }

    public RelationshipMetadataBuilder oppositeRoleName(String oppositeRoleName) {
        this.oppositeRoleName = oppositeRoleName;
        return this;
    }

    public RelationshipMetadataBuilder cardinality(Cardinality cardinality) {
        this.cardinality = cardinality;
        return this;
    }

    public RelationshipMetadataBuilder cardinality(int minSource, int maxSource, int minTarget, int maxTarget) {
        this.cardinality = Cardinality.of(minSource, maxSource, minTarget, maxTarget);
        return this;
    }

    public RelationshipMetadataBuilder mandatory(boolean mandatory) {
        this.mandatory = mandatory;
        return this;
    }

    public RelationshipMetadataBuilder ordered(boolean ordered) {
        this.ordered = ordered;
        return this;
    }

    public RelationshipMetadataBuilder external(boolean external) {
        this.external = external;
        return this;
    }

    public RelationshipMetadataBuilder composition(boolean composition) {
        this.composition = composition;
        return this;
    }

    public RelationshipMetadataBuilder source(String source) {
        this.source = source;
        return this;
    }

    public RelationshipMetadataBuilder physicalName(String physicalName) {
        this.physicalName = physicalName;
        return this;
    }

    public RelationshipMetadataBuilder semanticName(String semanticName) {
        this.semanticName = semanticName;
        return this;
    }

    public RelationshipMetadataBuilder mergeReason(RelationshipMetadata.MergeReason mergeReason) {
        this.mergeReason = mergeReason;
        return this;
    }

    public RelationshipMetadataBuilder mergeConfidence(RelationshipMetadata.MergeConfidence mergeConfidence) {
        this.mergeConfidence = mergeConfidence;
        return this;
    }

    public RelationshipMetadataBuilder mergeToken(String mergeToken) {
        this.mergeToken = mergeToken;
        return this;
    }

    /** Unvalidierter Build; Abschluss über die ModelMetadataFactory. */
    public RelationshipMetadata buildUnchecked() {
        return new RelationshipMetadata(
            name,
            sourceClass,
            targetClass,
            type,
            semanticKind,
            sourceAttribute,
            targetAttribute,
            associationName,
            sourceRoleName,
            targetRoleName,
            oppositeRoleName,
            cardinality,
            mandatory,
            ordered,
            external,
            composition,
            source,
            physicalName,
            semanticName,
            mergeReason,
            mergeConfidence,
            mergeToken
        );
    }
}

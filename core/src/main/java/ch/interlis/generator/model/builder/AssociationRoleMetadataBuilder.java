package ch.interlis.generator.model.builder;

import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.Cardinality;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable Builder für {@link AssociationRoleMetadata}.
 */
public final class AssociationRoleMetadataBuilder {

    private String name;
    private String targetClass;
    private String oppositeRoleName;
    private Cardinality cardinality;
    private boolean mandatory;
    private boolean ordered;
    private boolean external;
    private boolean composition;
    private String sourceAttribute;
    private String targetAttribute;
    private String physicalName;
    private String semanticName;
    private String source;
    private RelationshipMetadata.MergeReason mergeReason;
    private RelationshipMetadata.MergeConfidence mergeConfidence;
    private String mergeToken;

    public AssociationRoleMetadataBuilder(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public static AssociationRoleMetadataBuilder from(AssociationRoleMetadata role) {
        AssociationRoleMetadataBuilder builder = new AssociationRoleMetadataBuilder(role.getName());
        builder.targetClass = role.getTargetClass();
        builder.oppositeRoleName = role.getOppositeRoleName();
        builder.cardinality = role.getCardinality();
        builder.mandatory = role.isMandatory();
        builder.ordered = role.isOrdered();
        builder.external = role.isExternal();
        builder.composition = role.isComposition();
        builder.sourceAttribute = role.getSourceAttribute();
        builder.targetAttribute = role.getTargetAttribute();
        builder.physicalName = role.getPhysicalName();
        builder.semanticName = role.getSemanticName();
        builder.source = role.getSource();
        builder.mergeReason = role.getMergeReason();
        builder.mergeConfidence = role.getMergeConfidence();
        builder.mergeToken = role.getMergeToken();
        return builder;
    }

    public String name() {
        return name;
    }

    public String targetClass() {
        return targetClass;
    }

    public AssociationRoleMetadataBuilder targetClass(String targetClass) {
        this.targetClass = targetClass;
        return this;
    }

    public AssociationRoleMetadataBuilder oppositeRoleName(String oppositeRoleName) {
        this.oppositeRoleName = oppositeRoleName;
        return this;
    }

    public AssociationRoleMetadataBuilder cardinality(Cardinality cardinality) {
        this.cardinality = cardinality;
        return this;
    }

    public AssociationRoleMetadataBuilder mandatory(boolean mandatory) {
        this.mandatory = mandatory;
        return this;
    }

    public AssociationRoleMetadataBuilder ordered(boolean ordered) {
        this.ordered = ordered;
        return this;
    }

    public AssociationRoleMetadataBuilder external(boolean external) {
        this.external = external;
        return this;
    }

    public AssociationRoleMetadataBuilder composition(boolean composition) {
        this.composition = composition;
        return this;
    }

    public AssociationRoleMetadataBuilder sourceAttribute(String sourceAttribute) {
        this.sourceAttribute = sourceAttribute;
        return this;
    }

    public AssociationRoleMetadataBuilder targetAttribute(String targetAttribute) {
        this.targetAttribute = targetAttribute;
        return this;
    }

    public AssociationRoleMetadataBuilder physicalName(String physicalName) {
        this.physicalName = physicalName;
        return this;
    }

    public AssociationRoleMetadataBuilder semanticName(String semanticName) {
        this.semanticName = semanticName;
        return this;
    }

    public AssociationRoleMetadataBuilder source(String source) {
        this.source = source;
        return this;
    }

    public AssociationRoleMetadataBuilder mergeReason(RelationshipMetadata.MergeReason mergeReason) {
        this.mergeReason = mergeReason;
        return this;
    }

    public AssociationRoleMetadataBuilder mergeConfidence(RelationshipMetadata.MergeConfidence mergeConfidence) {
        this.mergeConfidence = mergeConfidence;
        return this;
    }

    public AssociationRoleMetadataBuilder mergeToken(String mergeToken) {
        this.mergeToken = mergeToken;
        return this;
    }

    /** Unvalidierter Build; Abschluss über die ModelMetadataFactory. */
    public AssociationRoleMetadata buildUnchecked() {
        return new AssociationRoleMetadata(
            name,
            targetClass,
            oppositeRoleName,
            cardinality,
            mandatory,
            ordered,
            external,
            composition,
            sourceAttribute,
            targetAttribute,
            physicalName,
            semanticName,
            source,
            mergeReason,
            mergeConfidence,
            mergeToken
        );
    }
}

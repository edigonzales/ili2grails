package ch.interlis.generator.model;

/**
 * Rolle einer INTERLIS-Association innerhalb der framework-agnostischen Core-IR.
 */
public class AssociationRoleMetadata {

    private String name;
    private String targetClass;
    private String oppositeRoleName;
    private RelationshipMetadata.Cardinality cardinality;
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

    public AssociationRoleMetadata(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }

    public String getOppositeRoleName() {
        return oppositeRoleName;
    }

    public void setOppositeRoleName(String oppositeRoleName) {
        this.oppositeRoleName = oppositeRoleName;
    }

    public RelationshipMetadata.Cardinality getCardinality() {
        return cardinality;
    }

    public void setCardinality(RelationshipMetadata.Cardinality cardinality) {
        this.cardinality = cardinality;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public boolean isComposition() {
        return composition;
    }

    public void setComposition(boolean composition) {
        this.composition = composition;
    }

    public String getSourceAttribute() {
        return sourceAttribute;
    }

    public void setSourceAttribute(String sourceAttribute) {
        this.sourceAttribute = sourceAttribute;
    }

    public String getTargetAttribute() {
        return targetAttribute;
    }

    public void setTargetAttribute(String targetAttribute) {
        this.targetAttribute = targetAttribute;
    }

    public String getPhysicalName() {
        return physicalName;
    }

    public void setPhysicalName(String physicalName) {
        this.physicalName = physicalName;
    }

    public String getSemanticName() {
        return semanticName;
    }

    public void setSemanticName(String semanticName) {
        this.semanticName = semanticName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public RelationshipMetadata.MergeReason getMergeReason() {
        return mergeReason;
    }

    public void setMergeReason(RelationshipMetadata.MergeReason mergeReason) {
        this.mergeReason = mergeReason;
    }

    public RelationshipMetadata.MergeConfidence getMergeConfidence() {
        return mergeConfidence;
    }

    public void setMergeConfidence(RelationshipMetadata.MergeConfidence mergeConfidence) {
        this.mergeConfidence = mergeConfidence;
    }

    public String getMergeToken() {
        return mergeToken;
    }

    public void setMergeToken(String mergeToken) {
        this.mergeToken = mergeToken;
    }
}

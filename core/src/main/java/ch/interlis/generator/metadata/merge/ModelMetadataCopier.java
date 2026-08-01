package ch.interlis.generator.metadata.merge;

import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiefe Kopie der Core-IR, damit der Merge seine Eingaben nicht mutiert.
 *
 * <p>Die physische Wahrheit (ili2db) wird kopiert und dient als Basis des
 * Merge-Ziels. Die semantische Eingabe (ili2c) wird niemals mutiert.</p>
 */
public final class ModelMetadataCopier {

    private ModelMetadataCopier() {
    }

    public static ModelMetadata copy(ModelMetadata source) {
        ModelMetadata target = new ModelMetadata(source.getModelName());
        target.setSchemaName(source.getSchemaName());
        target.setIliVersion(source.getIliVersion());
        target.setModelVersion(source.getModelVersion());
        target.setImportDate(copyDate(source.getImportDate()));
        target.setIli2dbVersion(source.getIli2dbVersion());
        target.setSettings(new LinkedHashMap<>(source.getSettings()));

        for (ClassMetadata classMetadata : source.getAllClasses()) {
            target.addClass(copyClass(classMetadata));
        }
        for (RelationshipMetadata relationship : source.getRelationships()) {
            target.addRelationship(copyRelationship(relationship));
        }
        for (ClassMetadata classMetadata : source.getAllClasses()) {
            ClassMetadata targetClass = target.getClass(classMetadata.getName());
            if (targetClass == null) {
                continue;
            }
            for (RelationshipMetadata relationship : classMetadata.getRelationships()) {
                if (!containsSame(target.getRelationships(), relationship)) {
                    targetClass.addRelationship(copyRelationship(relationship));
                }
            }
        }
        for (EnumMetadata enumMetadata : source.getAllEnums()) {
            target.addEnum(copyEnum(enumMetadata));
        }
        for (AssociationMetadata association : source.getAllAssociations()) {
            target.addAssociation(copyAssociation(association));
        }
        return target;
    }

    private static Date copyDate(Date date) {
        return date == null ? null : new Date(date.getTime());
    }

    public static ClassMetadata copyClass(ClassMetadata source) {
        ClassMetadata target = new ClassMetadata(source.getName());
        target.setTopicName(source.getTopicName());
        target.setTableName(source.getTableName());
        target.setSqlName(source.getSqlName());
        target.setDocumentation(source.getDocumentation());
        target.setAbstract(source.isAbstract());
        target.setBaseClass(source.getBaseClass());
        target.setKind(source.getKind());
        target.setInheritanceStrategy(source.getInheritanceStrategy());
        target.setLabels(new LinkedHashMap<>(source.getLabels()));
        for (AttributeMetadata attribute : source.getAllAttributes()) {
            target.addAttribute(copyAttribute(attribute));
        }
        return target;
    }

    public static AttributeMetadata copyAttribute(AttributeMetadata source) {
        AttributeMetadata target = new AttributeMetadata(source.getName());
        target.setQualifiedName(source.getQualifiedName());
        target.setColumnName(source.getColumnName());
        target.setSqlName(source.getSqlName());
        target.setIliType(source.getIliType());
        target.setDomainName(source.getDomainName());
        target.setCoreType(source.getCoreType());
        target.setJavaType(source.getJavaType());
        target.setDbType(source.getDbType());
        target.setMandatory(source.isMandatory());
        target.setPrimaryKey(source.isPrimaryKey());
        target.setForeignKey(source.isForeignKey());
        target.setGeometry(source.isGeometry());
        target.setGeometrySrid(source.getGeometrySrid());
        target.setGeometryKind(source.getGeometryKindEnum());
        target.setGeometryHasZ(source.getGeometryHasZ());
        target.setGeometryHasM(source.getGeometryHasM());
        target.setAllowEmptyGeometry(source.getAllowEmptyGeometry());
        target.setDocumentation(source.getDocumentation());
        target.setMaxLength(source.getMaxLength());
        target.setMinValue(source.getMinValue());
        target.setMaxValue(source.getMaxValue());
        target.setPrecision(source.getPrecision());
        target.setScale(source.getScale());
        target.setCardinalityMin(source.getCardinalityMin());
        target.setCardinalityMax(source.getCardinalityMax());
        target.setOrdered(source.isOrdered());
        target.setEnumType(source.getEnumType());
        target.setUnit(source.getUnit());
        target.setReferencedClass(source.getReferencedClass());
        target.setReferencedAttribute(source.getReferencedAttribute());
        target.setLabels(new LinkedHashMap<>(source.getLabels()));
        for (EnumMetadata.EnumValue value : source.getEnumValues()) {
            target.addEnumValue(copyEnumValue(value));
        }
        return target;
    }

    public static RelationshipMetadata copyRelationship(RelationshipMetadata source) {
        RelationshipMetadata target = new RelationshipMetadata(source.getName());
        target.setSourceClass(source.getSourceClass());
        target.setTargetClass(source.getTargetClass());
        target.setType(source.getType());
        target.setSemanticKind(source.getSemanticKind());
        target.setSourceAttribute(source.getSourceAttribute());
        target.setTargetAttribute(source.getTargetAttribute());
        target.setAssociationName(source.getAssociationName());
        target.setSourceRoleName(source.getSourceRoleName());
        target.setTargetRoleName(source.getTargetRoleName());
        target.setOppositeRoleName(source.getOppositeRoleName());
        target.setCardinality(copyCardinality(source.getCardinality()));
        target.setMandatory(source.isMandatory());
        target.setOrdered(source.isOrdered());
        target.setExternal(source.isExternal());
        target.setComposition(source.isComposition());
        target.setSource(source.getSource());
        target.setPhysicalName(source.getPhysicalName());
        target.setSemanticName(source.getSemanticName());
        target.setMergeReason(source.getMergeReason());
        target.setMergeConfidence(source.getMergeConfidence());
        target.setMergeToken(source.getMergeToken());
        return target;
    }

    private static RelationshipMetadata.Cardinality copyCardinality(RelationshipMetadata.Cardinality source) {
        if (source == null) {
            return null;
        }
        return new RelationshipMetadata.Cardinality(
            source.getMinSource(),
            source.getMaxSource(),
            source.getMinTarget(),
            source.getMaxTarget()
        );
    }

    public static AssociationMetadata copyAssociation(AssociationMetadata source) {
        AssociationMetadata target = new AssociationMetadata(source.getName());
        target.setAssociationClass(source.getAssociationClass());
        target.setPhysicalTable(source.getPhysicalTable());
        target.setPhysicalSqlName(source.getPhysicalSqlName());
        for (AssociationRoleMetadata role : source.getRoles()) {
            target.addRole(copyAssociationRole(role));
        }
        for (AttributeMetadata attribute : source.getAllAttributes()) {
            target.addAttribute(copyAttribute(attribute));
        }
        return target;
    }

    public static AssociationRoleMetadata copyAssociationRole(AssociationRoleMetadata source) {
        AssociationRoleMetadata target = new AssociationRoleMetadata(source.getName());
        target.setTargetClass(source.getTargetClass());
        target.setOppositeRoleName(source.getOppositeRoleName());
        target.setCardinality(copyCardinality(source.getCardinality()));
        target.setMandatory(source.isMandatory());
        target.setOrdered(source.isOrdered());
        target.setExternal(source.isExternal());
        target.setComposition(source.isComposition());
        target.setSourceAttribute(source.getSourceAttribute());
        target.setTargetAttribute(source.getTargetAttribute());
        target.setPhysicalName(source.getPhysicalName());
        target.setSemanticName(source.getSemanticName());
        target.setSource(source.getSource());
        target.setMergeReason(source.getMergeReason());
        target.setMergeConfidence(source.getMergeConfidence());
        target.setMergeToken(source.getMergeToken());
        return target;
    }

    public static EnumMetadata copyEnum(EnumMetadata source) {
        EnumMetadata target = new EnumMetadata(source.getName());
        target.setExtendable(source.isExtendable());
        target.setBaseEnum(source.getBaseEnum());
        for (EnumMetadata.EnumValue value : source.getValues()) {
            target.addValue(copyEnumValue(value));
        }
        return target;
    }

    public static EnumMetadata.EnumValue copyEnumValue(EnumMetadata.EnumValue source) {
        EnumMetadata.EnumValue target = new EnumMetadata.EnumValue(source.getIliCode(), source.getSeq());
        target.setDispName(source.getDispName());
        target.getLabels().putAll(source.getLabels());
        return target;
    }

    private static boolean containsSame(java.util.List<RelationshipMetadata> relationships,
                                        RelationshipMetadata candidate) {
        return relationships.stream().anyMatch(existing -> sameRelationship(existing, candidate));
    }

    private static boolean sameRelationship(RelationshipMetadata left, RelationshipMetadata right) {
        return java.util.Objects.equals(left.getName(), right.getName())
            && java.util.Objects.equals(left.getSourceClass(), right.getSourceClass())
            && java.util.Objects.equals(left.getTargetClass(), right.getTargetClass())
            && java.util.Objects.equals(left.getSourceAttribute(), right.getSourceAttribute())
            && java.util.Objects.equals(left.getTargetRoleName(), right.getTargetRoleName())
            && java.util.Objects.equals(left.getSemanticKind(), right.getSemanticKind());
    }

    public static Map<String, String> copyStringMap(Map<String, String> source) {
        return new LinkedHashMap<>(source);
    }
}

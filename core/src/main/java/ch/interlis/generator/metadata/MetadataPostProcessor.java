package ch.interlis.generator.metadata;

import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Nachbearbeitung des gemergten Metamodells: Typ-Fallbacks und konsistente
 * Association-Synchronisierung.
 *
 * <p>Die Association-Synchronisierung verwendet ausschliesslich kanonische
 * (gemergte, eindeutige) Relationships. Aus einem ambiguous Relationship wird
 * keine Association-Rolle abgeleitet. Rollen werden deterministisch sortiert.</p>
 */
public final class MetadataPostProcessor {

    /**
     * Führt die Nachbearbeitung aus.
     */
    public void process(ModelMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        inferMissingTypes(metadata);
        synchronizeAssociations(metadata);
    }

    /**
     * Ergänzt fehlende Typ-Fallbacks: {@link CoreType#UNKNOWN} → inferierter
     * Core-Typ, fehlender Java-Typ → inferierter Java-Typ.
     */
    void inferMissingTypes(ModelMetadata metadata) {
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            for (AttributeMetadata attribute : classMetadata.getAllAttributes()) {
                if (attribute.getCoreType() == CoreType.UNKNOWN) {
                    attribute.inferCoreType();
                }
                if (attribute.getJavaType() == null) {
                    attribute.inferJavaType();
                }
            }
        }
    }

    /**
     * Synchronisiert Association-Metadaten aus den kanonischen Relationships.
     */
    void synchronizeAssociations(ModelMetadata metadata) {
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            if (classMetadata.getKind() != ClassMetadata.ClassKind.ASSOCIATION) {
                continue;
            }
            AssociationMetadata association = metadata.getAssociation(classMetadata.getName());
            if (association == null) {
                association = new AssociationMetadata(classMetadata.getName());
            }
            association.setAssociationClass(classMetadata.getName());
            association.setPhysicalTable(classMetadata.getTableName());
            association.setPhysicalSqlName(classMetadata.getSqlName());
            metadata.addAssociation(association);
        }

        for (AssociationMetadata association : metadata.getAllAssociations()) {
            ClassMetadata associationClass = metadata.getClass(association.getAssociationClass());
            if (associationClass == null) {
                associationClass = metadata.getClass(association.getName());
            }
            if (associationClass != null) {
                association.setAssociationClass(associationClass.getName());
                association.setPhysicalTable(associationClass.getTableName());
                association.setPhysicalSqlName(associationClass.getSqlName());
            }

            List<RelationshipMetadata> roleRelationships = metadata.getAllRelationships().stream()
                .filter(relationship -> "ili2db+ili2c".equals(relationship.getSource()))
                .filter(relationship -> relationship.getSemanticKind()
                    == RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
                .filter(relationship -> association.getName()
                    .equals(resolveAssociationName(metadata, relationship)))
                .sorted(relationshipOrder())
                .toList();
            if (!roleRelationships.isEmpty()) {
                association.setRoles(roleRelationships.stream()
                    .map(this::toAssociationRole)
                    .toList());
            }

            if (associationClass != null) {
                association.setAttributes(new LinkedHashMap<>());
                for (AttributeMetadata attribute : associationClass.getAllAttributes()) {
                    if (!isAssociationRoleAttribute(attribute, roleRelationships)) {
                        association.addAttribute(attribute);
                    }
                }
            }
        }
    }

    private Comparator<RelationshipMetadata> relationshipOrder() {
        return Comparator
            .comparing(RelationshipMetadata::getTargetRoleName,
                Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMetadata::getSourceAttribute,
                Comparator.nullsLast(String::compareTo));
    }

    /**
     * Löst den Association-Namen eines Relationships auf: expliziter
     * Association-Name, sonst der Name der Source-Klasse, sofern diese eine
     * Association ist, sonst der Source-Klassenname.
     */
    public String resolveAssociationName(ModelMetadata metadata,
                                         RelationshipMetadata relationship) {
        if (relationship.getAssociationName() != null
            && !relationship.getAssociationName().isBlank()) {
            return relationship.getAssociationName();
        }
        ClassMetadata sourceClass = metadata.getClass(relationship.getSourceClass());
        if (sourceClass != null && sourceClass.getKind() == ClassMetadata.ClassKind.ASSOCIATION) {
            return sourceClass.getName();
        }
        return relationship.getSourceClass();
    }

    AssociationRoleMetadata toAssociationRole(RelationshipMetadata relationship) {
        String roleName = relationship.getTargetRoleName();
        if (roleName == null || roleName.isBlank()) {
            roleName = relationship.getSourceAttribute();
        }
        if (roleName == null || roleName.isBlank()) {
            roleName = relationship.getName();
        }
        AssociationRoleMetadata role = new AssociationRoleMetadata(roleName);
        role.setTargetClass(relationship.getTargetClass());
        role.setOppositeRoleName(relationship.getOppositeRoleName());
        role.setCardinality(relationship.getCardinality());
        role.setMandatory(relationship.isMandatory());
        role.setOrdered(relationship.isOrdered());
        role.setExternal(relationship.isExternal());
        role.setComposition(relationship.isComposition());
        role.setSourceAttribute(relationship.getSourceAttribute());
        role.setTargetAttribute(relationship.getTargetAttribute());
        role.setPhysicalName(relationship.getPhysicalName());
        role.setSemanticName(relationship.getSemanticName());
        role.setSource(relationship.getSource());
        role.setMergeReason(relationship.getMergeReason());
        role.setMergeConfidence(relationship.getMergeConfidence());
        role.setMergeToken(relationship.getMergeToken());
        return role;
    }

    boolean isAssociationRoleAttribute(AttributeMetadata attribute,
                                       List<RelationshipMetadata> roleRelationships) {
        if (attribute.isPrimaryKey() || attribute.isForeignKey()) {
            return true;
        }
        for (RelationshipMetadata relationship : roleRelationships) {
            if (equalsAny(attribute.getName(),
                relationship.getSourceAttribute(), relationship.getPhysicalName())
                || equalsAny(attribute.getColumnName(),
                relationship.getSourceAttribute(), relationship.getPhysicalName())
                || equalsAny(attribute.getSqlName(),
                relationship.getSourceAttribute(), relationship.getPhysicalName())) {
                return true;
            }
        }
        return false;
    }

    private boolean equalsAny(String value, String... candidates) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }
}

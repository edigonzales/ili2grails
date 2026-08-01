package ch.interlis.generator.reader.ili2db.assemble;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.builder.AssociationMetadataBuilder;
import ch.interlis.generator.model.builder.AssociationRoleMetadataBuilder;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.model.builder.RelationshipMetadataBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Bereitet Association-Klassen als eigene Core-IR vor: Association-Builder
 * mit Rollen aus den Relationship-Buildern und Rest-Attributen. Reine
 * IR-Transformation auf dem Builder; keine SQL-Zugriffe.
 */
public final class Ili2dbAssociationDeriver {

    public void derive(ModelMetadataBuilder builder) {
        for (ClassMetadataBuilder classMetadata : builder.classBuilders().values()) {
            if (classMetadata.kind() != ClassMetadata.ClassKind.ASSOCIATION) {
                continue;
            }

            AssociationMetadataBuilder association =
                builder.associationBuilder(classMetadata.name());
            association.associationClass(classMetadata.name());
            association.physicalTable(classMetadata.tableName());
            association.physicalSqlName(classMetadata.sqlName());

            List<RelationshipMetadataBuilder> associationRelationships =
                builder.relationshipBuilders().stream()
                    .filter(relationship -> Objects.equals(relationship.sourceClass(), classMetadata.name()))
                    .toList();
            for (RelationshipMetadataBuilder relationship : associationRelationships) {
                association.role(toAssociationRole(relationship));
            }
            for (AttributeMetadataBuilder attribute : classMetadata.attributeBuilders().values()) {
                if (!isAssociationRoleAttribute(attribute, associationRelationships)) {
                    association.attribute(attribute);
                }
            }
        }
    }

    private AssociationRoleMetadataBuilder toAssociationRole(
        RelationshipMetadataBuilder relationship) {
        String roleName = relationship.targetRoleName() != null
            ? relationship.targetRoleName()
            : relationship.sourceAttribute();
        if (roleName == null || roleName.isBlank()) {
            roleName = relationship.name();
        }
        AssociationRoleMetadataBuilder role = new AssociationRoleMetadataBuilder(roleName);
        role.targetClass(relationship.targetClass());
        role.oppositeRoleName(relationship.oppositeRoleName());
        role.cardinality(relationship.cardinality());
        role.mandatory(relationship.mandatory());
        role.ordered(relationship.ordered());
        role.external(relationship.external());
        role.composition(relationship.composition());
        role.sourceAttribute(relationship.sourceAttribute());
        role.targetAttribute(relationship.targetAttribute());
        role.physicalName(relationship.physicalName());
        role.semanticName(relationship.semanticName());
        role.source(relationship.source());
        role.mergeReason(relationship.mergeReason());
        role.mergeConfidence(relationship.mergeConfidence());
        role.mergeToken(relationship.mergeToken());
        return role;
    }

    private boolean isAssociationRoleAttribute(
        AttributeMetadataBuilder attribute,
        List<RelationshipMetadataBuilder> relationships) {
        if (attribute.primaryKey() || attribute.foreignKey()) {
            return true;
        }
        for (RelationshipMetadataBuilder relationship : relationships) {
            if (equalsAny(attribute.name(), relationship.sourceAttribute(), relationship.physicalName())
                || equalsAny(attribute.columnName(), relationship.sourceAttribute(), relationship.physicalName())
                || equalsAny(attribute.sqlName(), relationship.sourceAttribute(), relationship.physicalName())) {
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

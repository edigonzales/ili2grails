package ch.interlis.generator.metadata;

import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.AssociationMetadataBuilder;
import ch.interlis.generator.model.builder.AssociationRoleMetadataBuilder;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.model.builder.RelationshipMetadataBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Nachbearbeitung des gemergten Metamodells auf dem Build-Model:
 * konsistente Association-Synchronisierung.
 *
 * <p>Die Typ-Inferenz übernimmt die {@code ModelMetadataFactory} vor dem
 * Freeze; die Association-Synchronisierung verwendet ausschliesslich
 * kanonische (gemergte, eindeutige) Relationships. Aus einem ambiguous
 * Relationship wird keine Association-Rolle abgeleitet. Rollen werden
 * deterministisch sortiert.</p>
 */
public final class MetadataPostProcessor {

    /**
     * Führt die Nachbearbeitung auf dem Build-Model aus.
     */
    public void process(ModelMetadataBuilder builder) {
        Objects.requireNonNull(builder, "builder");
        synchronizeAssociations(builder);
    }

    /**
     * Synchronisiert Association-Metadaten aus den kanonischen Relationships.
     */
    void synchronizeAssociations(ModelMetadataBuilder builder) {
        for (ClassMetadataBuilder classBuilder : builder.classBuilders().values()) {
            if (classBuilder.kind() != ClassMetadata.ClassKind.ASSOCIATION) {
                continue;
            }
            AssociationMetadataBuilder association = builder.findAssociationBuilder(classBuilder.name())
                .orElseGet(() -> builder.associationBuilder(classBuilder.name()));
            association.associationClass(classBuilder.name());
            association.physicalTable(classBuilder.tableName());
            association.physicalSqlName(classBuilder.sqlName());
        }

        for (AssociationMetadataBuilder association : builder.associationBuilders().values()) {
            ClassMetadataBuilder associationClass = builder.requireClassBuilder(
                association.associationClass() != null
                    ? association.associationClass()
                    : association.name());
            if (associationClass != null) {
                association.associationClass(associationClass.name());
                association.physicalTable(associationClass.tableName());
                association.physicalSqlName(associationClass.sqlName());
            }

            List<RelationshipMetadataBuilder> roleRelationships = builder.relationshipBuilders().stream()
                .filter(relationship -> "ili2db+ili2c".equals(relationship.source()))
                .filter(relationship -> relationship.semanticKind()
                    == RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
                .filter(relationship -> association.name()
                    .equals(resolveAssociationName(builder, relationship)))
                .sorted(relationshipOrder())
                .toList();
            if (!roleRelationships.isEmpty()) {
                association.replaceRoles(roleRelationships.stream()
                    .map(this::toAssociationRole)
                    .toList());
            }

            if (associationClass != null) {
                association.replaceAttributes(new java.util.LinkedHashMap<>());
                for (AttributeMetadataBuilder attribute : associationClass.attributeBuilders().values()) {
                    if (!isAssociationRoleAttribute(attribute, roleRelationships)) {
                        association.attribute(attribute);
                    }
                }
            }
        }
    }

    private Comparator<RelationshipMetadataBuilder> relationshipOrder() {
        return Comparator
            .comparing((RelationshipMetadataBuilder relationship) -> relationship.targetRoleName(),
                Comparator.nullsLast(String::compareTo))
            .thenComparing((RelationshipMetadataBuilder relationship) -> relationship.sourceAttribute(),
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

    private String resolveAssociationName(ModelMetadataBuilder builder,
                                          RelationshipMetadataBuilder relationship) {
        if (relationship.associationName() != null && !relationship.associationName().isBlank()) {
            return relationship.associationName();
        }
        ClassMetadataBuilder sourceClass = builder.findClassBuilder(relationship.sourceClass()).orElse(null);
        if (sourceClass != null && sourceClass.kind() == ClassMetadata.ClassKind.ASSOCIATION) {
            return sourceClass.name();
        }
        return relationship.sourceClass();
    }

    AssociationRoleMetadataBuilder toAssociationRole(RelationshipMetadataBuilder relationship) {
        String roleName = relationship.targetRoleName();
        if (roleName == null || roleName.isBlank()) {
            roleName = relationship.sourceAttribute();
        }
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

    boolean isAssociationRoleAttribute(AttributeMetadataBuilder attribute,
                                       List<RelationshipMetadataBuilder> roleRelationships) {
        if (attribute.primaryKey() || attribute.foreignKey()) {
            return true;
        }
        for (RelationshipMetadataBuilder relationship : roleRelationships) {
            if (equalsAny(attribute.name(),
                relationship.sourceAttribute(), relationship.physicalName())
                || equalsAny(attribute.columnName(),
                relationship.sourceAttribute(), relationship.physicalName())
                || equalsAny(attribute.sqlName(),
                relationship.sourceAttribute(), relationship.physicalName())) {
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

package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps core IR relationships into Grails/GORM domain decisions.
 */
public final class GrailsRelationshipMapper {

    private final ModelMetadata metadata;
    private final TargetNameRegistry registry;
    private final Set<String> generatedClassNames;
    private final Map<String, List<RelationshipMetadata>> relationshipsBySource;
    private final Map<String, List<RelationshipMetadata>> relationshipsByTarget;

    private GrailsRelationshipMapper(ModelMetadata metadata, TargetNameRegistry registry) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.generatedClassNames = resolveGeneratedClassNames();
        this.relationshipsBySource = indexRelationships(RelationshipMetadata::getSourceClass);
        this.relationshipsByTarget = indexRelationships(RelationshipMetadata::getTargetClass);
    }

    public static GrailsRelationshipMapper forMetadata(ModelMetadata metadata,
                                                       GenerationConfig config,
                                                       TargetNameRegistry registry) {
        Objects.requireNonNull(config, "config");
        return new GrailsRelationshipMapper(metadata, registry);
    }

    public boolean shouldGenerate(ClassMetadata classMetadata) {
        return classMetadata != null && generatedClassNames.contains(classMetadata.getName());
    }

    public boolean shouldGenerateClass(ClassMetadata classMetadata) {
        return shouldGenerate(classMetadata);
    }

    public List<ClassMetadata> generatedClasses() {
        return metadata.getAllClasses().stream()
            .filter(this::shouldGenerate)
            .toList();
    }

    public DomainMapping map(ClassMetadata classMetadata) {
        List<DomainProperty> properties = new ArrayList<>();
        List<DomainCollection> collections = new ArrayList<>();
        List<DomainOwnership> belongsTo = new ArrayList<>();
        Set<String> usedProperties = new LinkedHashSet<>();
        Set<String> representedRelationships = new LinkedHashSet<>();

        for (AttributeMetadata attribute : classMetadata.getAllAttributes()) {
            if (attribute.isPrimaryKey()) {
                continue;
            }
            RelationshipMetadata relationship = relationshipForAttribute(classMetadata, attribute);
            if (isToManyComposition(relationship) && isGenerated(relationship.getTargetClass())) {
                collections.add(new DomainCollection(
                    uniquePropertyName(registry.collectionPropertyName(relationship), usedProperties),
                    registry.className(relationship.getTargetClass()),
                    relationship
                ));
                representedRelationships.add(relationshipKey(relationship));
                continue;
            }

            DomainProperty property = propertyForAttribute(classMetadata, attribute, relationship, usedProperties);
            properties.add(property);
            if (relationship != null) {
                representedRelationships.add(relationshipKey(relationship));
                if (isBelongsToComposition(relationship)) {
                    belongsTo.add(new DomainOwnership(property.name(), property.type(), relationship));
                }
            }
        }

        for (RelationshipMetadata relationship : relationshipsBySource.getOrDefault(classMetadata.getName(), List.of())) {
            if (representedRelationships.contains(relationshipKey(relationship))
                || !isGenerated(relationship.getTargetClass())) {
                continue;
            }
            if (relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE) {
                properties.add(propertyForRelationship(relationship, usedProperties));
            } else if (isToManyComposition(relationship)) {
                collections.add(new DomainCollection(
                    uniquePropertyName(registry.collectionPropertyName(relationship), usedProperties),
                    registry.className(relationship.getTargetClass()),
                    relationship
                ));
            } else if (relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
                || relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE) {
                properties.add(propertyForRelationship(relationship, usedProperties));
            }
        }

        for (RelationshipMetadata relationship : relationshipsByTarget.getOrDefault(classMetadata.getName(), List.of())) {
            if (relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
                || relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
                || !isGenerated(relationship.getSourceClass())
                || relationship.getType() != RelationshipMetadata.RelationType.MANY_TO_ONE) {
                continue;
            }
            collections.add(new DomainCollection(
                uniquePropertyName(registry.collectionPropertyName(relationship), usedProperties),
                registry.className(relationship.getSourceClass()),
                relationship
            ));
        }

        return new DomainMapping(classMetadata, properties, collections, belongsTo);
    }

    private DomainProperty propertyForAttribute(ClassMetadata owner,
                                                AttributeMetadata attribute,
                                                RelationshipMetadata relationship,
                                                Set<String> usedProperties) {
        String name = uniquePropertyName(registry.propertyName(owner, attribute), usedProperties);
        String type = resolvePropertyType(attribute, relationship);
        return new DomainProperty(
            name,
            type,
            attribute,
            relationship,
            attribute.getColumnName(),
            isNullable(attribute, relationship),
            attribute.getMaxLength(),
            attribute.getMinValue(),
            attribute.getMaxValue(),
            attribute.isGeometry(),
            attribute.getGeometrySrid(),
            attribute.getGeometryKind()
        );
    }

    private DomainProperty propertyForRelationship(RelationshipMetadata relationship,
                                                   Set<String> usedProperties) {
        return new DomainProperty(
            uniquePropertyName(registry.relationshipPropertyName(relationship), usedProperties),
            registry.className(relationship.getTargetClass()),
            null,
            relationship,
            null,
            !relationship.isMandatory(),
            null,
            null,
            null,
            false,
            null,
            null
        );
    }

    private String resolvePropertyType(AttributeMetadata attribute, RelationshipMetadata relationship) {
        if (attribute.getEnumType() != null) {
            var enumMetadata = metadata.getEnums().get(attribute.getEnumType());
            if (enumMetadata != null) {
                return registry.enumName(enumMetadata);
            }
        }
        if (relationship != null && isGenerated(relationship.getTargetClass())) {
            return registry.className(relationship.getTargetClass());
        }
        if (attribute.isForeignKey()
            && attribute.getReferencedClass() != null
            && isGenerated(attribute.getReferencedClass())) {
            return registry.className(attribute.getReferencedClass());
        }
        return NameUtils.simpleType(attribute.getJavaType());
    }

    private RelationshipMetadata relationshipForAttribute(ClassMetadata classMetadata,
                                                          AttributeMetadata attribute) {
        return relationshipsBySource.getOrDefault(classMetadata.getName(), List.of()).stream()
            .filter(relationship -> matchesAttribute(relationship, attribute))
            .min(relationshipPreference())
            .orElse(null);
    }

    private boolean matchesAttribute(RelationshipMetadata relationship, AttributeMetadata attribute) {
        return equalsAny(relationship.getSourceAttribute(), attribute.getName(), attribute.getSqlName(), attribute.getColumnName())
            || equalsAny(relationship.getTargetRoleName(), attribute.getName(), attribute.getSqlName(), attribute.getColumnName());
    }

    private Comparator<RelationshipMetadata> relationshipPreference() {
        return Comparator
            .comparingInt(this::semanticRank)
            .thenComparing(RelationshipMetadata::getName, Comparator.nullsLast(String::compareTo));
    }

    private int semanticRank(RelationshipMetadata relationship) {
        RelationshipMetadata.SemanticKind kind = relationship.getSemanticKind();
        if (kind == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE) {
            return 0;
        }
        if (kind == RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE) {
            return 1;
        }
        if (kind == RelationshipMetadata.SemanticKind.ILI2DB_FK) {
            return 2;
        }
        if (kind == RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE) {
            return 3;
        }
        return 4;
    }

    private boolean isToManyComposition(RelationshipMetadata relationship) {
        if (relationship == null
            || relationship.getSemanticKind() != RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE) {
            return false;
        }
        RelationshipMetadata.Cardinality cardinality = relationship.getCardinality();
        if (cardinality == null) {
            return relationship.getType() == RelationshipMetadata.RelationType.ONE_TO_MANY
                || relationship.getType() == RelationshipMetadata.RelationType.MANY_TO_MANY;
        }
        int maxTarget = cardinality.getMaxTarget();
        return maxTarget == -1 || maxTarget > 1;
    }

    private boolean isBelongsToComposition(RelationshipMetadata relationship) {
        return relationship != null
            && relationship.isComposition()
            && relationship.getSemanticKind() != RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
            && relationship.getType() == RelationshipMetadata.RelationType.MANY_TO_ONE;
    }

    private boolean isNullable(AttributeMetadata attribute, RelationshipMetadata relationship) {
        if (attribute.isMandatory()) {
            return false;
        }
        return relationship == null || !relationship.isMandatory();
    }

    private Set<String> resolveGeneratedClassNames() {
        Set<String> compositionTargets = metadata.getAllRelationships().stream()
            .filter(relationship -> relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE)
            .map(RelationshipMetadata::getTargetClass)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Set<String> generated = new LinkedHashSet<>();
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            if (classMetadata.isAbstract()) {
                continue;
            }
            if (classMetadata.getKind() == ClassMetadata.ClassKind.STRUCTURE
                && !hasPhysicalMapping(classMetadata)
                && !compositionTargets.contains(classMetadata.getName())) {
                continue;
            }
            generated.add(classMetadata.getName());
        }
        return generated;
    }

    private boolean hasPhysicalMapping(ClassMetadata classMetadata) {
        return (classMetadata.getTableName() != null && !classMetadata.getTableName().isBlank())
            || (classMetadata.getSqlName() != null && !classMetadata.getSqlName().isBlank());
    }

    private boolean isGenerated(String className) {
        return className != null && generatedClassNames.contains(className);
    }

    private Map<String, List<RelationshipMetadata>> indexRelationships(RelationshipClassSelector selector) {
        Map<String, List<RelationshipMetadata>> indexed = new LinkedHashMap<>();
        for (RelationshipMetadata relationship : sorted(metadata.getAllRelationships())) {
            String className = selector.className(relationship);
            if (className == null) {
                continue;
            }
            indexed.computeIfAbsent(className, key -> new ArrayList<>()).add(relationship);
        }
        return indexed;
    }

    private List<RelationshipMetadata> sorted(Collection<RelationshipMetadata> relationships) {
        return relationships.stream()
            .sorted(Comparator
                .comparing(RelationshipMetadata::getSourceClass, Comparator.nullsLast(String::compareTo))
                .thenComparing(RelationshipMetadata::getTargetClass, Comparator.nullsLast(String::compareTo))
                .thenComparing(RelationshipMetadata::getTargetRoleName, Comparator.nullsLast(String::compareTo))
                .thenComparing(RelationshipMetadata::getName, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    private String uniquePropertyName(String baseName, Set<String> usedProperties) {
        String candidate = baseName == null || baseName.isBlank() ? "value" : baseName;
        if (usedProperties.add(candidate)) {
            return candidate;
        }
        int suffix = 1;
        String suffixed;
        do {
            suffixed = candidate + suffix++;
        } while (!usedProperties.add(suffixed));
        return suffixed;
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

    private String relationshipKey(RelationshipMetadata relationship) {
        return String.join("|",
            nullToEmpty(relationship.getName()),
            nullToEmpty(relationship.getSourceClass()),
            nullToEmpty(relationship.getTargetClass()),
            nullToEmpty(relationship.getSourceAttribute()),
            nullToEmpty(relationship.getTargetRoleName()),
            relationship.getSemanticKind() != null ? relationship.getSemanticKind().name() : ""
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface RelationshipClassSelector {
        String className(RelationshipMetadata relationship);
    }

    public record DomainMapping(
        ClassMetadata classMetadata,
        List<DomainProperty> properties,
        List<DomainCollection> collections,
        List<DomainOwnership> belongsTo
    ) {
    }

    public record DomainProperty(
        String name,
        String type,
        AttributeMetadata attribute,
        RelationshipMetadata relationship,
        String columnName,
        boolean nullable,
        Integer maxLength,
        String minValue,
        String maxValue,
        boolean geometry,
        Integer geometrySrid,
        String geometryKind
    ) {
    }

    public record DomainCollection(
        String name,
        String type,
        RelationshipMetadata relationship
    ) {
    }

    public record DomainOwnership(
        String name,
        String type,
        RelationshipMetadata relationship
    ) {
    }
}

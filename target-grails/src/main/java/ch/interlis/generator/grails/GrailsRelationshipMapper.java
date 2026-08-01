package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.AttributeConstraints;
import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
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
 * Plant ausschliesslich die GORM-Persistenzabbildung (Properties, echte
 * Kompositions-Collections, belongsTo, mappedBy).
 *
 * <p>Normale eingehende {@code MANY_TO_ONE}-Relationships erzeugen auf der
 * Zielklasse bewusst keine GORM-Collection (kein {@code static hasMany}).
 * Inverse/Navigationsbeziehungen plant ausschliesslich der
 * {@link GrailsInverseRelationshipPlanner} query-basiert.</p>
 *
 * <p>Eine to-many-Komposition wird nur dann persistent ({@code hasMany} +
 * {@code mappedBy}), wenn die physische Abbildung eindeutig belegt ist:
 * genau ein Child-Property zeigt auf den Owner. Bei Unsicherheit wird
 * fail-closed entschieden: keine Collection, stattdessen ein
 * {@link PersistenceDiagnostic}.</p>
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

    /**
     * Eingehende Relationships einer Zielklasse (Zielklasse == targetClass).
     */
    public List<RelationshipMetadata> incomingRelationships(String targetClassName) {
        if (targetClassName == null) {
            return List.of();
        }
        return List.copyOf(relationshipsByTarget.getOrDefault(targetClassName, List.of()));
    }

    /**
     * Ausgehende Relationships einer Source-Klasse.
     */
    public List<RelationshipMetadata> outgoingRelationships(String sourceClassName) {
        if (sourceClassName == null) {
            return List.of();
        }
        return List.copyOf(relationshipsBySource.getOrDefault(sourceClassName, List.of()));
    }

    /**
     * Löst die persistente Property einer Relationship auf der Source-Klasse auf.
     * Es wird nie der erste Kandidat gewählt: 0 Kandidaten sind {@code NOT_FOUND},
     * mehrere sind {@code AMBIGUOUS}.
     */
    public PropertyResolution resolvePropertyForRelationship(ClassMetadata sourceClass,
                                                             RelationshipMetadata relationship) {
        Objects.requireNonNull(sourceClass, "sourceClass");
        Objects.requireNonNull(relationship, "relationship");
        List<DomainProperty> matches = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (AttributeMetadata attribute : sourceClass.getAllAttributes()) {
            if (attribute.isPrimaryKey()) {
                continue;
            }
            RelationshipMetadata attributeRelationship =
                relationshipForAttribute(sourceClass, attribute);
            if (attributeRelationship != null && sameRelationship(attributeRelationship, relationship)) {
                matches.add(propertyForAttribute(sourceClass, attribute, attributeRelationship, used));
            }
        }
        if (matches.size() == 1) {
            return new PropertyResolution(PropertyResolution.Status.RESOLVED, matches.get(0), List.of());
        }
        if (matches.isEmpty()) {
            return new PropertyResolution(PropertyResolution.Status.NOT_FOUND, null, List.of());
        }
        return new PropertyResolution(PropertyResolution.Status.AMBIGUOUS, null, matches);
    }

    public DomainMapping map(ClassMetadata classMetadata) {
        List<DomainProperty> properties = new ArrayList<>();
        List<PersistentCollection> collections = new ArrayList<>();
        List<DomainOwnership> belongsTo = new ArrayList<>();
        List<PersistenceDiagnostic> diagnostics = new ArrayList<>();
        Set<String> usedProperties = new LinkedHashSet<>();
        Set<String> representedRelationships = new LinkedHashSet<>();

        for (AttributeMetadata attribute : classMetadata.getAllAttributes()) {
            if (attribute.isPrimaryKey()) {
                continue;
            }
            RelationshipMetadata relationship = relationshipForAttribute(classMetadata, attribute);
            if (isToManyComposition(relationship)) {
                PersistentCollection collection = resolveCompositionCollection(
                    classMetadata, relationship, usedProperties, diagnostics);
                if (collection != null) {
                    collections.add(collection);
                    representedRelationships.add(relationshipKey(relationship));
                }
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
                PersistentCollection collection = resolveCompositionCollection(
                    classMetadata, relationship, usedProperties, diagnostics);
                if (collection != null) {
                    collections.add(collection);
                }
            } else if (relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
                || relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE) {
                properties.add(propertyForRelationship(relationship, usedProperties));
            }
        }

        return new DomainMapping(classMetadata, properties, collections, belongsTo, diagnostics);
    }

    /**
     * Löst eine to-many-Kompositions-Collection mit eindeutigem Child-FK auf.
     * Fail-closed: ohne eindeutige physische Abbildung entsteht keine Collection.
     */
    private PersistentCollection resolveCompositionCollection(ClassMetadata ownerClass,
                                                              RelationshipMetadata relationship,
                                                              Set<String> usedProperties,
                                                              List<PersistenceDiagnostic> diagnostics) {
        if (!isToManyComposition(relationship)) {
            return null;
        }
        String childClassName = relationship.getTargetClass();
        if (childClassName == null || !isGenerated(childClassName)) {
            return null;
        }
        ClassMetadata childClass = metadata.getClass(childClassName);
        if (childClass == null) {
            return null;
        }

        List<DomainProperty> candidateProperties = new ArrayList<>();
        for (RelationshipMetadata fk : outgoingRelationships(childClassName)) {
            if (!Objects.equals(fk.getTargetClass(), ownerClass.getName())
                || fk.getType() != RelationshipMetadata.RelationType.MANY_TO_ONE
                || fk.isExternal()
                || (fk.getSemanticKind() != RelationshipMetadata.SemanticKind.ILI2DB_FK
                && fk.getSemanticKind() != RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE)) {
                continue;
            }
            PropertyResolution resolution = resolvePropertyForRelationship(childClass, fk);
            if (resolution.status() == PropertyResolution.Status.RESOLVED) {
                candidateProperties.add(resolution.property());
            } else if (resolution.status() == PropertyResolution.Status.AMBIGUOUS) {
                diagnostics.add(new PersistenceDiagnostic(
                    PersistenceDiagnostic.Severity.ERROR,
                    PersistenceDiagnostic.Code.RELATIONSHIP_PROPERTY_AMBIGUOUS,
                    ownerClass.getName(),
                    relationship.getName(),
                    "composition child property for relationship '" + fk.getName()
                        + "' is ambiguous"
                ));
            }
        }

        if (candidateProperties.size() == 1) {
            DomainProperty mappedBy = candidateProperties.get(0);
            if (mappedBy.columnName() == null || mappedBy.columnName().isBlank()) {
                diagnostics.add(new PersistenceDiagnostic(
                    PersistenceDiagnostic.Severity.WARNING,
                    PersistenceDiagnostic.Code.COMPOSITION_COLLECTION_UNRESOLVED,
                    ownerClass.getName(),
                    relationship.getName(),
                    "composition collection has no physical child FK column"
                ));
                return null;
            }
            return new PersistentCollection(
                uniquePropertyName(registry.collectionPropertyName(relationship), usedProperties),
                registry.className(childClassName),
                mappedBy.name(),
                CollectionKind.COMPOSITION,
                relationship
            );
        }

        if (candidateProperties.isEmpty()) {
            diagnostics.add(new PersistenceDiagnostic(
                PersistenceDiagnostic.Severity.WARNING,
                PersistenceDiagnostic.Code.COMPOSITION_COLLECTION_UNRESOLVED,
                ownerClass.getName(),
                relationship.getName(),
                "composition collection has no resolvable child FK property"
            ));
        } else {
            diagnostics.add(new PersistenceDiagnostic(
                PersistenceDiagnostic.Severity.ERROR,
                PersistenceDiagnostic.Code.COMPOSITION_MAPPED_BY_AMBIGUOUS,
                ownerClass.getName(),
                relationship.getName(),
                "composition collection has multiple child FK properties; "
                    + "mappedBy is not guessed"
            ));
        }
        return null;
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
            attribute.getConstraints(),
            attribute.isGeometry(),
            attribute.getGeometrySrid(),
            attribute.getGeometryKind(),
            attribute.getGeometryHasZ(),
            attribute.getGeometryHasM(),
            attribute.getAllowEmptyGeometry()
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
            constraintsForRelationship(relationship),
            false,
            null,
            null,
            null,
            null,
            null
        );
    }

    private AttributeConstraints constraintsForRelationship(RelationshipMetadata relationship) {
        RelationshipMetadata.Cardinality cardinality = relationship.getCardinality();
        return new AttributeConstraints(
            relationship.isMandatory(),
            null,
            null,
            null,
            null,
            null,
            cardinality != null ? cardinality.getMinTarget() : null,
            cardinality != null ? cardinality.getMaxTarget() : null,
            relationship.isOrdered()
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
            || equalsAny(relationship.getPhysicalName(), attribute.getName(), attribute.getSqlName(), attribute.getColumnName())
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
            if (classMetadata.getKind() == ClassMetadata.ClassKind.ASSOCIATION
                && !hasPhysicalMapping(classMetadata)) {
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
        for (RelationshipMetadata relationship : effectiveRelationships()) {
            String className = selector.className(relationship);
            if (className == null) {
                continue;
            }
            indexed.computeIfAbsent(className, key -> new ArrayList<>()).add(relationship);
        }
        return indexed;
    }

    private List<RelationshipMetadata> effectiveRelationships() {
        List<RelationshipMetadata> relationships = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        metadata.getAllAssociations().stream()
            .sorted(Comparator.comparing(AssociationMetadata::getName, Comparator.nullsLast(String::compareTo)))
            .forEach(association -> association.getRoles().stream()
                .sorted(Comparator
                    .comparing(AssociationRoleMetadata::getName, Comparator.nullsLast(String::compareTo))
                    .thenComparing(AssociationRoleMetadata::getTargetClass, Comparator.nullsLast(String::compareTo)))
                .map(role -> relationshipFromAssociationRole(association, role))
                .forEach(relationship -> {
                    if (seen.add(relationshipIdentityKey(relationship))) {
                        relationships.add(relationship);
                    }
                }));

        for (RelationshipMetadata relationship : sorted(metadata.getAllRelationships())) {
            if (seen.add(relationshipIdentityKey(relationship))) {
                relationships.add(relationship);
            }
        }
        return relationships;
    }

    private RelationshipMetadata relationshipFromAssociationRole(AssociationMetadata association,
                                                                 AssociationRoleMetadata role) {
        RelationshipMetadata relationship = new RelationshipMetadata(
            association.getName() + "." + role.getName()
        );
        relationship.setSourceClass(association.getAssociationClass() != null
            ? association.getAssociationClass()
            : association.getName());
        relationship.setTargetClass(role.getTargetClass());
        relationship.setType(RelationshipMetadata.RelationType.ASSOCIATION);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
        relationship.setAssociationName(association.getName());
        relationship.setSourceRoleName(role.getOppositeRoleName());
        relationship.setTargetRoleName(role.getName());
        relationship.setOppositeRoleName(role.getOppositeRoleName());
        relationship.setCardinality(role.getCardinality());
        relationship.setMandatory(role.isMandatory());
        relationship.setOrdered(role.isOrdered());
        relationship.setExternal(role.isExternal());
        relationship.setComposition(role.isComposition());
        relationship.setSourceAttribute(role.getSourceAttribute());
        relationship.setTargetAttribute(role.getTargetAttribute());
        relationship.setSource(role.getSource());
        relationship.setPhysicalName(role.getPhysicalName());
        relationship.setSemanticName(role.getSemanticName());
        relationship.setMergeReason(role.getMergeReason());
        relationship.setMergeConfidence(role.getMergeConfidence());
        relationship.setMergeToken(role.getMergeToken());
        return relationship;
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

    private String relationshipIdentityKey(RelationshipMetadata relationship) {
        return String.join("|",
            nullToEmpty(relationship.getSourceClass()),
            nullToEmpty(relationship.getTargetClass()),
            nullToEmpty(relationship.getSourceAttribute()),
            nullToEmpty(relationship.getTargetRoleName()),
            relationship.getSemanticKind() != null ? relationship.getSemanticKind().name() : ""
        );
    }

    private boolean sameRelationship(RelationshipMetadata left, RelationshipMetadata right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getName(), right.getName())
            && Objects.equals(left.getSourceClass(), right.getSourceClass())
            && Objects.equals(left.getTargetClass(), right.getTargetClass())
            && Objects.equals(left.getSourceAttribute(), right.getSourceAttribute())
            && Objects.equals(left.getTargetRoleName(), right.getTargetRoleName())
            && left.getSemanticKind() == right.getSemanticKind();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface RelationshipClassSelector {
        String className(RelationshipMetadata relationship);
    }

    /**
     * Ergebnis der Property-Auflösung für eine Relationship.
     */
    public record PropertyResolution(
        Status status,
        DomainProperty property,
        List<DomainProperty> candidates
    ) {

        public enum Status {
            RESOLVED,
            NOT_FOUND,
            AMBIGUOUS
        }

        public PropertyResolution {
            candidates = candidates == null
                ? List.of()
                : List.copyOf(candidates);
        }
    }

    /**
     * Persistenzplan einer Klasse: Properties, persistente Collections,
     * Ownerships und Persistenz-Diagnostics.
     */
    public record DomainMapping(
        ClassMetadata classMetadata,
        List<DomainProperty> properties,
        List<PersistentCollection> collections,
        List<DomainOwnership> belongsTo,
        List<PersistenceDiagnostic> diagnostics
    ) {

        public DomainMapping {
            properties = List.copyOf(properties);
            collections = List.copyOf(collections);
            belongsTo = List.copyOf(belongsTo);
            diagnostics = List.copyOf(diagnostics);
        }
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
        AttributeConstraints constraints,
        boolean geometry,
        Integer geometrySrid,
        String geometryKind,
        Boolean geometryHasZ,
        Boolean geometryHasM,
        Boolean allowEmptyGeometry
    ) {
    }

    /**
     * Persistente GORM-Collection. Es gibt bewusst keine Navigations- oder
     * Related-Section-Art in diesem Persistenztyp.
     */
    public record PersistentCollection(
        String name,
        String elementType,
        String mappedByProperty,
        CollectionKind kind,
        RelationshipMetadata relationship
    ) {
    }

    /**
     * Art einer persistenten Collection. Aktuell nur echte Komposition.
     */
    public enum CollectionKind {
        COMPOSITION
    }

    public record DomainOwnership(
        String name,
        String type,
        RelationshipMetadata relationship
    ) {
    }
}

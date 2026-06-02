package ch.interlis.generator.django;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps Core-IR classes and relationships into minimal Django model declarations.
 */
final class DjangoModelMapper {

    private final ModelMetadata metadata;
    private final DjangoNameRegistry registry;
    private final Set<String> generatedClassNames;
    private final Map<String, List<RelationshipMetadata>> relationshipsBySource;

    private DjangoModelMapper(ModelMetadata metadata, DjangoNameRegistry registry) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.generatedClassNames = resolveGeneratedClassNames();
        this.relationshipsBySource = indexRelationships(RelationshipMetadata::getSourceClass);
    }

    static DjangoModelMapper forMetadata(ModelMetadata metadata, DjangoNameRegistry registry) {
        return new DjangoModelMapper(metadata, registry);
    }

    List<DjangoModelMapping> mappings() {
        return metadata.getAllClasses().stream()
            .filter(this::shouldGenerate)
            .map(this::map)
            .toList();
    }

    private DjangoModelMapping map(ClassMetadata classMetadata) {
        List<DjangoField> fields = new ArrayList<>();
        Set<String> usedFields = new LinkedHashSet<>();
        Set<String> representedRelationships = new LinkedHashSet<>();

        for (AttributeMetadata attribute : classMetadata.getAllAttributes()) {
            RelationshipMetadata relationship = relationshipForAttribute(classMetadata, attribute);
            if (relationship != null && isToManyComposition(relationship)) {
                fields.add(toManyCompositionField(relationship, usedFields));
                representedRelationships.add(relationshipKey(relationship));
                continue;
            }
            if (attribute.isPrimaryKey()) {
                fields.add(primaryKeyField(attribute, usedFields));
                continue;
            }
            if (relationship != null) {
                fields.add(relationshipField(relationship, usedFields));
                representedRelationships.add(relationshipKey(relationship));
                continue;
            }
            if (attribute.isForeignKey() && hasText(attribute.getReferencedClass())) {
                fields.add(attributeForeignKeyField(attribute, usedFields));
                continue;
            }
            fields.add(scalarField(attribute, usedFields));
        }

        for (RelationshipMetadata relationship : relationshipsBySource.getOrDefault(classMetadata.getName(), List.of())) {
            if (representedRelationships.contains(relationshipKey(relationship))
                || !isGenerated(relationship.getTargetClass())) {
                continue;
            }
            if (isToManyComposition(relationship)) {
                fields.add(toManyCompositionField(relationship, usedFields));
            } else if (relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
                || relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
                || relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE
                || relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.ILI2DB_FK) {
                fields.add(relationshipField(relationship, usedFields));
            }
        }

        return new DjangoModelMapping(
            classMetadata,
            registry.className(classMetadata),
            fields.stream().filter(Objects::nonNull).toList()
        );
    }

    private DjangoField primaryKeyField(AttributeMetadata attribute, Set<String> usedFields) {
        String fieldName = uniqueFieldName(registry.fieldName(attribute), usedFields);
        List<String> args = new ArrayList<>();
        args.add("primary_key=True");
        if (hasText(attribute.getColumnName())) {
            args.add("db_column=\"" + py(attribute.getColumnName()) + "\"");
        }
        return new DjangoField(fieldName, "models.BigAutoField", args, false);
    }

    private DjangoField scalarField(AttributeMetadata attribute, Set<String> usedFields) {
        String fieldName = uniqueFieldName(registry.fieldName(attribute), usedFields);
        FieldType fieldType = fieldType(attribute);
        List<String> args = new ArrayList<>();
        addMaxLength(attribute, fieldType, args);
        addEnumChoices(attribute, args);
        addDbColumn(attribute, fieldName, args);
        addNullBlank(attribute.isMandatory(), args);
        if (attribute.isGeometry() && attribute.getGeometrySrid() != null) {
            args.add("srid=" + attribute.getGeometrySrid());
        }
        return new DjangoField(fieldName, fieldType.constructor(), args, fieldType.usesGeoDjango());
    }

    private DjangoField attributeForeignKeyField(AttributeMetadata attribute, Set<String> usedFields) {
        String fieldName = uniqueFieldName(registry.fieldName(attribute), usedFields);
        List<String> args = new ArrayList<>();
        args.add("\"" + py(registry.className(attribute.getReferencedClass())) + "\"");
        args.add("on_delete=models.PROTECT");
        addDbColumn(attribute, fieldName, args);
        addNullBlank(attribute.isMandatory(), args);
        args.add("related_name=\"+\"");
        return new DjangoField(fieldName, "models.ForeignKey", args, false);
    }

    private DjangoField relationshipField(RelationshipMetadata relationship, Set<String> usedFields) {
        String fieldName = uniqueFieldName(registry.relationshipFieldName(relationship), usedFields);
        List<String> args = new ArrayList<>();
        args.add("\"" + py(registry.className(relationship.getTargetClass())) + "\"");
        args.add("on_delete=" + onDelete(relationship));
        String dbColumn = physicalColumn(relationship);
        if (hasText(dbColumn)) {
            args.add("db_column=\"" + py(dbColumn) + "\"");
        }
        addNullBlank(relationship.isMandatory(), args);
        args.add("related_name=\"+\"");
        return new DjangoField(fieldName, "models.ForeignKey", args, false);
    }

    private DjangoField toManyCompositionField(RelationshipMetadata relationship, Set<String> usedFields) {
        String fieldName = uniqueFieldName(registry.relationshipFieldName(relationship), usedFields);
        List<String> args = new ArrayList<>();
        args.add("\"" + py(registry.className(relationship.getTargetClass())) + "\"");
        args.add("blank=True");
        args.add("related_name=\"+\"");
        return new DjangoField(fieldName, "models.ManyToManyField", args, false);
    }

    private FieldType fieldType(AttributeMetadata attribute) {
        CoreType coreType = attribute.getCoreType();
        if (coreType != CoreType.UNKNOWN) {
            return fieldTypeFromCoreType(attribute, coreType);
        }
        return fieldTypeFromLegacyHints(attribute);
    }

    private FieldType fieldTypeFromCoreType(AttributeMetadata attribute, CoreType coreType) {
        return switch (coreType) {
            case COORD, POLYLINE, SURFACE -> new FieldType("models.GeometryField", true);
            case ENUM -> new FieldType("models.CharField", false);
            case TEXT -> attribute.getMaxLength() == null
                ? new FieldType("models.TextField", false)
                : new FieldType("models.CharField", false);
            case MTEXT -> new FieldType("models.TextField", false);
            case NUMERIC -> new FieldType("models.DecimalField", false);
            case BOOLEAN -> new FieldType("models.BooleanField", false);
            case DATE -> new FieldType("models.DateField", false);
            case DATETIME -> new FieldType("models.DateTimeField", false);
            case TIME -> new FieldType("models.TimeField", false);
            case REFERENCE, COMPOSITION, OBJECT, UNKNOWN -> new FieldType("models.TextField", false);
        };
    }

    private FieldType fieldTypeFromLegacyHints(AttributeMetadata attribute) {
        if (attribute.isGeometry()) {
            return new FieldType("models.GeometryField", true);
        }
        if (attribute.getEnumType() != null) {
            return new FieldType("models.CharField", false);
        }
        String javaType = attribute.getJavaType();
        if (javaType == null) {
            return hasText(attribute.getMaxValue()) || hasText(attribute.getMinValue())
                ? new FieldType("models.DecimalField", false)
                : new FieldType("models.TextField", false);
        }
        String simpleType = simpleType(javaType);
        return switch (simpleType) {
            case "String" -> attribute.getMaxLength() == null
                ? new FieldType("models.TextField", false)
                : new FieldType("models.CharField", false);
            case "LocalDate" -> new FieldType("models.DateField", false);
            case "LocalDateTime" -> new FieldType("models.DateTimeField", false);
            case "Integer" -> new FieldType("models.IntegerField", false);
            case "Long" -> new FieldType("models.BigIntegerField", false);
            case "BigDecimal" -> new FieldType("models.DecimalField", false);
            case "Double", "Float" -> new FieldType("models.FloatField", false);
            case "Boolean" -> new FieldType("models.BooleanField", false);
            default -> new FieldType("models.TextField", false);
        };
    }

    private void addMaxLength(AttributeMetadata attribute, FieldType fieldType, List<String> args) {
        if ("models.CharField".equals(fieldType.constructor())) {
            int maxLength = attribute.getMaxLength() != null
                ? attribute.getMaxLength()
                : enumMaxLength(attribute);
            args.add("max_length=" + Math.max(maxLength, 1));
        }
        if ("models.DecimalField".equals(fieldType.constructor())) {
            args.add("max_digits=20");
            args.add("decimal_places=6");
        }
    }

    private int enumMaxLength(AttributeMetadata attribute) {
        EnumMetadata enumMetadata = metadata.getEnums().get(attribute.getEnumType());
        if (enumMetadata == null || enumMetadata.getValues().isEmpty()) {
            return 255;
        }
        return enumMetadata.getValues().stream()
            .map(EnumMetadata.EnumValue::getIliCode)
            .filter(Objects::nonNull)
            .mapToInt(String::length)
            .max()
            .orElse(255);
    }

    private void addEnumChoices(AttributeMetadata attribute, List<String> args) {
        if (attribute.getEnumType() == null) {
            return;
        }
        EnumMetadata enumMetadata = metadata.getEnums().get(attribute.getEnumType());
        if (enumMetadata != null) {
            args.add("choices=" + registry.enumChoicesName(enumMetadata) + ".choices");
        }
    }

    private void addDbColumn(AttributeMetadata attribute, String fieldName, List<String> args) {
        if (hasText(attribute.getColumnName()) && !attribute.getColumnName().equals(fieldName)) {
            args.add("db_column=\"" + py(attribute.getColumnName()) + "\"");
        }
    }

    private void addNullBlank(boolean mandatory, List<String> args) {
        if (!mandatory) {
            args.add("null=True");
            args.add("blank=True");
        }
    }

    private String onDelete(RelationshipMetadata relationship) {
        return relationship.isComposition() ? "models.CASCADE" : "models.PROTECT";
    }

    private String physicalColumn(RelationshipMetadata relationship) {
        if (hasText(relationship.getPhysicalName())) {
            return relationship.getPhysicalName();
        }
        if (hasText(relationship.getSource()) && relationship.getSource().contains("ili2db")) {
            return relationship.getSourceAttribute();
        }
        return null;
    }

    private RelationshipMetadata relationshipForAttribute(ClassMetadata classMetadata, AttributeMetadata attribute) {
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
        if (kind == RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE) {
            return 2;
        }
        if (kind == RelationshipMetadata.SemanticKind.ILI2DB_FK) {
            return 3;
        }
        return 4;
    }

    private boolean shouldGenerate(ClassMetadata classMetadata) {
        return classMetadata != null && generatedClassNames.contains(classMetadata.getName());
    }

    private Set<String> resolveGeneratedClassNames() {
        Set<String> compositionTargets = metadata.getAllRelationships().stream()
            .filter(relationship -> relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE)
            .map(RelationshipMetadata::getTargetClass)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

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
        return hasText(classMetadata.getTableName()) || hasText(classMetadata.getSqlName());
    }

    private boolean isGenerated(String className) {
        return className != null && generatedClassNames.contains(className);
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

    private Map<String, List<RelationshipMetadata>> indexRelationships(Function<RelationshipMetadata, String> selector) {
        Map<String, List<RelationshipMetadata>> indexed = new LinkedHashMap<>();
        for (RelationshipMetadata relationship : effectiveRelationships()) {
            String key = selector.apply(relationship);
            if (key != null) {
                indexed.computeIfAbsent(key, ignored -> new ArrayList<>()).add(relationship);
            }
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

        metadata.getAllRelationships().stream()
            .sorted(Comparator.comparing(RelationshipMetadata::getName, Comparator.nullsLast(String::compareTo)))
            .forEach(relationship -> {
                if (seen.add(relationshipIdentityKey(relationship))) {
                    relationships.add(relationship);
                }
            });
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

    private String uniqueFieldName(String preferredName, Set<String> usedFields) {
        String value = preferredName;
        int suffix = 2;
        while (!usedFields.add(value)) {
            value = preferredName + "_" + suffix;
            suffix++;
        }
        return value;
    }

    private boolean equalsAny(String value, String... candidates) {
        if (!hasText(value)) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String simpleType(String type) {
        int idx = type.lastIndexOf('.');
        return idx >= 0 ? type.substring(idx + 1) : type;
    }

    private static String relationshipKey(RelationshipMetadata relationship) {
        return relationship.getName()
            + "|" + relationship.getSourceClass()
            + "|" + relationship.getTargetClass()
            + "|" + relationship.getSourceAttribute()
            + "|" + relationship.getTargetRoleName()
            + "|" + relationship.getSemanticKind();
    }

    private static String relationshipIdentityKey(RelationshipMetadata relationship) {
        return relationship.getSourceClass()
            + "|" + relationship.getTargetClass()
            + "|" + relationship.getSourceAttribute()
            + "|" + relationship.getTargetRoleName()
            + "|" + relationship.getSemanticKind();
    }

    private static String py(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record DjangoModelMapping(ClassMetadata classMetadata, String className, List<DjangoField> fields) {
        boolean usesGeoDjango() {
            return fields.stream().anyMatch(DjangoField::usesGeoDjango);
        }
    }

    record DjangoField(String name, String constructor, List<String> args, boolean usesGeoDjango) {
        String render() {
            return name + " = " + constructor + "(" + String.join(", ", args) + ")";
        }
    }

    private record FieldType(String constructor, boolean usesGeoDjango) {
    }
}

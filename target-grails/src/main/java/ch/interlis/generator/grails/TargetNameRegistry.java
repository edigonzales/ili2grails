package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves stable target names for framework-specific generators without changing the core IR.
 */
public final class TargetNameRegistry {

    private static final Set<String> RESERVED_WORDS = Set.of(
        "abstract", "assert", "as", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "def", "default", "do", "double", "else", "enum",
        "extends", "false", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "in", "instanceof", "int", "interface", "long", "native", "new", "null",
        "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "trait", "transient",
        "true", "try", "var", "void", "volatile", "while"
    );

    private final ModelMetadata metadata;
    private final GenerationConfig config;
    private final Map<String, String> classNames;
    private final Map<String, String> enumNames;
    private final Map<String, Map<String, String>> propertyNames;
    private final Map<String, String> collectionPropertyNames;
    private final Map<String, Map<String, String>> enumConstantNames;

    private TargetNameRegistry(ModelMetadata metadata, GenerationConfig config) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.config = Objects.requireNonNull(config, "config");
        this.classNames = resolveTypeNames(
            metadata.getAllClasses().stream()
                .sorted(Comparator.comparing(ClassMetadata::getName, Comparator.nullsLast(String::compareTo)))
                .toList(),
            ClassMetadata::getName,
            ClassMetadata::getSimpleName
        );
        this.enumNames = resolveTypeNames(
            metadata.getAllEnums().stream()
                .sorted(Comparator.comparing(EnumMetadata::getName, Comparator.nullsLast(String::compareTo)))
                .toList(),
            EnumMetadata::getName,
            EnumMetadata::getSimpleName
        );
        this.propertyNames = resolvePropertyNames();
        this.collectionPropertyNames = resolveCollectionPropertyNames();
        this.enumConstantNames = resolveEnumConstantNames();
    }

    public static TargetNameRegistry forMetadata(ModelMetadata metadata, GenerationConfig config) {
        return new TargetNameRegistry(metadata, config);
    }

    public String className(ClassMetadata classMetadata) {
        if (classMetadata == null) {
            return "Object";
        }
        return classNames.getOrDefault(classMetadata.getName(), toPascalIdentifier(classMetadata.getSimpleName()));
    }

    public String className(String className) {
        ClassMetadata classMetadata = metadata.getClass(className);
        if (classMetadata != null) {
            return className(classMetadata);
        }
        return toPascalIdentifier(simpleSegment(className));
    }

    public String enumName(EnumMetadata enumMetadata) {
        if (enumMetadata == null) {
            return "Object";
        }
        return enumNames.getOrDefault(enumMetadata.getName(), toPascalIdentifier(enumMetadata.getSimpleName()));
    }

    public String enumConstantName(EnumMetadata enumMetadata, EnumMetadata.EnumValue enumValue) {
        if (enumMetadata == null || enumValue == null) {
            return "VALUE";
        }
        Map<String, String> names = enumConstantNames.get(enumMetadata.getName());
        if (names == null) {
            return toEnumConstantIdentifier(enumValue.getIliCode());
        }
        return names.getOrDefault(enumValueKey(enumValue), toEnumConstantIdentifier(enumValue.getIliCode()));
    }

    public String propertyName(ClassMetadata classMetadata, AttributeMetadata attribute) {
        if (classMetadata == null || attribute == null) {
            return "value";
        }
        Map<String, String> names = propertyNames.get(classMetadata.getName());
        if (names == null) {
            return toLowerCamelIdentifier(rawPropertyBase(attribute));
        }
        return names.getOrDefault(attributeKey(attribute), toLowerCamelIdentifier(rawPropertyBase(attribute)));
    }

    public String relationshipPropertyName(RelationshipMetadata relationship) {
        if (relationship == null) {
            return "value";
        }
        if (relationship.getTargetRoleName() != null && !relationship.getTargetRoleName().isBlank()) {
            return toLowerCamelIdentifier(relationship.getTargetRoleName());
        }
        if (relationship.getSourceAttribute() != null && !relationship.getSourceAttribute().isBlank()) {
            return toLowerCamelIdentifier(relationship.getSourceAttribute());
        }
        if (relationship.getSourceRoleName() != null && !relationship.getSourceRoleName().isBlank()) {
            return toLowerCamelIdentifier(relationship.getSourceRoleName());
        }
        return toLowerCamelIdentifier(simpleSegment(relationship.getName()));
    }

    public String collectionPropertyName(RelationshipMetadata relationship) {
        if (relationship == null) {
            return "items";
        }
        return collectionPropertyNames.getOrDefault(
            relationshipKey(relationship),
            rawCollectionPropertyName(relationship)
        );
    }

    public String controllerName(ClassMetadata classMetadata) {
        return className(classMetadata) + "Controller";
    }

    public String viewPath(ClassMetadata classMetadata) {
        return toLowerCamelIdentifier(className(classMetadata));
    }

    public String domainPackage() {
        return config.getDomainPackage();
    }

    public String enumPackage() {
        return config.getEnumPackage();
    }

    public String controllerPackage() {
        return config.getControllerPackage();
    }

    private <T> Map<String, String> resolveTypeNames(List<T> elements,
                                                     Function<T, String> qualifiedName,
                                                     Function<T, String> simpleName) {
        Map<String, String> resolved = new LinkedHashMap<>();
        Map<String, List<T>> byBase = elements.stream()
            .collect(Collectors.groupingBy(
                element -> toPascalIdentifier(simpleName.apply(element)),
                LinkedHashMap::new,
                Collectors.toList()
            ));
        Set<String> used = new LinkedHashSet<>();
        for (Map.Entry<String, List<T>> entry : byBase.entrySet()) {
            String base = entry.getKey();
            List<T> group = entry.getValue().stream()
                .sorted(Comparator.comparing(qualifiedName, Comparator.nullsLast(String::compareTo)))
                .toList();
            if (group.size() == 1 && used.add(base)) {
                resolved.put(qualifiedName.apply(group.get(0)), base);
                continue;
            }
            int index = 1;
            for (T element : group) {
                List<String> candidates = typeNameCandidates(qualifiedName.apply(element), base, index++);
                String name = firstAvailable(candidates, used);
                resolved.put(qualifiedName.apply(element), name);
            }
        }
        return resolved;
    }

    private List<String> typeNameCandidates(String qualifiedName, String base, int index) {
        List<String> candidates = new ArrayList<>();
        List<String> parentSegments = parentSegments(qualifiedName);
        if (!parentSegments.isEmpty()) {
            candidates.add(toPascalIdentifier(parentSegments.get(parentSegments.size() - 1) + "_" + base));
        }
        if (parentSegments.size() > 1) {
            candidates.add(toPascalIdentifier(String.join("_", parentSegments.subList(1, parentSegments.size())) + "_" + base));
        }
        if (!parentSegments.isEmpty()) {
            candidates.add(toPascalIdentifier(String.join("_", parentSegments) + "_" + base));
        }
        candidates.add(base + index);
        return candidates;
    }

    private Map<String, Map<String, String>> resolveEnumConstantNames() {
        Map<String, Map<String, String>> resolved = new LinkedHashMap<>();
        for (EnumMetadata enumMetadata : metadata.getAllEnums()) {
            Map<String, String> constants = new LinkedHashMap<>();
            Set<String> used = new LinkedHashSet<>();
            List<EnumMetadata.EnumValue> values = enumMetadata.getValues().stream()
                .sorted(Comparator
                    .comparingInt(EnumMetadata.EnumValue::getSeq)
                    .thenComparing(EnumMetadata.EnumValue::getIliCode, Comparator.nullsLast(String::compareTo)))
                .toList();
            for (EnumMetadata.EnumValue enumValue : values) {
                String raw = enumValue.getIliCode();
                String candidate = isValidIdentifier(raw) && used.add(raw)
                    ? raw
                    : firstAvailable(enumConstantCandidates(raw, enumValue.getSeq()), used);
                constants.put(enumValueKey(enumValue), candidate);
            }
            resolved.put(enumMetadata.getName(), constants);
        }
        return resolved;
    }

    private List<String> enumConstantCandidates(String value, int sequence) {
        List<String> candidates = new ArrayList<>();
        candidates.add(toEnumConstantIdentifier(value));
        candidates.add("VALUE_" + sequence);
        return candidates;
    }

    private Map<String, Map<String, String>> resolvePropertyNames() {
        Map<String, Map<String, String>> resolved = new LinkedHashMap<>();
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            List<AttributeMetadata> attributes = classMetadata.getAllAttributes().stream()
                .sorted(Comparator.comparing(this::attributeSortKey))
                .toList();
            Map<String, String> classProperties = new LinkedHashMap<>();
            Map<String, List<AttributeMetadata>> byBase = attributes.stream()
                .collect(Collectors.groupingBy(
                    attribute -> toLowerCamelIdentifier(rawPropertyBase(attribute)),
                    LinkedHashMap::new,
                    Collectors.toList()
                ));
            Set<String> used = new LinkedHashSet<>();
            for (Map.Entry<String, List<AttributeMetadata>> entry : byBase.entrySet()) {
                String base = entry.getKey();
                List<AttributeMetadata> group = entry.getValue();
                if (group.size() == 1 && used.add(base)) {
                    classProperties.put(attributeKey(group.get(0)), base);
                    continue;
                }
                int index = 1;
                for (AttributeMetadata attribute : group) {
                    List<String> candidates = propertyNameCandidates(attribute, base, index++);
                    String name = firstAvailable(candidates, used);
                    classProperties.put(attributeKey(attribute), name);
                }
            }
            resolved.put(classMetadata.getName(), classProperties);
        }
        return resolved;
    }

    private List<String> propertyNameCandidates(AttributeMetadata attribute, String base, int index) {
        List<String> candidates = new ArrayList<>();
        String targetContext = targetContext(attribute);
        if (targetContext != null && !targetContext.isBlank()) {
            candidates.add(toLowerCamelIdentifier(targetContext + "_" + base));
        }
        String attributeName = attribute.getName();
        if (attributeName != null && !attributeName.isBlank()) {
            candidates.add(toLowerCamelIdentifier(attributeName + "_" + base));
        }
        String qualifiedName = attribute.getQualifiedName();
        List<String> parentSegments = parentSegments(qualifiedName);
        if (!parentSegments.isEmpty()) {
            candidates.add(toLowerCamelIdentifier(parentSegments.get(parentSegments.size() - 1) + "_" + base));
        }
        candidates.add(base + index);
        return candidates;
    }

    private Map<String, String> resolveCollectionPropertyNames() {
        Map<String, String> resolved = new HashMap<>();
        Map<String, List<RelationshipMetadata>> byTarget = metadata.getAllRelationships().stream()
            .filter(relationship -> relationship.getType() == RelationshipMetadata.RelationType.MANY_TO_ONE
                || relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE)
            .filter(relationship -> relationship.getTargetClass() != null)
            .sorted(relationshipComparator())
            .collect(Collectors.groupingBy(
                this::collectionOwnerKey,
                LinkedHashMap::new,
                Collectors.toList()
            ));
        for (List<RelationshipMetadata> relationships : byTarget.values()) {
            Map<String, List<RelationshipMetadata>> byBase = relationships.stream()
                .collect(Collectors.groupingBy(
                    this::rawCollectionPropertyName,
                    LinkedHashMap::new,
                    Collectors.toList()
                ));
            Set<String> used = new LinkedHashSet<>();
            for (Map.Entry<String, List<RelationshipMetadata>> entry : byBase.entrySet()) {
                String base = entry.getKey();
                List<RelationshipMetadata> group = entry.getValue();
                if (group.size() == 1 && used.add(base)) {
                    resolved.put(relationshipKey(group.get(0)), base);
                    continue;
                }
                int index = 1;
                for (RelationshipMetadata relationship : group) {
                    List<String> candidates = collectionNameCandidates(relationship, base, index++);
                    String name = firstAvailable(candidates, used);
                    resolved.put(relationshipKey(relationship), name);
                }
            }
        }
        return resolved;
    }

    private List<String> collectionNameCandidates(RelationshipMetadata relationship, String base, int index) {
        List<String> candidates = new ArrayList<>();
        boolean composition = relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE;
        if (relationship.getTargetRoleName() != null) {
            String roleName = toLowerCamelIdentifier(relationship.getTargetRoleName());
            candidates.add(composition ? roleName : NameUtils.pluralize(roleName));
        }
        if (relationship.getSourceAttribute() != null) {
            String attributeName = toLowerCamelIdentifier(relationship.getSourceAttribute());
            candidates.add(composition ? attributeName : NameUtils.pluralize(attributeName));
        }
        candidates.add(base + index);
        return candidates;
    }

    private String rawCollectionPropertyName(RelationshipMetadata relationship) {
        if (relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
            && relationship.getSourceAttribute() != null
            && !relationship.getSourceAttribute().isBlank()) {
            return toLowerCamelIdentifier(relationship.getSourceAttribute());
        }
        if (relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
            && relationship.getTargetRoleName() != null
            && !relationship.getTargetRoleName().isBlank()) {
            return toLowerCamelIdentifier(relationship.getTargetRoleName());
        }
        return NameUtils.pluralize(toLowerCamelIdentifier(className(relationship.getSourceClass())));
    }

    private String collectionOwnerKey(RelationshipMetadata relationship) {
        if (relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE) {
            return relationship.getSourceClass();
        }
        return relationship.getTargetClass();
    }

    private String targetContext(AttributeMetadata attribute) {
        if (attribute.getReferencedClass() != null) {
            return className(attribute.getReferencedClass());
        }
        if (attribute.getEnumType() != null) {
            EnumMetadata enumMetadata = metadata.getEnums().get(attribute.getEnumType());
            if (enumMetadata != null) {
                return enumName(enumMetadata);
            }
            return simpleSegment(attribute.getEnumType());
        }
        if (attribute.getDomainName() != null) {
            return simpleSegment(attribute.getDomainName());
        }
        return null;
    }

    private String rawPropertyBase(AttributeMetadata attribute) {
        if (attribute.getSqlName() != null && !attribute.getSqlName().isBlank()) {
            return attribute.getSqlName();
        }
        return attribute.getName();
    }

    private String firstAvailable(List<String> candidates, Set<String> used) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && used.add(candidate)) {
                return candidate;
            }
        }
        String fallbackBase = candidates.stream()
            .filter(candidate -> candidate != null && !candidate.isBlank())
            .findFirst()
            .orElse("GeneratedName");
        int suffix = 1;
        String candidate;
        do {
            candidate = fallbackBase + suffix++;
        } while (!used.add(candidate));
        return candidate;
    }

    private String toPascalIdentifier(String value) {
        String identifier = toPascal(value);
        if (identifier.isBlank()) {
            identifier = "GeneratedType";
        }
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            identifier = "Type" + identifier;
        }
        if (isReserved(identifier)) {
            identifier += "Type";
        }
        return identifier;
    }

    private String toLowerCamelIdentifier(String value) {
        List<String> segments = splitSegments(value);
        if (segments.isEmpty()) {
            return "value";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(normalizeLowerSegment(segments.get(0)));
        for (int i = 1; i < segments.size(); i++) {
            builder.append(normalizePascalSegment(segments.get(i)));
        }
        String identifier = builder.toString();
        if (identifier.isBlank()) {
            identifier = "value";
        }
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            identifier = "value" + normalizePascalSegment(identifier);
        }
        if (isReserved(identifier)) {
            identifier += "Type";
        }
        return identifier;
    }

    private String toEnumConstantIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "VALUE";
        }
        String identifier = value.trim().replaceAll("[^A-Za-z0-9_]+", "_");
        identifier = identifier.replaceAll("_+", "_");
        identifier = trimUnderscores(identifier);
        if (identifier.isBlank()) {
            identifier = "VALUE";
        }
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            identifier = "VALUE_" + identifier;
        }
        if (isReserved(identifier)) {
            identifier += "Type";
        }
        return identifier;
    }

    private String toPascal(String value) {
        return splitSegments(value).stream()
            .map(this::normalizePascalSegment)
            .collect(Collectors.joining());
    }

    private List<String> splitSegments(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] rawParts = value.trim().split("[^A-Za-z0-9]+");
        List<String> segments = new ArrayList<>();
        for (String rawPart : rawParts) {
            if (!rawPart.isBlank()) {
                segments.add(rawPart);
            }
        }
        return segments;
    }

    private String normalizePascalSegment(String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        if (isAllUpper(segment)) {
            return segment;
        }
        if (hasMixedCase(segment)) {
            return Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
        }
        String lower = segment.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String normalizeLowerSegment(String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        if (isAllUpper(segment)) {
            return segment.toLowerCase(Locale.ROOT);
        }
        if (hasMixedCase(segment)) {
            return Character.toLowerCase(segment.charAt(0)) + segment.substring(1);
        }
        return segment.toLowerCase(Locale.ROOT);
    }

    private boolean isAllUpper(String value) {
        return value.equals(value.toUpperCase(Locale.ROOT))
            && !value.equals(value.toLowerCase(Locale.ROOT));
    }

    private boolean hasMixedCase(String value) {
        return !value.equals(value.toUpperCase(Locale.ROOT))
            && !value.equals(value.toLowerCase(Locale.ROOT));
    }

    private boolean isReserved(String value) {
        return RESERVED_WORDS.contains(value.toLowerCase(Locale.ROOT));
    }

    private boolean isValidIdentifier(String value) {
        if (value == null || value.isBlank() || isReserved(value)) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String trimUnderscores(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '_') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '_') {
            end--;
        }
        return value.substring(start, end);
    }

    private List<String> parentSegments(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return List.of();
        }
        String[] segments = qualifiedName.split("\\.");
        if (segments.length <= 1) {
            return List.of();
        }
        List<String> parents = new ArrayList<>();
        for (int i = 0; i < segments.length - 1; i++) {
            parents.add(segments[i]);
        }
        return parents;
    }

    private String simpleSegment(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return "Object";
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    private String attributeKey(AttributeMetadata attribute) {
        if (attribute.getQualifiedName() != null) {
            return attribute.getQualifiedName();
        }
        if (attribute.getColumnName() != null) {
            return attribute.getName() + "|" + attribute.getColumnName();
        }
        return attribute.getName();
    }

    private String attributeSortKey(AttributeMetadata attribute) {
        return String.join("|",
            nullToEmpty(attribute.getQualifiedName()),
            nullToEmpty(attribute.getName()),
            nullToEmpty(attribute.getColumnName())
        );
    }

    private String enumValueKey(EnumMetadata.EnumValue enumValue) {
        return enumValue.getSeq() + "|" + nullToEmpty(enumValue.getIliCode());
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

    private Comparator<RelationshipMetadata> relationshipComparator() {
        return Comparator
            .comparing(RelationshipMetadata::getTargetClass, Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMetadata::getSourceClass, Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMetadata::getTargetRoleName, Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMetadata::getName, Comparator.nullsLast(String::compareTo));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package ch.interlis.generator.django;

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
 * Resolves Python/Django names without writing target-specific names into the Core-IR.
 */
final class DjangoNameRegistry {

    private static final Set<String> PYTHON_RESERVED_WORDS = Set.of(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
        "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
        "try", "while", "with", "yield"
    );

    private final ModelMetadata metadata;
    private final Map<String, String> classNames;
    private final Map<String, String> enumChoiceNames;
    private final Map<String, Map<String, String>> enumConstantNames;

    private DjangoNameRegistry(ModelMetadata metadata) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.classNames = resolveTypeNames(
            metadata.getAllClasses().stream()
                .sorted(Comparator.comparing(ClassMetadata::getName, Comparator.nullsLast(String::compareTo)))
                .toList(),
            ClassMetadata::getName,
            ClassMetadata::getSimpleName
        );
        this.enumChoiceNames = resolveTypeNames(
            metadata.getAllEnums().stream()
                .sorted(Comparator.comparing(EnumMetadata::getName, Comparator.nullsLast(String::compareTo)))
                .toList(),
            EnumMetadata::getName,
            enumMetadata -> enumMetadata.getSimpleName() + "Choices"
        );
        this.enumConstantNames = resolveEnumConstantNames();
    }

    static DjangoNameRegistry forMetadata(ModelMetadata metadata) {
        return new DjangoNameRegistry(metadata);
    }

    String className(ClassMetadata classMetadata) {
        if (classMetadata == null) {
            return "object";
        }
        return classNames.getOrDefault(classMetadata.getName(), toPascalIdentifier(classMetadata.getSimpleName()));
    }

    String className(String className) {
        ClassMetadata classMetadata = metadata.getClass(className);
        if (classMetadata != null) {
            return className(classMetadata);
        }
        return toPascalIdentifier(simpleSegment(className));
    }

    String enumChoicesName(EnumMetadata enumMetadata) {
        if (enumMetadata == null) {
            return "GeneratedChoices";
        }
        return enumChoiceNames.getOrDefault(
            enumMetadata.getName(),
            toPascalIdentifier(enumMetadata.getSimpleName()) + "Choices"
        );
    }

    String enumConstantName(EnumMetadata enumMetadata, EnumMetadata.EnumValue enumValue) {
        if (enumMetadata == null || enumValue == null) {
            return "VALUE";
        }
        Map<String, String> names = enumConstantNames.get(enumMetadata.getName());
        if (names == null) {
            return toUpperSnakeIdentifier(enumValue.getIliCode());
        }
        return names.getOrDefault(enumValueKey(enumValue), toUpperSnakeIdentifier(enumValue.getIliCode()));
    }

    String fieldName(AttributeMetadata attribute) {
        if (attribute == null) {
            return "value";
        }
        return toSnakeIdentifier(attribute.getName());
    }

    String relationshipFieldName(RelationshipMetadata relationship) {
        if (relationship == null) {
            return "value";
        }
        if (hasText(relationship.getTargetRoleName())) {
            return toSnakeIdentifier(relationship.getTargetRoleName());
        }
        if (hasText(relationship.getSourceAttribute())) {
            return toSnakeIdentifier(stripIdSuffix(relationship.getSourceAttribute()));
        }
        if (hasText(relationship.getSourceRoleName())) {
            return toSnakeIdentifier(relationship.getSourceRoleName());
        }
        return toSnakeIdentifier(simpleSegment(relationship.getName()));
    }

    static String toSnakeIdentifier(String raw) {
        List<String> words = words(raw);
        String value = words.isEmpty()
            ? "value"
            : words.stream().map(word -> word.toLowerCase(Locale.ROOT)).collect(Collectors.joining("_"));
        if (Character.isDigit(value.charAt(0))) {
            value = "field_" + value;
        }
        if (PYTHON_RESERVED_WORDS.contains(value)) {
            value = value + "_field";
        }
        return value;
    }

    private static String toUpperSnakeIdentifier(String raw) {
        String value = toSnakeIdentifier(raw).toUpperCase(Locale.ROOT);
        if (Character.isDigit(value.charAt(0))) {
            value = "VALUE_" + value;
        }
        return value;
    }

    private static String toPascalIdentifier(String raw) {
        List<String> words = words(raw);
        String value = words.isEmpty()
            ? "GeneratedModel"
            : words.stream()
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT)
                    + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining());
        if (Character.isDigit(value.charAt(0))) {
            value = "Model" + value;
        }
        if (PYTHON_RESERVED_WORDS.contains(value)) {
            value = value + "Model";
        }
        return value;
    }

    private static List<String> words(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String separated = raw
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("([A-Z])([A-Z][a-z])", "$1_$2");
        String[] parts = separated.split("[^A-Za-z0-9]+");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                words.add(part);
            }
        }
        return words;
    }

    private <T> Map<String, String> resolveTypeNames(List<T> items,
                                                     Function<T, String> qualifiedName,
                                                     Function<T, String> preferredName) {
        Map<String, Long> simpleCounts = items.stream()
            .collect(Collectors.groupingBy(preferredName, LinkedHashMap::new, Collectors.counting()));
        Map<String, String> names = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        for (T item : items) {
            String preferred = preferredName.apply(item);
            String candidate = simpleCounts.getOrDefault(preferred, 0L) > 1
                ? toPascalIdentifier(contextPrefix(qualifiedName.apply(item)) + "_" + preferred)
                : toPascalIdentifier(preferred);
            names.put(qualifiedName.apply(item), unique(candidate, used));
        }
        return names;
    }

    private Map<String, Map<String, String>> resolveEnumConstantNames() {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (EnumMetadata enumMetadata : metadata.getAllEnums()) {
            Map<String, String> names = new LinkedHashMap<>();
            Set<String> used = new LinkedHashSet<>();
            for (EnumMetadata.EnumValue value : enumMetadata.getValues()) {
                names.put(enumValueKey(value), unique(toUpperSnakeIdentifier(value.getIliCode()), used));
            }
            result.put(enumMetadata.getName(), names);
        }
        return result;
    }

    private static String contextPrefix(String qualifiedName) {
        if (!hasText(qualifiedName)) {
            return "model";
        }
        String[] parts = qualifiedName.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2];
        }
        return parts[0];
    }

    private static String unique(String candidate, Set<String> used) {
        String value = candidate;
        int suffix = 2;
        while (!used.add(value)) {
            value = candidate + suffix;
            suffix++;
        }
        return value;
    }

    private static String stripIdSuffix(String value) {
        if (value == null) {
            return null;
        }
        if (value.toLowerCase(Locale.ROOT).endsWith("_id")) {
            return value.substring(0, value.length() - 3);
        }
        return value;
    }

    private static String simpleSegment(String value) {
        if (value == null) {
            return null;
        }
        int idx = value.lastIndexOf('.');
        return idx >= 0 ? value.substring(idx + 1) : value;
    }

    private static String enumValueKey(EnumMetadata.EnumValue value) {
        return value.getIliCode() + "#" + value.getSeq();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

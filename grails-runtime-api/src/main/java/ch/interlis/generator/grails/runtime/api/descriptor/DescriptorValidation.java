package ch.interlis.generator.grails.runtime.api.descriptor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared validation and defensive-copy helpers for descriptor records.
 *
 * <p>Package-private by design: descriptors are the only consumers.</p>
 */
final class DescriptorValidation {

    private DescriptorValidation() {
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }

    static <T> List<T> immutableCopy(List<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        List<T> copy = List.copyOf(values);
        if (containsNull(copy)) {
            throw new IllegalArgumentException(fieldName + " must not contain null values");
        }
        return copy;
    }

    static <T> Set<T> immutableCopy(Set<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        Set<T> copy = new LinkedHashSet<>(values);
        if (containsNull(copy)) {
            throw new IllegalArgumentException(fieldName + " must not contain null values");
        }
        return Set.copyOf(copy);
    }

    static <K, V> Map<K, V> immutableLinkedCopy(Map<K, V> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        Map<K, V> copy = new LinkedHashMap<>(values);
        if (copy.containsKey(null) || copy.containsValue(null)) {
            throw new IllegalArgumentException(fieldName + " must not contain null keys or values");
        }
        return java.util.Collections.unmodifiableMap(copy);
    }

    static void requireDistinctNames(List<String> names, String fieldName) {
        Set<String> unique = new LinkedHashSet<>(names);
        if (unique.size() != names.size()) {
            throw new IllegalArgumentException(fieldName + " must not contain duplicate names");
        }
    }

    private static boolean containsNull(List<?> values) {
        return values.stream().anyMatch(Objects::isNull);
    }

    private static boolean containsNull(Set<?> values) {
        return values.stream().anyMatch(Objects::isNull);
    }
}

package ch.interlis.generator.generator;

import java.util.Locale;

final class NameUtils {

    private NameUtils() {
    }

    static String toLowerCamel(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String[] parts = value.replace('-', '_').split("_");
        StringBuilder sb = new StringBuilder();
        int index = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isBlank()) {
                continue;
            }
            String normalized = normalizePart(part);
            if (index == 0) {
                sb.append(lowercaseFirst(normalized));
            } else {
                sb.append(uppercaseFirst(normalized));
            }
            index++;
        }
        if (sb.isEmpty()) {
            return value;
        }
        return sb.toString();
    }

    static String toUpperCamel(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String lowerCamel = toLowerCamel(value);
        if (lowerCamel == null || lowerCamel.isBlank()) {
            return lowerCamel;
        }
        return uppercaseFirst(lowerCamel);
    }

    static String pluralize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith("s")) {
            return value + "es";
        }
        return value + "s";
    }

    static String packageToPath(String packageName) {
        return packageName.replace('.', '/');
    }

    static String simpleType(String javaType) {
        if (javaType == null) {
            return "Object";
        }
        int lastDot = javaType.lastIndexOf('.');
        return lastDot >= 0 ? javaType.substring(lastDot + 1) : javaType;
    }

    private static String normalizePart(String part) {
        boolean hasUpper = !part.equals(part.toLowerCase(Locale.ROOT));
        boolean hasLower = !part.equals(part.toUpperCase(Locale.ROOT));
        if (hasUpper && hasLower) {
            return part;
        }
        return part.toLowerCase(Locale.ROOT);
    }

    private static String lowercaseFirst(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String uppercaseFirst(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}

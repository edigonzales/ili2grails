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
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isBlank()) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            if (i == 0) {
                sb.append(lower);
            } else {
                sb.append(Character.toUpperCase(lower.charAt(0)))
                    .append(lower.substring(1));
            }
        }
        if (sb.isEmpty()) {
            return value;
        }
        return sb.toString();
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
}

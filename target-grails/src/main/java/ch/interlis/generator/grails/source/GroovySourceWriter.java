package ch.interlis.generator.grails.source;

import java.util.Collection;
import java.util.List;

/**
 * Escapes and renders Groovy source literals deterministically.
 *
 * <p>Registry generators use these primitives instead of ad-hoc quote helpers
 * so that escaping stays uniform and compile-time checkable.</p>
 */
public final class GroovySourceWriter {

    /** Single-quoted Groovy string literal with all escapes. */
    public String stringLiteral(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder("'");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '\'' -> escaped.append("\\'");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '$' -> escaped.append("\\$");
                default -> escaped.append(character);
            }
        }
        return escaped.append('\'').toString();
    }

    /** Enum literal rendered as the enum type name plus constant. */
    public String enumLiteral(Class<? extends Enum<?>> type, Enum<?> value) {
        if (value == null) {
            return "null";
        }
        return type.getSimpleName() + "." + value.name();
    }

    /** Groovy list literal of strings. */
    public String listOfStrings(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
            .map(this::stringLiteral)
            .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    /** Groovy list literal of a generic type rendered by the provided renderer. */
    public <T> String listOf(Collection<T> values, java.util.function.Function<T, String> renderer) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
            .map(renderer)
            .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    /** Groovy literal for a nullable integer (-1 stays the unbounded sentinel). */
    public String nullableInteger(Integer value) {
        return value == null ? "null" : Integer.toString(value);
    }

    /** Groovy literal for a nullable boolean. */
    public String nullableBoolean(Boolean value) {
        return value == null ? "null" : Boolean.toString(value);
    }
}

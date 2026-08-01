package ch.interlis.generator.reader.sql;

import java.util.Objects;

/**
 * Typisierter SQL-Identifier (Schema-, Tabellen- oder Spaltenname).
 *
 * <p>Prepared Statements schützen Werte, nicht Identifier. Dynamische Identifier werden
 * deshalb als eigener Typ behandelt: sie werden je nach {@link SqlIdentifierKind} validiert,
 * segmentweise modelliert und erst beim SQL-Rendering über den {@link SqlIdentifierRenderer}
 * gequotet. Raw-Namen in der Core-IR bleiben ungequotet; Quoting ist ausschliesslich eine
 * SQL-Rendering-Aufgabe.</p>
 */
public final class SqlIdentifier {

    private static final int MAX_IDENTIFIER_LENGTH = 128;

    private final String value;
    private final SqlIdentifierKind kind;

    private SqlIdentifier(String value, SqlIdentifierKind kind) {
        this.value = value;
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    /**
     * Erzeugt einen vom Benutzer gelieferten Identifier und validiert ihn strikt.
     *
     * @throws InvalidSqlIdentifierException bei Verletzung der Zeichenregeln
     */
    public static SqlIdentifier userSupplied(String value) {
        validateUserSupplied(value);
        return new SqlIdentifier(value, SqlIdentifierKind.USER_SUPPLIED);
    }

    /**
     * Erzeugt einen aus der Datenbank entdeckten Identifier (breiter Zeichensatz erlaubt).
     */
    public static SqlIdentifier discovered(String value) {
        validateDiscovered(value);
        return new SqlIdentifier(value, SqlIdentifierKind.DATABASE_DISCOVERED);
    }

    /**
     * Erzeugt einen internen Konstanten-Identifier. Verletzung des Musters ist ein Programmierfehler.
     *
     * @throws InvalidSqlIdentifierException wenn der Name nicht dem Muster
     *         {@code [A-Za-z_][A-Za-z0-9_]*} entspricht
     */
    public static SqlIdentifier internal(String value) {
        if (!INTERNAL_PATTERN.matcher(value).matches()) {
            throw new InvalidSqlIdentifierException(
                SqlIdentifierKind.INTERNAL_CONSTANT,
                value,
                "internal constant identifiers must match [A-Za-z_][A-Za-z0-9_]*"
            );
        }
        return new SqlIdentifier(value, SqlIdentifierKind.INTERNAL_CONSTANT);
    }

    public String value() {
        return value;
    }

    public SqlIdentifierKind kind() {
        return kind;
    }

    /**
     * Gibt an, ob der Identifier beim Rendering gequotet werden muss.
     *
     * <p>Benutzergelieferte Identifier brauchen kein Quoting, wenn sie exakt dem
     * ungequoteten Kleinbuchstaben-Muster entsprechen (PostgreSQL foldet ungequotete
     * Identifier auf Kleinbuchstaben). Entdeckte Identifier werden gequotet, sobald sie
     * vom Kleinbuchstaben-Muster abweichen (Grossbuchstaben, Leerzeichen, Sonderzeichen);
     * reine Kleinbuchstaben-Namen wurden in der Regel ungequotet angelegt und sind auch
     * ungequotet eindeutig auflösbar.</p>
     */
    public boolean requiresQuoting() {
        return switch (kind) {
            case USER_SUPPLIED -> !LOWERCASE_PATTERN.matcher(value).matches();
            case DATABASE_DISCOVERED -> !LOWERCASE_PATTERN.matcher(value).matches();
            case INTERNAL_CONSTANT -> false;
        };
    }

    private static void validateUserSupplied(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidSqlIdentifierException(
                SqlIdentifierKind.USER_SUPPLIED, value, "identifier must not be blank");
        }
        if (value.indexOf('.') >= 0) {
            throw new InvalidSqlIdentifierException(
                SqlIdentifierKind.USER_SUPPLIED, value, "identifier must be a single segment without '.'");
        }
        if (value.indexOf('\0') >= 0) {
            throw new InvalidSqlIdentifierException(
                SqlIdentifierKind.USER_SUPPLIED, value, "identifier must not contain NUL");
        }
        if (hasControlCharacters(value)) {
            throw new InvalidSqlIdentifierException(
                SqlIdentifierKind.USER_SUPPLIED, value, "identifier must not contain control characters");
        }
        if (value.indexOf(';') >= 0) {
            throw new InvalidSqlIdentifierException(
                SqlIdentifierKind.USER_SUPPLIED, value, "identifier must not contain ';'");
        }
        if (value.contains("--") || value.contains("/*") || value.contains("*/")) {
            throw new InvalidSqlIdentifierException(
                SqlIdentifierKind.USER_SUPPLIED, value, "identifier must not contain SQL comment sequences");
        }
        if (value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new InvalidSqlIdentifierException(
                SqlIdentifierKind.USER_SUPPLIED, value,
                "identifier length " + value.length() + " exceeds maximum " + MAX_IDENTIFIER_LENGTH);
        }
        char first = value.charAt(0);
        if (!(Character.isLetter(first) || first == '_')) {
            throw new InvalidSqlIdentifierException(
                SqlIdentifierKind.USER_SUPPLIED, value,
                "identifier must start with a letter or '_'");
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '-';
            if (!allowed) {
                throw new InvalidSqlIdentifierException(
                    SqlIdentifierKind.USER_SUPPLIED, value,
                    "identifier character '" + c + "' is not allowed");
            }
        }
    }

    private static void validateDiscovered(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidSqlIdentifierException(
                SqlIdentifierKind.DATABASE_DISCOVERED, value, "identifier must not be blank");
        }
        if (value.indexOf('\0') >= 0) {
            throw new InvalidSqlIdentifierException(
                SqlIdentifierKind.DATABASE_DISCOVERED, value, "identifier must not contain NUL");
        }
    }

    private static boolean hasControlCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || Character.getType(c) == Character.CONTROL) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.regex.Pattern INTERNAL_PATTERN =
        java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final java.util.regex.Pattern LOWERCASE_PATTERN =
        java.util.regex.Pattern.compile("[a-z_][a-z0-9_]*");

    @Override
    public String toString() {
        return "SqlIdentifier{" + kind + ", '" + value + "'}";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SqlIdentifier that)) {
            return false;
        }
        return kind == that.kind && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + value.hashCode();
    }
}

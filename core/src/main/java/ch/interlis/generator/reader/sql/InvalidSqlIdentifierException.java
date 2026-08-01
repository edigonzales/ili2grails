package ch.interlis.generator.reader.sql;

/**
 * Signalisiert einen ungültigen SQL-Identifier. Wird bei Eingabevalidierung
 * (Benutzereingaben) und bei Programmierfehlern (interne Konstanten) geworfen.
 */
public final class InvalidSqlIdentifierException extends IllegalArgumentException {

    private final SqlIdentifierKind kind;
    private final String value;
    private final String reason;

    public InvalidSqlIdentifierException(SqlIdentifierKind kind, String value, String reason) {
        super("Invalid " + kind + " SQL identifier: " + reason
            + (value == null ? "" : " (value '" + value + "')"));
        this.kind = kind;
        this.value = value;
        this.reason = reason;
    }

    public SqlIdentifierKind kind() {
        return kind;
    }

    public String value() {
        return value;
    }

    public String reason() {
        return reason;
    }
}

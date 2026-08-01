package ch.interlis.generator.reader.ili2db.schema;

/**
 * Primärschlüssel-Spalte einer Tabelle.
 */
public record PrimaryKeySchema(
    String columnName,
    int ordinalPosition
) {
}

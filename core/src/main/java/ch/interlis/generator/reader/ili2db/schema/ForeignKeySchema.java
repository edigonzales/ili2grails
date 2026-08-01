package ch.interlis.generator.reader.ili2db.schema;

/**
 * Fremdschlüssel-Referenz einer Tabelle.
 */
public record ForeignKeySchema(
    String columnName,
    String targetTable,
    String targetColumn,
    String constraintName
) {
}

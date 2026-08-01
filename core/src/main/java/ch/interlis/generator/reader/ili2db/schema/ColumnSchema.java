package ch.interlis.generator.reader.ili2db.schema;

/**
 * Spalten-Snapshot einer Tabelle.
 */
public record ColumnSchema(
    String name,
    Integer jdbcType,
    String databaseTypeName,
    boolean nullable,
    Integer size,
    Integer decimalDigits,
    Integer ordinalPosition
) {
}

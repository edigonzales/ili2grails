package ch.interlis.generator.grails.verification.mapping;

/**
 * Physische Datenbank-Spalte (Spezifikation §33).
 */
public record DatabaseColumnMapping(
    String columnName,
    String databaseType,
    boolean nullable,
    Integer srid,
    String geometryType
) {
}

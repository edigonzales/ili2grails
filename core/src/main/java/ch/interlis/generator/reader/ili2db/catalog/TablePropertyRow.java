package ch.interlis.generator.reader.ili2db.catalog;

/**
 * Typisierte Katalog-Zeile des ili2db-Metamodells.
 */
public record TablePropertyRow(
    String tableName, String setting
) {
}

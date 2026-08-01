package ch.interlis.generator.reader.ili2db.catalog;

/**
 * Typisierte Katalog-Zeile des ili2db-Metamodells.
 */
public record ModelRow(
    String modelName, String content
) {
}

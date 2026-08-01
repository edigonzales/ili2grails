package ch.interlis.generator.reader.ili2db.catalog;

/**
 * Typisierte Katalog-Zeile des ili2db-Metamodells.
 */
public record InheritanceRow(
    String thisClass, String baseClass
) {
}

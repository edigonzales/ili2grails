package ch.interlis.generator.reader.ili2db.catalog;

/**
 * Typisierte Katalog-Zeile des ili2db-Metamodells. Validiert nur grundlegende
 * Null-/Blank-Invarianten; die fachliche Zusammenführung geschieht im Assembler.
 */
public record AttributeMappingRow(
    String iliName,
    String sqlName,
    String owner,
    String target
) {
}

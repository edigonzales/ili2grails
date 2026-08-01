package ch.interlis.generator.reader.ili2db.catalog;

/**
 * Typisierte Katalog-Zeile des ili2db-Metamodells. Validiert nur grundlegende
 * Null-/Blank-Invarianten; die fachliche Zusammenführung geschieht im Assembler.
 */
public record EnumDomainRow(
    String ownerTable,
    String columnName,
    String enumIliName,
    String enumTableName
) {
}

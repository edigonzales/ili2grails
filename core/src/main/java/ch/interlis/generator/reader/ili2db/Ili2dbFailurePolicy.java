package ch.interlis.generator.reader.ili2db;

/**
 * Fehlerpolitik des ili2db-Readers.
 */
public enum Ili2dbFailurePolicy {
    /** Blockierende Diagnostics führen zum Abbruch. */
    STRICT,
    /** Diagnostics werden geliefert, der Lauf wird fortgesetzt. */
    DIAGNOSTIC
}

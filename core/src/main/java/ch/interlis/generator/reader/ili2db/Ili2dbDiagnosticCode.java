package ch.interlis.generator.reader.ili2db;

/**
 * Codes der Reader-Diagnostics.
 *
 * <p>Physische Duplikate (Tabelle/Spalte) gehören nicht hierher: Die
 * einzige Wahrheit dafür ist {@code ModelMetadataDiagnosticCode} im
 * Modell-Validator (P2-D005).</p>
 */
public enum Ili2dbDiagnosticCode {
    REQUIRED_META_TABLE_MISSING,
    OPTIONAL_META_TABLE_MISSING,
    META_TABLE_COLUMNS_UNSUPPORTED,
    REQUESTED_MODEL_MISSING,
    SELECTED_DEPENDENCY_MISSING,
    CLASS_MAPPING_INCOMPLETE,
    ATTRIBUTE_OWNER_UNRESOLVED,
    TARGET_CLASS_UNRESOLVED,
    COLUMN_SCHEMA_MISSING,
    ENUM_DOMAIN_UNRESOLVED,
    ENUM_TABLE_UNREADABLE,
    INHERITANCE_UNRESOLVED,
    GEOMETRY_METADATA_UNAVAILABLE,
    PRIMARY_KEY_ASSUMED,
    ASSOCIATION_MAPPING_INCOMPLETE,
    DATABASE_DIALECT_UNSUPPORTED
}

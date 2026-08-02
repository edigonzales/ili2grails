package ch.interlis.generator.reader.ili2db.metrics;

/**
 * Arten von JDBC-Aufrufen, die für Query-Budget-Verträge gezählt werden
 * (Spezifikation §50.1).
 */
public enum JdbcInvocationKind {
    CREATE_STATEMENT,
    PREPARE_STATEMENT,
    EXECUTE_QUERY,
    EXECUTE_UPDATE,
    METADATA_GET_TABLES,
    METADATA_GET_COLUMNS,
    METADATA_GET_PRIMARY_KEYS,
    METADATA_GET_IMPORTED_KEYS,
    METADATA_GET_EXPORTED_KEYS
}

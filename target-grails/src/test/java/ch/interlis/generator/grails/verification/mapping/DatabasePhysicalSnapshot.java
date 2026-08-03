package ch.interlis.generator.grails.verification.mapping;

import java.util.Map;

/**
 * Reales Datenbank-Schema (Spezifikation §33).
 */
public record DatabasePhysicalSnapshot(
    String schemaName,
    Map<String, DatabaseTableMapping> tables
) {

    public DatabasePhysicalSnapshot {
        tables = tables == null ? Map.of() : Map.copyOf(tables);
    }

    public DatabaseTableMapping table(String tableName) {
        if (tableName == null) {
            return null;
        }
        return tables.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(tableName))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
}

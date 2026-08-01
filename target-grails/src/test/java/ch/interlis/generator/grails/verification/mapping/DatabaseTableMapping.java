package ch.interlis.generator.grails.verification.mapping;

import java.util.List;
import java.util.Map;

/**
 * Physische Datenbank-Tabelle (Spezifikation §33).
 */
public record DatabaseTableMapping(
    String tableName,
    Map<String, DatabaseColumnMapping> columns,
    List<String> primaryKeyColumns,
    List<DatabaseForeignKeyMapping> foreignKeys
) {

    public DatabaseTableMapping {
        columns = columns == null ? Map.of() : Map.copyOf(columns);
        primaryKeyColumns = primaryKeyColumns == null ? List.of() : List.copyOf(primaryKeyColumns);
        foreignKeys = foreignKeys == null ? List.of() : List.copyOf(foreignKeys);
    }
}

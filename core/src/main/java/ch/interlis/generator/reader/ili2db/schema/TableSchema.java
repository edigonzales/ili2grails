package ch.interlis.generator.reader.ili2db.schema;

import ch.interlis.generator.reader.sql.QualifiedSqlName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable Schema-Snapshot einer Tabelle.
 */
public record TableSchema(
    QualifiedSqlName name,
    Map<String, ColumnSchema> columns,
    List<PrimaryKeySchema> primaryKeys,
    List<ForeignKeySchema> importedKeys
) {

    public TableSchema {
        Map<String, ColumnSchema> normalized = new LinkedHashMap<>();
        columns.forEach((key, value) -> normalized.put(normalize(key), value));
        columns = java.util.Collections.unmodifiableMap(normalized);
        primaryKeys = primaryKeys == null ? List.of() : List.copyOf(primaryKeys);
        importedKeys = importedKeys == null ? List.of() : List.copyOf(importedKeys);
    }

    public Optional<ColumnSchema> column(String rawName) {
        return rawName == null ? Optional.empty() : Optional.ofNullable(columns.get(normalize(rawName)));
    }

    public boolean isPrimaryKey(String rawColumnName) {
        if (rawColumnName == null) {
            return false;
        }
        String normalized = normalize(rawColumnName);
        return primaryKeys.stream().anyMatch(key -> normalize(key.columnName()).equals(normalized));
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}

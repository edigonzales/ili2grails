package ch.interlis.generator.reader.ili2db.schema;

import ch.interlis.generator.reader.sql.QualifiedSqlName;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable Schema-Snapshot über alle ausgewählten Tabellen.
 */
public final class JdbcSchemaSnapshot {

    private final Map<QualifiedSqlName, TableSchema> tables;
    private final Map<String, TableSchema> byNormalizedTable;

    public JdbcSchemaSnapshot(Collection<TableSchema> tables) {
        Map<QualifiedSqlName, TableSchema> indexed = new LinkedHashMap<>();
        Map<String, TableSchema> byName = new LinkedHashMap<>();
        for (TableSchema table : tables) {
            indexed.put(table.name(), table);
            String objectName = table.name().object() != null ? table.name().object().value() : null;
            if (objectName != null) {
                byName.putIfAbsent(normalize(objectName), table);
            }
            String schemaName = table.name().schema() != null ? table.name().schema().value() : null;
            if (schemaName != null && objectName != null) {
                byName.putIfAbsent(normalize(schemaName + "." + objectName), table);
            }
        }
        this.tables = Collections.unmodifiableMap(indexed);
        this.byNormalizedTable = Collections.unmodifiableMap(byName);
    }

    public Optional<TableSchema> table(QualifiedSqlName name) {
        return name == null ? Optional.empty() : Optional.ofNullable(tables.get(name));
    }

    public Optional<TableSchema> tableByRawName(String rawName) {
        if (rawName == null) {
            return Optional.empty();
        }
        TableSchema direct = byNormalizedTable.get(normalize(rawName));
        if (direct != null) {
            return Optional.of(direct);
        }
        for (TableSchema table : tables.values()) {
            String objectName = table.name().object() != null ? table.name().object().value() : null;
            if (objectName != null && normalize(objectName).equals(normalize(rawName))) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    public Optional<ColumnSchema> column(QualifiedSqlName table, String columnName) {
        return table(table).flatMap(schema -> schema.column(columnName));
    }

    public Collection<TableSchema> tables() {
        return tables.values();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

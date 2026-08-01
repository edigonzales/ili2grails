package ch.interlis.generator.reader.ili2db.schema;

import ch.interlis.generator.reader.ili2db.Ili2dbDiagnostic;
import ch.interlis.generator.reader.ili2db.Ili2dbDiagnosticCode;
import ch.interlis.generator.reader.ili2db.Ili2dbReadContext;
import ch.interlis.generator.reader.ili2db.Ili2dbSeverity;
import ch.interlis.generator.reader.sql.QualifiedSqlName;
import ch.interlis.generator.reader.sql.SqlIdentifier;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC-basierte Schema-Introspektion: Spalten, Primär- und Fremdschlüssel
 * pro Tabelle (ein Snapshot pro Tabelle, kein N+1 pro Attribut).
 */
public final class DefaultJdbcSchemaIntrospector implements JdbcSchemaIntrospector {

    @Override
    public JdbcSchemaSnapshot inspect(Ili2dbReadContext context,
                                      java.util.Collection<QualifiedSqlName> tables)
        throws SQLException {
        List<TableSchema> snapshots = new ArrayList<>();
        DatabaseMetaData metadata = context.connection().getMetaData();
        for (QualifiedSqlName table : tables) {
            snapshots.add(inspectTable(metadata, context, table));
        }
        return new JdbcSchemaSnapshot(snapshots);
    }

    public TableSchema inspectTable(DatabaseMetaData metadata,
                                    Ili2dbReadContext context,
                                    QualifiedSqlName table) throws SQLException {
        Map<String, ColumnSchema> columns = inspectColumns(metadata, context, table);
        List<PrimaryKeySchema> primaryKeys = inspectPrimaryKeys(metadata, context, table);
        List<ForeignKeySchema> importedKeys = inspectImportedKeys(metadata, context, table);
        return new TableSchema(table, columns, primaryKeys, importedKeys);
    }

    public Map<String, ColumnSchema> inspectColumns(DatabaseMetaData metadata,
                                                    Ili2dbReadContext context,
                                                    QualifiedSqlName table) throws SQLException {
        Map<String, ColumnSchema> columns = new LinkedHashMap<>();
        String schema = context.schema() != null ? context.schema().value() : null;
        String tableName = table.object() != null ? table.object().value() : null;
        if (tableName == null) {
            return columns;
        }
        try (ResultSet rs = metadata.getColumns(null, schema, tableName, null)) {
            while (rs.next()) {
                String resolvedTable = rs.getString("TABLE_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                if (columnName == null || (resolvedTable != null
                    && !resolvedTable.equalsIgnoreCase(tableName))) {
                    continue;
                }
                Integer dataType = rs.getInt("DATA_TYPE");
                if (rs.wasNull()) {
                    dataType = null;
                }
                Integer nullable = rs.getInt("NULLABLE");
                if (rs.wasNull()) {
                    nullable = null;
                }
                Integer size = rs.getInt("COLUMN_SIZE");
                if (rs.wasNull()) {
                    size = null;
                }
                Integer decimalDigits = rs.getInt("DECIMAL_DIGITS");
                if (rs.wasNull()) {
                    decimalDigits = null;
                }
                Integer ordinal = rs.getInt("ORDINAL_POSITION");
                if (rs.wasNull()) {
                    ordinal = null;
                }
                columns.put(columnName, new ColumnSchema(
                    columnName,
                    dataType,
                    rs.getString("TYPE_NAME"),
                    nullable == null || nullable != java.sql.ResultSetMetaData.columnNoNulls,
                    size,
                    decimalDigits,
                    ordinal
                ));
            }
        }
        return columns;
    }

    public List<PrimaryKeySchema> inspectPrimaryKeys(DatabaseMetaData metadata,
                                                     Ili2dbReadContext context,
                                                     QualifiedSqlName table) throws SQLException {
        List<PrimaryKeySchema> primaryKeys = new ArrayList<>();
        String schema = context.schema() != null ? context.schema().value() : null;
        String tableName = table.object() != null ? table.object().value() : null;
        if (tableName == null) {
            return primaryKeys;
        }
        try (ResultSet rs = metadata.getPrimaryKeys(null, schema, tableName)) {
            while (rs.next()) {
                primaryKeys.add(new PrimaryKeySchema(
                    rs.getString("COLUMN_NAME"),
                    rs.getInt("KEY_SEQ")
                ));
            }
        }
        return primaryKeys;
    }

    public List<ForeignKeySchema> inspectImportedKeys(DatabaseMetaData metadata,
                                                      Ili2dbReadContext context,
                                                      QualifiedSqlName table) throws SQLException {
        List<ForeignKeySchema> importedKeys = new ArrayList<>();
        String schema = context.schema() != null ? context.schema().value() : null;
        String tableName = table.object() != null ? table.object().value() : null;
        if (tableName == null) {
            return importedKeys;
        }
        try (ResultSet rs = metadata.getImportedKeys(null, schema, tableName)) {
            while (rs.next()) {
                importedKeys.add(new ForeignKeySchema(
                    rs.getString("FKCOLUMN_NAME"),
                    rs.getString("PKTABLE_NAME"),
                    rs.getString("PKCOLUMN_NAME"),
                    rs.getString("FK_NAME")
                ));
            }
        }
        return importedKeys;
    }
}

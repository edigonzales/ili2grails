package ch.interlis.generator.grails.verification.mapping;

import ch.interlis.generator.reader.sql.SqlIdentifier;
import ch.interlis.generator.reader.sql.SqlIdentifierRenderer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Liest das physische Datenbank-Schema über JDBC-Metadaten
 * (Spezifikation §33). Dynamische Identifier werden über die vorhandenen
 * SQL-Identifier-Klassen behandelt.
 */
public final class DatabasePhysicalSnapshotReader {

    public DatabasePhysicalSnapshot read(Connection connection, String schemaName)
        throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Map<String, DatabaseTableMapping> tables = new LinkedHashMap<>();
        Map<String, Map<String, DatabaseColumnMapping>> mutableColumns = new LinkedHashMap<>();

        List<String> tableNames = new ArrayList<>();
        try (ResultSet rs = metadata.getTables(null, schemaName, null, new String[] {"TABLE"})) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                if (table != null) {
                    tableNames.add(table);
                }
            }
        }
        for (String tableName : tableNames) {
            mutableColumns.put(tableName, readColumns(metadata, schemaName, tableName));
        }
        enrichGeometryColumns(connection, schemaName, mutableColumns);
        for (String tableName : tableNames) {
            tables.put(tableName, new DatabaseTableMapping(tableName,
                mutableColumns.get(tableName),
                readPrimaryKeys(metadata, schemaName, tableName),
                readForeignKeys(metadata, schemaName, tableName)));
        }
        return new DatabasePhysicalSnapshot(schemaName, tables);
    }

    private Map<String, DatabaseColumnMapping> readColumns(DatabaseMetaData metadata,
                                                           String schemaName,
                                                           String tableName) throws SQLException {
        Map<String, DatabaseColumnMapping> columns = new LinkedHashMap<>();
        try (ResultSet rs = metadata.getColumns(null, schemaName, tableName, null)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if (columnName == null) {
                    continue;
                }
                Integer nullable = rs.getInt("NULLABLE");
                if (rs.wasNull()) {
                    nullable = null;
                }
                columns.put(columnName, new DatabaseColumnMapping(
                    columnName,
                    rs.getString("TYPE_NAME"),
                    nullable == null || nullable != java.sql.ResultSetMetaData.columnNoNulls,
                    null,
                    null
                ));
            }
        }
        return columns;
    }

    private List<String> readPrimaryKeys(DatabaseMetaData metadata,
                                         String schemaName,
                                         String tableName) throws SQLException {
        List<String> primaryKeys = new ArrayList<>();
        try (ResultSet rs = metadata.getPrimaryKeys(null, schemaName, tableName)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }
        return primaryKeys;
    }

    private List<DatabaseForeignKeyMapping> readForeignKeys(DatabaseMetaData metadata,
                                                            String schemaName,
                                                            String tableName) throws SQLException {
        Map<String, MutableForeignKey> foreignKeys = new LinkedHashMap<>();
        try (ResultSet rs = metadata.getImportedKeys(null, schemaName, tableName)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                String pkColumn = rs.getString("PKCOLUMN_NAME");
                String key = fkName != null ? fkName : (pkTable + ":" + fkColumn);
                MutableForeignKey existing = foreignKeys.get(key);
                if (existing == null) {
                    existing = new MutableForeignKey(pkTable);
                    foreignKeys.put(key, existing);
                }
                existing.sourceColumns.add(fkColumn);
                existing.targetColumns.add(pkColumn);
            }
        }
        List<DatabaseForeignKeyMapping> result = new ArrayList<>();
        foreignKeys.values().forEach(mutable -> result.add(new DatabaseForeignKeyMapping(
            mutable.sourceColumns, mutable.targetTable, mutable.targetColumns)));
        return result;
    }

    /**
     * PostGIS: geometry_columns liefert Typ und SRID pro Geometrie-Spalte.
     * Falls die View fehlt (kein PostGIS), bleiben die Angaben null und die
     * Validierung behandelt das als nicht verfügbar.
     */
    private void enrichGeometryColumns(Connection connection, String schemaName,
                                       Map<String, Map<String, DatabaseColumnMapping>> columnsByTable) {
        String sql = "SELECT f_table_name, f_geometry_column, type, srid FROM public.geometry_columns "
            + "WHERE lower(f_table_schema) = lower(?)";
        try (java.sql.PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schemaName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("f_table_name");
                    String columnName = rs.getString("f_geometry_column");
                    String type = rs.getString("type");
                    Integer srid = rs.getInt("srid");
                    if (rs.wasNull()) {
                        srid = null;
                    }
                    if (tableName == null || columnName == null) {
                        continue;
                    }
                    for (Map.Entry<String, Map<String, DatabaseColumnMapping>> entry
                        : columnsByTable.entrySet()) {
                        if (!entry.getKey().equalsIgnoreCase(tableName)) {
                            continue;
                        }
                        DatabaseColumnMapping column = entry.getValue().get(columnName);
                        if (column != null) {
                            entry.getValue().put(columnName, new DatabaseColumnMapping(
                                column.columnName(), column.databaseType(), column.nullable(),
                                srid, type));
                        }
                    }
                }
            }
        } catch (SQLException ignored) {
            // geometry_columns nicht verfügbar; Geometrie-Metadaten bleiben null
        }
    }

    private static final class MutableForeignKey {
        private final String targetTable;
        private final List<String> sourceColumns = new ArrayList<>();
        private final List<String> targetColumns = new ArrayList<>();

        private MutableForeignKey(String targetTable) {
            this.targetTable = targetTable;
        }
    }
}

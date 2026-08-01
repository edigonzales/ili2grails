package ch.interlis.generator.reader.ili2db.schema;

import ch.interlis.generator.reader.ili2db.Ili2dbReadContext;
import ch.interlis.generator.reader.sql.QualifiedSqlName;
import ch.interlis.generator.reader.sql.SqlIdentifier;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite-spezifische Schema-Introspektion über PRAGMA-Abfragen.
 */
public final class SqliteSchemaIntrospector implements JdbcSchemaIntrospector {

    @Override
    public JdbcSchemaSnapshot inspect(Ili2dbReadContext context,
                                      java.util.Collection<QualifiedSqlName> tables)
        throws SQLException {
        List<TableSchema> snapshots = new ArrayList<>();
        for (QualifiedSqlName table : tables) {
            String tableName = table.object() != null ? table.object().value() : null;
            if (tableName == null) {
                continue;
            }
            String escapedTable = tableName.replace("'", "''");
            Map<String, ColumnSchema> columns = new LinkedHashMap<>();
            List<PrimaryKeySchema> primaryKeys = new ArrayList<>();
            String sql = "PRAGMA table_info('" + escapedTable + "')";
            try (Statement stmt = context.connection().createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (name == null) {
                        continue;
                    }
                    String typeName = rs.getString("type");
                    int notNull = rs.getInt("notnull");
                    int pk = rs.getInt("pk");
                    columns.put(name, new ColumnSchema(
                        name,
                        null,
                        typeName,
                        notNull == 0,
                        parseColumnSize(typeName),
                        parseDecimalDigits(typeName),
                        rs.getInt("cid")
                    ));
                    if (pk > 0) {
                        primaryKeys.add(new PrimaryKeySchema(name, pk));
                    }
                }
            }
            snapshots.add(new TableSchema(table, columns, primaryKeys, List.of()));
        }
        return new JdbcSchemaSnapshot(snapshots);
    }

    private Integer parseColumnSize(String typeName) {
        if (typeName == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\((\\d+)\\)").matcher(typeName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private Integer parseDecimalDigits(String typeName) {
        if (typeName == null) {
            return null;
        }
        java.util.regex.Matcher matcher =
            java.util.regex.Pattern.compile("\\(\\d+\\s*,\\s*(\\d+)\\)").matcher(typeName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }
}

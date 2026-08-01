package ch.interlis.generator.reader.ili2db.schema;

import ch.interlis.generator.model.GeometryKind;
import ch.interlis.generator.reader.ili2db.Ili2dbDiagnostic;
import ch.interlis.generator.reader.ili2db.Ili2dbDiagnosticCode;
import ch.interlis.generator.reader.ili2db.Ili2dbReadContext;
import ch.interlis.generator.reader.ili2db.Ili2dbSeverity;
import ch.interlis.generator.reader.sql.QualifiedSqlName;
import ch.interlis.generator.reader.sql.SqlIdentifier;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PostGIS-Geometry-Introspektion: eine Batch-Query gegen
 * {@code geometry_columns}, kein {@code Find_SRID} pro Spalte.
 */
public final class PostgisGeometryIntrospector implements GeometryIntrospector {

    @Override
    public GeometrySchemaSnapshot inspect(Ili2dbReadContext context,
                                          Collection<QualifiedSqlName> selectedTables)
        throws SQLException {
        if (context.dialect() != DatabaseDialect.POSTGRESQL) {
            return GeometrySchemaSnapshot.empty();
        }
        String resolvedSchema = context.schema() != null ? context.schema().value() : null;
        if (resolvedSchema == null || resolvedSchema.isBlank()) {
            resolvedSchema = "public";
        }

        String sql =
            "SELECT f_table_schema, f_table_name, f_geometry_column, type, srid, coord_dimension " +
            "FROM geometry_columns " +
            "WHERE lower(f_table_schema) = lower(?)"
        ;
        Map<String, GeometryColumnSchema> byKey = new LinkedHashMap<>();
        try (PreparedStatement stmt = context.connection().prepareStatement(sql)) {
            stmt.setString(1, resolvedSchema);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("f_table_name");
                    String columnName = rs.getString("f_geometry_column");
                    String rawType = rs.getString("type");
                    Integer srid = rs.getInt("srid");
                    if (rs.wasNull()) {
                        srid = null;
                    }
                    Integer coordDimension = rs.getInt("coord_dimension");
                    if (rs.wasNull()) {
                        coordDimension = null;
                    }
                    if (tableName == null || columnName == null) {
                        continue;
                    }
                    String normalizedType = normalizeGeometryTypeSuffix(rawType);
                    boolean hasZ = normalizedType.endsWith("Z") || normalizedType.endsWith("ZM");
                    boolean hasM = normalizedType.endsWith("M") || normalizedType.endsWith("ZM");
                    byKey.put((tableName + "." + columnName).toLowerCase(Locale.ROOT),
                        new GeometryColumnSchema(
                            new QualifiedSqlName(context.schema(), SqlIdentifier.discovered(tableName)),
                            columnName,
                            GeometryKind.from(rawType),
                            srid != null && srid > 0 ? srid : null,
                            hasZ,
                            hasM
                        ));
                }
            }
        } catch (SQLException e) {
            return GeometrySchemaSnapshot.unavailable();
        }

        List<GeometryColumnSchema> columns = new ArrayList<>();
        for (QualifiedSqlName selected : selectedTables) {
            String tableName = selected.object() != null ? selected.object().value() : null;
            if (tableName == null) {
                continue;
            }
            for (Map.Entry<String, GeometryColumnSchema> entry : byKey.entrySet()) {
                if (entry.getKey().startsWith((tableName + ".").toLowerCase(Locale.ROOT))) {
                    columns.add(entry.getValue());
                }
            }
        }
        return new GeometrySchemaSnapshot(columns, true);
    }

    private String normalizeGeometryTypeSuffix(String rawKind) {
        if (rawKind == null) {
            return "";
        }
        return rawKind.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
    }
}

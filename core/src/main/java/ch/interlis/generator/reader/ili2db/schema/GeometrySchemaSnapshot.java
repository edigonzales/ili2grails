package ch.interlis.generator.reader.ili2db.schema;

import ch.interlis.generator.reader.sql.QualifiedSqlName;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Immutable Geometry-Snapshot.
 */
public record GeometrySchemaSnapshot(
    List<GeometryColumnSchema> columns,
    boolean metadataAvailable
) {

    public GeometrySchemaSnapshot {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    public static GeometrySchemaSnapshot unavailable() {
        return new GeometrySchemaSnapshot(List.of(), false);
    }

    public static GeometrySchemaSnapshot empty() {
        return new GeometrySchemaSnapshot(List.of(), true);
    }

    public Optional<GeometryColumnSchema> column(QualifiedSqlName table, String columnName) {
        return columns.stream()
            .filter(column -> column.table().equals(table))
            .filter(column -> column.columnName().equalsIgnoreCase(columnName))
            .findFirst();
    }

    public Collection<GeometryColumnSchema> allColumns() {
        return columns;
    }
}

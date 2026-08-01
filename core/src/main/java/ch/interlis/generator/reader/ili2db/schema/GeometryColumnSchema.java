package ch.interlis.generator.reader.ili2db.schema;

import ch.interlis.generator.model.GeometryKind;
import ch.interlis.generator.reader.sql.QualifiedSqlName;

/**
 * Geometry-Spalten-Snapshot aus {@code geometry_columns}.
 */
public record GeometryColumnSchema(
    QualifiedSqlName table,
    String columnName,
    GeometryKind kind,
    Integer srid,
    Boolean hasZ,
    Boolean hasM
) {
}

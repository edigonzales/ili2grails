package ch.interlis.generator.reader.ili2db.schema;

import ch.interlis.generator.reader.ili2db.Ili2dbReadContext;
import ch.interlis.generator.reader.sql.QualifiedSqlName;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Geometry-Introspektion (batchweise, nicht pro Spalte).
 */
public interface GeometryIntrospector {

    GeometrySchemaSnapshot inspect(Ili2dbReadContext context,
                                   Collection<QualifiedSqlName> selectedTables)
        throws SQLException;
}

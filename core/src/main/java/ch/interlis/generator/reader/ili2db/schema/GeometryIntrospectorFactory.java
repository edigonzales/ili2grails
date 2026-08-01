package ch.interlis.generator.reader.ili2db.schema;

/**
 * Erzeugt die passende Geometry-Introspektion für den Dialekt.
 */
public final class GeometryIntrospectorFactory {

    public GeometryIntrospector forDialect(DatabaseDialect dialect) {
        if (dialect == DatabaseDialect.POSTGRESQL) {
            return new PostgisGeometryIntrospector();
        }
        return (context, selectedTables) -> GeometrySchemaSnapshot.empty();
    }
}

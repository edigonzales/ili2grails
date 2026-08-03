package ch.interlis.generator.reader.ili2db.schema;

/**
 * Erzeugt die passende Geometry-Introspektion für den Dialekt.
 */
public final class GeometryIntrospectorFactory {

    public GeometryIntrospector forDialect(DatabaseDialect dialect) {
        if (dialect == DatabaseDialect.POSTGRESQL) {
            return new PostgisGeometryIntrospector();
        }
        // Kein PostGIS: Geometrie-Metadaten sind nicht verfügbar; der
        // Coordinator erzeugt dafür die WARNING GEOMETRY_METADATA_UNAVAILABLE.
        return (context, selectedTables) -> GeometrySchemaSnapshot.unavailable();
    }
}

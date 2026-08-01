package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogSnapshot;
import ch.interlis.generator.reader.ili2db.schema.DatabaseDialect;
import ch.interlis.generator.reader.ili2db.schema.GeometrySchemaSnapshot;
import ch.interlis.generator.reader.ili2db.schema.JdbcSchemaSnapshot;
import ch.interlis.generator.reader.sql.SqlIdentifier;
import ch.interlis.generator.reader.sql.SqlIdentifierRenderer;

import java.sql.Connection;
import java.util.List;
import java.util.Objects;

/**
 * Kontext eines ili2db-Lesedurchgangs: Verbindung, Auswahl, Schema,
 * Identifier-Rendering und Fehlerpolitik.
 */
public record Ili2dbReadContext(
    Connection connection,
    ModelSelection modelSelection,
    SqlIdentifier schema,
    SqlIdentifierRenderer identifiers,
    DatabaseDialect dialect,
    Ili2dbFailurePolicy failurePolicy
) {

    public Ili2dbReadContext {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(modelSelection, "modelSelection");
        Objects.requireNonNull(identifiers, "identifiers");
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
    }
}

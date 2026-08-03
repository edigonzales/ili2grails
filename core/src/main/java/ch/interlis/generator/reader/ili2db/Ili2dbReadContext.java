package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.reader.ili2db.schema.DatabaseDialect;
import ch.interlis.generator.reader.sql.SqlIdentifier;
import ch.interlis.generator.reader.sql.SqlIdentifierRenderer;

import java.sql.Connection;
import java.util.Objects;

/**
 * Technische Ausführungsumgebung eines ili2db-Lesedurchgangs: Verbindung,
 * Schema, Identifier-Rendering und Dialekt.
 *
 * <p>Enthält bewusst keine fachliche Modellauswahl. Diese wird dem
 * Coordinator direkt übergeben.</p>
 */
public record Ili2dbReadContext(
    Connection connection,
    SqlIdentifier schema,
    SqlIdentifierRenderer identifiers,
    DatabaseDialect dialect
) {

    public Ili2dbReadContext {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(identifiers, "identifiers");
        Objects.requireNonNull(dialect, "dialect");
    }
}

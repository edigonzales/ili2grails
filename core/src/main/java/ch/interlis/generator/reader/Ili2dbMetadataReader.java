package ch.interlis.generator.reader;

import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.ili2db.Ili2dbFailurePolicy;
import ch.interlis.generator.reader.ili2db.Ili2dbReadContext;
import ch.interlis.generator.reader.ili2db.Ili2dbReadCoordinator;
import ch.interlis.generator.reader.ili2db.Ili2dbReadRequest;
import ch.interlis.generator.reader.ili2db.Ili2dbReadResult;
import ch.interlis.generator.reader.ili2db.schema.DatabaseDialect;
import ch.interlis.generator.reader.ili2db.schema.DatabaseDialectDetector;
import ch.interlis.generator.reader.sql.SqlIdentifier;
import ch.interlis.generator.reader.sql.SqlIdentifierRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Fassade für das Lesen von Metadaten aus den ili2db Metatabellen einer
 * Datenbank.
 *
 * <p>Die eigentliche Zerlegung (Katalog, Schema, Geometry, Enum, Assembly)
 * liegt im {@code reader.ili2db}-Paket; diese Klasse delegiert an den
 * {@link Ili2dbReadCoordinator} und bewahrt die Kompatibilitäts-API
 * ({@link #create(Connection, String)}, {@link #readMetadata(ModelSelection)}).</p>
 *
 * <p>Alle dynamischen Schema-, Tabellen- und Spaltennamen werden als
 * {@link SqlIdentifier} modelliert und beim SQL-Aufbau über den
 * {@link SqlIdentifierRenderer} gequotet. Es gibt keine {@code {schema}}-Ersetzung
 * und keine ungeprüfte String-Konkatenation dynamischer Identifier.</p>
 */
public class Ili2dbMetadataReader {

    private static final Logger logger = LoggerFactory.getLogger(Ili2dbMetadataReader.class);

    private final Connection connection;
    private final SqlIdentifier schema;
    private final SqlIdentifierRenderer identifierRenderer;

    /**
     * Kompatibilitäts-Konstruktor ohne SQLException. Die Renderer-Initialisierung
     * läuft über die JDBC-Metadaten; bei nicht lesbaren Metadaten wird ein
     * warnender Fallback auf PostgreSQL-Quoting verwendet.
     *
     * <p>Bevorzugt ist die Factory {@link #create(Connection, String)}, die Fehler
     * strikt weiterreicht.</p>
     */
    public Ili2dbMetadataReader(Connection connection, String schemaName) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.schema = toSchemaIdentifier(schemaName);
        this.identifierRenderer = defaultRenderer(connection);
    }

    private Ili2dbMetadataReader(Connection connection,
                                 SqlIdentifier schema,
                                 SqlIdentifierRenderer identifierRenderer) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.schema = schema;
        this.identifierRenderer = Objects.requireNonNull(identifierRenderer, "identifierRenderer");
    }

    /**
     * Factory mit strikter Renderer-Initialisierung über JDBC-Metadaten.
     */
    public static Ili2dbMetadataReader create(Connection connection, String schemaName)
            throws SQLException {
        return new Ili2dbMetadataReader(
            Objects.requireNonNull(connection, "connection"),
            toSchemaIdentifier(schemaName),
            SqlIdentifierRenderer.from(connection.getMetaData())
        );
    }

    private static SqlIdentifier toSchemaIdentifier(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            return null;
        }
        return SqlIdentifier.userSupplied(schemaName);
    }

    private static SqlIdentifierRenderer defaultRenderer(Connection connection) {
        try {
            return SqlIdentifierRenderer.from(connection.getMetaData());
        } catch (SQLException e) {
            logger.warn("Could not read identifier quote string from JDBC metadata; "
                + "falling back to PostgreSQL quoting ('\"')", e);
            return SqlIdentifierRenderer.fromQuoteString("\"");
        }
    }

    /**
     * Kompatibilitäts-API: liest nur das Root-Modell
     * ({@link ModelSelection#rootOnly(String)}).
     */
    public ModelMetadata readMetadata(String modelName) throws SQLException {
        return readMetadata(ModelSelection.rootOnly(modelName));
    }

    /**
     * Liest die kompletten Metadaten für die übergebene Modellauswahl.
     *
     * <p>Gelesen wird nur die Schnittmenge aus {@link ModelSelection#includedModelNames()}
     * und den in {@code t_ili2db_model} verfügbaren Modellen. Unabhängige Modelle des
     * Schemas werden nie hinzugefügt. Das Root-Modell muss in der Datenbank vorhanden
     * sein.</p>
     */
    public ModelMetadata readMetadata(ModelSelection selection) throws SQLException {
        Objects.requireNonNull(selection, "selection");
        logger.info("Reading ili2db metadata for selection: {} -> {}",
            selection.rootModelName(), selection.includedModelNames());

        DatabaseDialect dialect = new DatabaseDialectDetector().detect(connection.getMetaData());
        Ili2dbReadContext context = new Ili2dbReadContext(
            connection, selection, schema, identifierRenderer, dialect,
            Ili2dbFailurePolicy.STRICT);
        Ili2dbReadRequest request = Ili2dbReadRequest.strict(selection,
            schema != null ? schema.value() : null);

        Ili2dbReadResult result = new Ili2dbReadCoordinator().read(context, request);
        result.throwIfBlocking();
        return result.metadata();
    }
}

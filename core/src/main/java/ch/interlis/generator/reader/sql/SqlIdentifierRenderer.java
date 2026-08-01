package ch.interlis.generator.reader.sql;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Rendert SQL-Identifier mit korrektem Quoting für die konkrete Datenbank.
 *
 * <p>Der Quote-String wird über {@link DatabaseMetaData#getIdentifierQuoteString()}
 * ermittelt. Enthaltene Quotezeichen werden verdoppelt. Ein leerer Quote-String
 * (Datenbank ohne Quoting) wird korrekt behandelt: Der Identifier wird unverändert
 * zurückgegeben.</p>
 */
public final class SqlIdentifierRenderer {

    private final String quote;

    private SqlIdentifierRenderer(String quote) {
        this.quote = quote;
    }

    /**
     * Erzeugt einen Renderer aus den JDBC-Metadaten der Verbindung.
     */
    public static SqlIdentifierRenderer from(DatabaseMetaData metadata) throws SQLException {
        Objects.requireNonNull(metadata, "metadata");
        String quoteString = metadata.getIdentifierQuoteString();
        return new SqlIdentifierRenderer(quoteString == null ? "" : quoteString);
    }

    /**
     * Erzeugt einen Renderer mit explizitem Quote-String (z.&nbsp;B. {@code "\""} für
     * PostgreSQL/H2). Nützlich für Tests und Datenbanken ohne JDBC-Metadaten.
     */
    public static SqlIdentifierRenderer fromQuoteString(String quoteString) {
        return new SqlIdentifierRenderer(quoteString == null ? "" : quoteString);
    }

    /**
     * Renderer ohne Quoting (entspricht einer Datenbank mit leerem Quote-String).
     */
    public static SqlIdentifierRenderer withoutQuoting() {
        return new SqlIdentifierRenderer("");
    }

    /**
     * Quotet einen einzelnen Identifier. Liefert den Rohwert, wenn kein Quoting
     * benötigt wird oder die Datenbank keinen Quote-String kennt.
     */
    public String quote(SqlIdentifier identifier) {
        Objects.requireNonNull(identifier, "identifier");
        if (quote.isEmpty() || !identifier.requiresQuoting()) {
            return identifier.value();
        }
        String escaped = identifier.value().replace(quote, quote + quote);
        return quote + escaped + quote;
    }

    /**
     * Qualifiziert Schema und Objekt. Ein {@code null}-Schema liefert nur das Objekt.
     * Es wird nie ungeprüft {@code schema + "." + table} konkateniert.
     */
    public String qualify(SqlIdentifier schema, SqlIdentifier object) {
        Objects.requireNonNull(object, "object");
        if (schema == null) {
            return quote(object);
        }
        return quote(schema) + "." + quote(object);
    }
}

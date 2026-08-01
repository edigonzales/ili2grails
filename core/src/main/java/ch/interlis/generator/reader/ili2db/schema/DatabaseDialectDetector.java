package ch.interlis.generator.reader.ili2db.schema;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Ermittelt den {@link DatabaseDialect} einmalig aus den JDBC-Metadaten.
 */
public final class DatabaseDialectDetector {

    public DatabaseDialect detect(DatabaseMetaData metadata) throws SQLException {
        String productName = metadata.getDatabaseProductName();
        if (productName == null) {
            return DatabaseDialect.OTHER;
        }
        String normalized = productName.toLowerCase(Locale.ROOT);
        if (normalized.contains("postgresql")) {
            return DatabaseDialect.POSTGRESQL;
        }
        if (normalized.contains("h2")) {
            return DatabaseDialect.H2;
        }
        if (normalized.contains("sqlite")) {
            return DatabaseDialect.SQLITE;
        }
        return DatabaseDialect.OTHER;
    }
}

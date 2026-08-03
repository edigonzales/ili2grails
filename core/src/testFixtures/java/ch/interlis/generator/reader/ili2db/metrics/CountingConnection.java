package ch.interlis.generator.reader.ili2db.metrics;

import java.sql.Connection;

/**
 * Gezählte JDBC-Verbindung; erzeugt via {@link CountingJdbcProxy#wrap}.
 */
public interface CountingConnection extends Connection {

    JdbcInvocationSummary snapshot();
}

package ch.interlis.generator.reader.ili2db.schema;

import ch.interlis.generator.reader.ili2db.Ili2dbReadContext;
import ch.interlis.generator.reader.sql.QualifiedSqlName;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Schema-Introspektion tabellenweise; keine Spalten-Introspektion pro Attribut.
 */
public interface JdbcSchemaIntrospector {

    JdbcSchemaSnapshot inspect(Ili2dbReadContext context,
                               Collection<QualifiedSqlName> tables) throws SQLException;
}

package ch.interlis.generator.reader.ili2db.schema;

import ch.interlis.generator.reader.ili2db.Ili2dbReadContext;
import ch.interlis.generator.reader.sql.QualifiedSqlName;
import ch.interlis.generator.reader.sql.SqlIdentifier;
import ch.interlis.generator.reader.sql.SqlIdentifierRenderer;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * SQLite-Schema-Introspektion über PRAGMA-Abfragen.
 */
class SqliteSchemaIntrospectorTest {

    @Test
    void inspectsColumnsAndPrimaryKeysViaPragma() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE sample ("
                    + "t_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name VARCHAR(100) NOT NULL,"
                    + "amount DECIMAL(10,2))");
            }

            JdbcSchemaSnapshot snapshot = new SqliteSchemaIntrospector().inspect(
                context(connection),
                List.of(new QualifiedSqlName(null, SqlIdentifier.discovered("sample"))));

            assertThat(snapshot.tableByRawName("sample")).isPresent();
            TableSchema sample = snapshot.tableByRawName("sample").get();
            assertThat(sample.column("name")).isPresent();
            ColumnSchema name = sample.column("name").get();
            assertThat(name.databaseTypeName()).containsIgnoringCase("varchar");
            assertThat(name.nullable()).isFalse();
            assertThat(name.size()).isEqualTo(100);
            assertThat(sample.isPrimaryKey("t_id")).isTrue();
            assertThat(sample.isPrimaryKey("name")).isFalse();
            ColumnSchema amount = sample.column("amount").get();
            assertThat(amount.size()).isEqualTo(10);
            assertThat(amount.decimalDigits()).isEqualTo(2);
        }
    }

    private Ili2dbReadContext context(Connection connection) throws Exception {
        return new Ili2dbReadContext(
            connection,
            null,
            SqlIdentifierRenderer.from(connection.getMetaData()),
            DatabaseDialect.SQLITE
        );
    }
}

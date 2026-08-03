package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.reader.ili2db.schema.DatabaseDialect;
import ch.interlis.generator.reader.sql.SqlIdentifierRenderer;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Enum-Schicht: Werte werden einmal pro Tabelle und Lauf gelesen;
 * unlesbare Tabellen erzeugen eine non-blocking Diagnostik.
 */
class Ili2dbEnumReaderTest {

    @Test
    void readsValuesAndCachesPerTablePerRun() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:enum_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE sample_status ("
                    + "ilicode VARCHAR(100), dispname VARCHAR(100), seq INT)");
                stmt.execute("INSERT INTO sample_status VALUES ('a', 'A', 0)");
                stmt.execute("INSERT INTO sample_status VALUES ('b', 'B', 1)");
            }

            Ili2dbEnumReader reader = new Ili2dbEnumReader();
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            List<EnumMetadata.EnumValue> first =
                reader.valuesOf(context(connection), "sample_status", diagnostics);
            List<EnumMetadata.EnumValue> second =
                reader.valuesOf(context(connection), "sample_status", diagnostics);

            assertThat(first).extracting(v -> v.getIliCode()).containsExactly("a", "b");
            assertThat(second).isSameAs(first);
            assertThat(diagnostics).isEmpty();
            assertThat(reader.cachedValues()).hasSize(1);
        }
    }

    @Test
    void missingEnumTableProducesWarningNotFailure() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:enum_missing_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            Ili2dbEnumReader reader = new Ili2dbEnumReader();
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();

            List<EnumMetadata.EnumValue> values =
                reader.valuesOf(context(connection), "no_such_table", diagnostics);

            assertThat(values).isEmpty();
            assertThat(diagnostics)
                .extracting(Ili2dbDiagnostic::code)
                .contains(Ili2dbDiagnosticCode.ENUM_TABLE_UNREADABLE);
            assertThat(diagnostics).noneMatch(Ili2dbDiagnostic::isBlocking);
        }
    }

    private Ili2dbReadContext context(Connection connection) throws Exception {
        return new Ili2dbReadContext(
            connection,
            null,
            SqlIdentifierRenderer.from(connection.getMetaData()),
            DatabaseDialect.H2
        );
    }
}

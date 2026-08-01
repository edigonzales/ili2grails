package ch.interlis.generator.reader.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SqlIdentifierRendererTest {

    private static final SqlIdentifierRenderer POSTGRES =
        SqlIdentifierRenderer.fromQuoteString("\"");

    private static final SqlIdentifierRenderer H2 =
        SqlIdentifierRenderer.fromQuoteString("\"");

    @Test
    void postgresQuoteQuotesUppercaseSchema() {
        SqlIdentifier schema = SqlIdentifier.userSupplied("MySchema");
        assertThat(POSTGRES.quote(schema)).isEqualTo("\"MySchema\"");
    }

    @Test
    void postgresQuoteQuotesHyphenatedSchema() {
        SqlIdentifier schema = SqlIdentifier.userSupplied("my-schema");
        assertThat(POSTGRES.quote(schema)).isEqualTo("\"my-schema\"");
    }

    @Test
    void postgresQuoteLeavesPlainLowercaseUnquoted() {
        assertThat(POSTGRES.quote(SqlIdentifier.userSupplied("public"))).isEqualTo("public");
        assertThat(POSTGRES.quote(SqlIdentifier.userSupplied("_private"))).isEqualTo("_private");
    }

    @Test
    void h2QuoteIsUsedForH2() {
        assertThat(H2.quote(SqlIdentifier.userSupplied("MySchema"))).isEqualTo("\"MySchema\"");
    }

    @Test
    void internalConstantsNeverQuoted() {
        assertThat(POSTGRES.quote(SqlIdentifier.internal("t_ili2db_settings")))
            .isEqualTo("t_ili2db_settings");
    }

    @Test
    void discoveredIdentifierWithSpaceIsQuoted() {
        SqlIdentifier identifier = SqlIdentifier.discovered("Fläche 1");
        assertThat(POSTGRES.quote(identifier)).isEqualTo("\"Fläche 1\"");
    }

    @Test
    void discoveredIdentifierWithQuoteCharIsEscaped() {
        SqlIdentifier identifier = SqlIdentifier.discovered("weird\"name");
        assertThat(POSTGRES.quote(identifier)).isEqualTo("\"weird\"\"name\"");
    }

    @Test
    void blankQuoteStringReturnsRawValue() {
        SqlIdentifierRenderer noQuoting = SqlIdentifierRenderer.withoutQuoting();
        assertThat(noQuoting.quote(SqlIdentifier.userSupplied("MySchema"))).isEqualTo("MySchema");
        assertThat(noQuoting.quote(SqlIdentifier.discovered("a b"))).isEqualTo("a b");
    }

    @Test
    void qualifyCombinesSchemaAndObjectWithQuoting() {
        SqlIdentifier schema = SqlIdentifier.userSupplied("MySchema");
        SqlIdentifier table = SqlIdentifier.internal("t_ili2db_settings");
        assertThat(POSTGRES.qualify(schema, table)).isEqualTo("\"MySchema\".t_ili2db_settings");
    }

    @Test
    void qualifyWithNullSchemaReturnsOnlyObject() {
        SqlIdentifier table = SqlIdentifier.internal("t_ili2db_settings");
        assertThat(POSTGRES.qualify(null, table)).isEqualTo("t_ili2db_settings");
    }

    @Test
    void uppercaseSchemaKeepsCase() {
        SqlIdentifier schema = SqlIdentifier.userSupplied("MySchema");
        assertThat(POSTGRES.quote(schema)).contains("MySchema");
    }

    @Test
    void rendererFromMetadataUsesDatabaseQuote() throws Exception {
        java.sql.Connection connection = java.sql.DriverManager.getConnection(
            "jdbc:h2:mem:renderer_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try {
            SqlIdentifierRenderer renderer = SqlIdentifierRenderer.from(connection.getMetaData());
            assertThat(renderer.quote(SqlIdentifier.userSupplied("MySchema"))).isEqualTo("\"MySchema\"");
        } finally {
            connection.close();
        }
    }

    @Test
    void qualifiedSqlNameRendersViaRenderer() {
        QualifiedSqlName name = new QualifiedSqlName(
            SqlIdentifier.userSupplied("MySchema"),
            SqlIdentifier.internal("t_ili2db_settings")
        );
        assertThat(name.render(POSTGRES)).isEqualTo("\"MySchema\".t_ili2db_settings");
    }

    @Test
    void qualifiedSqlNameWithNullSchemaRendersObjectOnly() {
        QualifiedSqlName name = new QualifiedSqlName(
            null,
            SqlIdentifier.internal("t_ili2db_settings")
        );
        assertThat(name.render(POSTGRES)).isEqualTo("t_ili2db_settings");
    }
}

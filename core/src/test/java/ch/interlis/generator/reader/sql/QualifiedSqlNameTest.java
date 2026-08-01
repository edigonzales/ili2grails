package ch.interlis.generator.reader.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QualifiedSqlNameTest {

    private static final SqlIdentifierRenderer RENDERER =
        SqlIdentifierRenderer.fromQuoteString("\"");

    @Test
    void rendersQualifiedName() {
        QualifiedSqlName name = new QualifiedSqlName(
            SqlIdentifier.userSupplied("MySchema"),
            SqlIdentifier.internal("t_ili2db_settings")
        );
        assertThat(name.render(RENDERER)).isEqualTo("\"MySchema\".t_ili2db_settings");
    }

    @Test
    void rendersObjectOnlyWithoutSchema() {
        QualifiedSqlName name = new QualifiedSqlName(
            null,
            SqlIdentifier.internal("t_ili2db_settings")
        );
        assertThat(name.render(RENDERER)).isEqualTo("t_ili2db_settings");
    }

    @Test
    void rejectsNullObject() {
        assertThatThrownBy(() -> new QualifiedSqlName(null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullRenderer() {
        QualifiedSqlName name = new QualifiedSqlName(
            null,
            SqlIdentifier.internal("t_ili2db_settings")
        );
        assertThatThrownBy(() -> name.render(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void quoteCharactersInSegmentsAreEscaped() {
        QualifiedSqlName name = new QualifiedSqlName(
            null,
            SqlIdentifier.discovered("tab\"le")
        );
        assertThat(name.render(RENDERER)).isEqualTo("\"tab\"\"le\"");
    }
}

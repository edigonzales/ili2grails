package ch.interlis.generator.reader.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class SqlIdentifierTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "public",
        "MySchema",
        "my-schema",
        "_private",
        "schema$1",
        "MySchema2"
    })
    void acceptsValidUserSuppliedIdentifiers(String value) {
        SqlIdentifier identifier = SqlIdentifier.userSupplied(value);
        assertThat(identifier.value()).isEqualTo(value);
        assertThat(identifier.kind()).isEqualTo(SqlIdentifierKind.USER_SUPPLIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "public.other",
        "public;DROP TABLE x",
        "a--comment",
        "a/*x*/",
        "x*/y",
        "",
        "   ",
        "1abc",
        "-abc",
        "a b",
        "a'b"
    })
    void rejectsInvalidUserSuppliedIdentifiers(String value) {
        assertThatThrownBy(() -> SqlIdentifier.userSupplied(value))
            .isInstanceOf(InvalidSqlIdentifierException.class);
    }

    @Test
    void rejectsNullUserSuppliedIdentifier() {
        assertThatThrownBy(() -> SqlIdentifier.userSupplied(null))
            .isInstanceOf(InvalidSqlIdentifierException.class);
    }

    @Test
    void rejectsNulUserSuppliedIdentifier() {
        assertThatThrownBy(() -> SqlIdentifier.userSupplied("ab\0cd"))
            .isInstanceOf(InvalidSqlIdentifierException.class);
    }

    @Test
    void rejectsControlCharacters() {
        assertThatThrownBy(() -> SqlIdentifier.userSupplied("ab\ncd"))
            .isInstanceOf(InvalidSqlIdentifierException.class);
    }

    @Test
    void rejectsOverlongIdentifier() {
        String longName = "x".repeat(129);
        assertThatThrownBy(() -> SqlIdentifier.userSupplied(longName))
            .isInstanceOf(InvalidSqlIdentifierException.class);
    }

    @Test
    void acceptsMaxLengthIdentifier() {
        String longName = "x".repeat(128);
        assertThat(SqlIdentifier.userSupplied(longName).value()).isEqualTo(longName);
    }

    @Test
    void discoveredIdentifiersAllowWideCharacterSet() {
        SqlIdentifier identifier = SqlIdentifier.discovered("Fläche 1");
        assertThat(identifier.value()).isEqualTo("Fläche 1");
        assertThat(identifier.kind()).isEqualTo(SqlIdentifierKind.DATABASE_DISCOVERED);
        assertThat(identifier.requiresQuoting()).isTrue();
    }

    @Test
    void rejectsBlankDiscoveredIdentifier() {
        assertThatThrownBy(() -> SqlIdentifier.discovered("  "))
            .isInstanceOf(InvalidSqlIdentifierException.class);
    }

    @Test
    void rejectsNulDiscoveredIdentifier() {
        assertThatThrownBy(() -> SqlIdentifier.discovered("a\0b"))
            .isInstanceOf(InvalidSqlIdentifierException.class);
    }

    @Test
    void acceptsInternalConstants() {
        SqlIdentifier identifier = SqlIdentifier.internal("t_ili2db_settings");
        assertThat(identifier.value()).isEqualTo("t_ili2db_settings");
        assertThat(identifier.requiresQuoting()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1abc", "a.b", "a-b", "a b", "a$b", ""})
    void rejectsInvalidInternalConstants(String value) {
        assertThatThrownBy(() -> SqlIdentifier.internal(value))
            .isInstanceOf(InvalidSqlIdentifierException.class);
    }

    @Test
    void userSuppliedQuotingRules() {
        assertThat(SqlIdentifier.userSupplied("public").requiresQuoting()).isFalse();
        assertThat(SqlIdentifier.userSupplied("my_schema").requiresQuoting()).isFalse();
        assertThat(SqlIdentifier.userSupplied("MySchema").requiresQuoting()).isTrue();
        assertThat(SqlIdentifier.userSupplied("my-schema").requiresQuoting()).isTrue();
        assertThat(SqlIdentifier.userSupplied("_private").requiresQuoting()).isFalse();
        assertThat(SqlIdentifier.userSupplied("schema$1").requiresQuoting()).isTrue();
    }

    @Test
    void discoveredIdentifierQuotingRules() {
        assertThat(SqlIdentifier.discovered("addressstatus").requiresQuoting()).isFalse();
        assertThat(SqlIdentifier.discovered("AddressStatus").requiresQuoting()).isTrue();
        assertThat(SqlIdentifier.discovered("Fläche 1").requiresQuoting()).isTrue();
    }

    @Test
    void equalsAndHashCodeUseValueAndKind() {
        assertThat(SqlIdentifier.userSupplied("public"))
            .isEqualTo(SqlIdentifier.userSupplied("public"))
            .isNotEqualTo(SqlIdentifier.discovered("public"));
        assertThat(SqlIdentifier.userSupplied("public").hashCode())
            .isEqualTo(SqlIdentifier.userSupplied("public").hashCode());
    }
}

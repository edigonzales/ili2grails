package ch.interlis.generator.grails.source;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroovySourceWriterTest {

    private final GroovySourceWriter writer = new GroovySourceWriter();

    @Test
    void escapesAllGroovySpecialCharacters() {
        assertThat(writer.stringLiteral("plain")).isEqualTo("'plain'");
        assertThat(writer.stringLiteral(null)).isEqualTo("null");
        assertThat(writer.stringLiteral("a'b\\c\nd\re\tf$g"))
            .isEqualTo("'a\\'b\\\\c\\nd\\re\\tf\\$g'");
    }

    @Test
    void rendersEnumsAndNulls() {
        assertThat(writer.enumLiteral(DemoEnum.class, DemoEnum.A)).isEqualTo("DemoEnum.A");
        assertThat(writer.enumLiteral(DemoEnum.class, null)).isEqualTo("null");
        assertThat(writer.nullableInteger(null)).isEqualTo("null");
        assertThat(writer.nullableInteger(-1)).isEqualTo("-1");
        assertThat(writer.nullableInteger(7)).isEqualTo("7");
        assertThat(writer.nullableBoolean(null)).isEqualTo("null");
        assertThat(writer.nullableBoolean(true)).isEqualTo("true");
    }

    @Test
    void rendersStringListsDeterministically() {
        assertThat(writer.listOfStrings(List.of())).isEqualTo("[]");
        assertThat(writer.listOfStrings(List.of("a", "b'c")))
            .isEqualTo("['a', 'b\\'c']");
    }

    private enum DemoEnum {
        A,
        B
    }
}

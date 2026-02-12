package ch.interlis.generator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataReaderAppCliTest {

    @Test
    void parsesNewGrailsOptions() throws Exception {
        Object cliOptions = parseArgs(
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--grails-output", "generated",
            "--grails-ui-theme", "carbon",
            "--grails-map-editor", "openlayers",
            "--grails-default-srid", "2056"
        );

        assertThat(cliOptions).isNotNull();
        assertThat(readField(cliOptions, "grailsUiTheme")).isEqualTo("carbon");
        assertThat(readField(cliOptions, "grailsMapEditor")).isEqualTo("openlayers");
        assertThat(readField(cliOptions, "grailsDefaultSrid")).isEqualTo(2056);
    }

    @Test
    void rejectsInvalidUiTheme() throws Exception {
        Object cliOptions = parseArgs(
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--grails-output", "generated",
            "--grails-ui-theme", "modernish"
        );
        assertThat(cliOptions).isNull();
    }

    @Test
    void resolvesMapEditorDefaultsBasedOnTheme() throws Exception {
        Object defaultOptions = parseArgs(
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--grails-output", "generated"
        );
        Object carbonOptions = parseArgs(
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--grails-output", "generated",
            "--grails-ui-theme", "carbon"
        );

        Method resolveUiTheme = MetadataReaderApp.class.getDeclaredMethod("resolveUiTheme", defaultOptions.getClass());
        Method resolveMapEditor = MetadataReaderApp.class.getDeclaredMethod(
            "resolveMapEditor",
            defaultOptions.getClass(),
            String.class
        );
        resolveUiTheme.setAccessible(true);
        resolveMapEditor.setAccessible(true);

        String defaultTheme = (String) resolveUiTheme.invoke(null, defaultOptions);
        String defaultMapEditor = (String) resolveMapEditor.invoke(null, defaultOptions, defaultTheme);

        String carbonTheme = (String) resolveUiTheme.invoke(null, carbonOptions);
        String carbonMapEditor = (String) resolveMapEditor.invoke(null, carbonOptions, carbonTheme);

        assertThat(defaultTheme).isEqualTo("default");
        assertThat(defaultMapEditor).isEqualTo("none");
        assertThat(carbonTheme).isEqualTo("carbon");
        assertThat(carbonMapEditor).isEqualTo("openlayers");
    }

    private Object parseArgs(String... args) throws Exception {
        Method parseArgs = MetadataReaderApp.class.getDeclaredMethod("parseArgs", String[].class);
        parseArgs.setAccessible(true);
        return parseArgs.invoke(null, (Object) args);
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}

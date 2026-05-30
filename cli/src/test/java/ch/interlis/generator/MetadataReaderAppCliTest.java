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
            "--metadata-json", "build/metadata/simple.json",
            "--grails-ui-theme", "bootstrap",
            "--grails-map-editor", "openlayers",
            "--grails-default-srid", "2056"
        );

        assertThat(cliOptions).isNotNull();
        assertThat(readField(cliOptions, "grailsUiTheme")).isEqualTo("bootstrap");
        assertThat(readField(cliOptions, "grailsMapEditor")).isEqualTo("openlayers");
        assertThat(readField(cliOptions, "grailsDefaultSrid")).isEqualTo(2056);
        assertThat(readField(cliOptions, "metadataJsonPath").toString())
            .isEqualTo("build/metadata/simple.json");
    }

    @Test
    void rejectsRemovedCarbonUiTheme() throws Exception {
        Object cliOptions = parseArgs(
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--grails-output", "generated",
            "--grails-ui-theme", "carbon"
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
        Object bootstrapOptions = parseArgs(
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--grails-output", "generated",
            "--grails-ui-theme", "bootstrap"
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

        String bootstrapTheme = (String) resolveUiTheme.invoke(null, bootstrapOptions);
        String bootstrapMapEditor = (String) resolveMapEditor.invoke(null, bootstrapOptions, bootstrapTheme);

        assertThat(defaultTheme).isEqualTo("default");
        assertThat(defaultMapEditor).isEqualTo("none");
        assertThat(bootstrapTheme).isEqualTo("bootstrap");
        assertThat(bootstrapMapEditor).isEqualTo("openlayers");
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

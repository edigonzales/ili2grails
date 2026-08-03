package ch.interlis.generator.grails;

import ch.interlis.generator.grails.project.RuntimeCoordinates;
import ch.interlis.generator.grails.project.plan.TextFileEdit;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsBuildGradleUpdaterTest {

    private static final String BUILD_FILE = """
        buildscript {
            repositories { mavenCentral() }
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            implementation "org.grails:grails-core:7.0.6"
        }
        """;

    @Test
    void defaultThemeAddsOnlyRuntimeAndDatabaseDependencies() {
        GenerationConfig config = GenerationConfig.builder(Path.of("app"), "com.example").build();

        TextFileEdit first = plan(BUILD_FILE, config);
        TextFileEdit second = plan(first.updatedContent(), config);

        assertThat(first.updatedContent())
            .contains("// <ili2grails-runtime-repository>", "mavenLocal()")
            .contains("// <ili2grails-dependencies>")
            .contains(RuntimeCoordinates.ili2grailsRuntime().notation())
            .contains("org.postgresql:postgresql:42.7.7")
            .doesNotContain("jts-core", "hibernate-spatial", "webjars:bootstrap", "webjars.npm:ol", "webjars.npm:proj4");
        assertThat(second.changed()).isFalse();
        assertThat(second.updatedContent()).isEqualTo(first.updatedContent());
    }

    @Test
    void bootstrapOpenlayersGeometryAddsOnlyConfiguredDependencies() {
        GenerationConfig config = GenerationConfig.builder(Path.of("app"), "com.example")
            .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
            .mapEditor(GenerationConfig.MAP_EDITOR_OPENLAYERS)
            .geometryEnabled(true)
            .build();

        String content = plan(BUILD_FILE, config).updatedContent();

        assertThat(content)
            .contains("org.locationtech.jts:jts-core:1.19.0")
            .contains("org.hibernate:hibernate-spatial:5.6.15.Final")
            .contains("org.webjars:bootstrap:5.3.3")
            .contains("org.webjars.npm:ol:9.2.4")
            .contains("org.webjars.npm:proj4:2.11.0");
    }

    @Test
    void migratesKnownLinesAndPreservesDifferentApplicationVersions() {
        String existing = BUILD_FILE.replace(
            "implementation \"org.grails:grails-core:7.0.6\"",
            """
                implementation "org.grails:grails-core:7.0.6"
                implementation "org.webjars:bootstrap:5.3.3"
                implementation "org.webjars.npm:ol:8.0.0"
                implementation "ch.interlis.generator:ili2grails-runtime:9.9.9"""
        );
        GenerationConfig config = GenerationConfig.builder(Path.of("app"), "com.example")
            .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
            .build();

        String content = plan(existing, config).updatedContent();

        assertThat(content).containsOnlyOnce("org.webjars:bootstrap:5.3.3");
        assertThat(content).contains("org.webjars.npm:ol:8.0.0");
        assertThat(content).contains("ili2grails-runtime:9.9.9");
        assertThat(content).containsOnlyOnce(RuntimeCoordinates.ili2grailsRuntime().notation());
    }

    private TextFileEdit plan(String content, GenerationConfig config) {
        return new GrailsBuildGradleUpdater().plan(
            Path.of("build.gradle"), content, config, RuntimeCoordinates.ili2grailsRuntime());
    }
}

package ch.interlis.generator.grails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsApplicationYamlUpdaterTest {

    @Test
    void updatesDevelopmentUrlAndDialect(@TempDir Path tempDir) throws Exception {
        Path yamlPath = tempDir.resolve("application.yml");
        Files.writeString(yamlPath, String.join("\n",
            "---",
            "dataSource:",
            "  driverClassName: org.h2.Driver",
            "  username: sa",
            "  password: sa",
            "environments:",
            "  development:",
            "    dataSource:",
            "      url: jdbc:h2:mem:test",
            "      driverClassName: org.h2.Driver",
            "      dbCreate: create-drop",
            "---",
            "hibernate:",
            "  dialect: org.hibernate.dialect.H2Dialect",
            ""
        ));

        GrailsApplicationYamlUpdater updater = new GrailsApplicationYamlUpdater();
        updater.ensureDevelopmentDataSourceUrl(
            yamlPath,
            "jdbc:postgresql://localhost:5432/testdb?user=postgres&password=secret&dbSchema=ignored",
            "public"
        );

        String updated = Files.readString(yamlPath);
        assertThat(updated).contains("jdbc:postgresql://localhost:5432/testdb?currentSchema=public");
        assertThat(updated).contains("username: \"${DB_USERNAME}\"");
        assertThat(updated).contains("password: \"${DB_PASSWORD}\"");
        assertThat(updated).doesNotContain("user=postgres");
        assertThat(updated).doesNotContain("password=secret");
        assertThat(updated).doesNotContain("dbSchema=ignored");
        assertThat(updated).contains("dbCreate: \"none\"");
        assertThat(updated).contains("org.hibernate.dialect.PostgreSQLDialect");
        assertThat(updated).contains("production:");
        assertThat(updated).contains("url: \"${DB_URL}\"");
        assertThat(updated).contains("username: \"${DB_USERNAME}\"");
        assertThat(updated).contains("password: \"${DB_PASSWORD}\"");
        assertThat(updated).doesNotContain("username: \"sa\"");
        assertThat(updated).doesNotContain("password: \"sa\"");
        assertThat(updated).doesNotContain("org.h2.Driver");
    }

    @Test
    void writesSelectedUiLanguage(@TempDir Path tempDir) throws Exception {
        Path yamlPath = tempDir.resolve("application.yml");
        Files.writeString(yamlPath, "---\n" +
            "grails:\n" +
            "  profile: web\n");

        new GrailsApplicationYamlUpdater().ensureDevelopmentDataSourceUrl(
            yamlPath,
            null,
            null,
            false,
            2056,
            GenerationConfig.LANGUAGE_EN
        );

        assertThat(Files.readString(yamlPath)).contains(
            "ili2grails:", "language: \"en\"", "locale: \"en\"", "locale-resolver: \"fixed\""
        );
    }

    @Test
    void enablesSpatialDialectAndDefaultSrid(@TempDir Path tempDir) throws Exception {
        Path yamlPath = tempDir.resolve("application.yml");
        Files.writeString(yamlPath, String.join("\n",
            "---",
            "environments:",
            "  development:",
            "    dataSource:",
            "      url: jdbc:h2:mem:test",
            "      dbCreate: create-drop",
            "hibernate:",
            "  dialect: org.hibernate.dialect.H2Dialect",
            ""
        ));

        GrailsApplicationYamlUpdater updater = new GrailsApplicationYamlUpdater();
        updater.ensureDevelopmentDataSourceUrl(
            yamlPath,
            "jdbc:postgresql://localhost:5432/testdb",
            "public",
            true,
            2056
        );

        String updated = Files.readString(yamlPath);
        assertThat(updated).contains("org.hibernate.spatial.dialect.postgis.PostgisDialect");
        assertThat(updated).contains("production:");
        assertThat(updated).contains("url: \"${DB_URL}\"");
        assertThat(updated).contains("dbCreate: \"none\"");
        assertThat(updated).contains("defaultSrid: 2056");
    }
}

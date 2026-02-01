package ch.interlis.generator.generator;

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
        updater.ensureDevelopmentDataSourceUrl(yamlPath, "jdbc:sqlite:./testdb.gpkg");

        String updated = Files.readString(yamlPath);
        assertThat(updated).contains("jdbc:sqlite:./testdb.gpkg");
        assertThat(updated).contains("dbCreate: \"none\"");
        assertThat(updated).contains("org.hibernate.dialect.SQLiteDialect");
        assertThat(updated).doesNotContain("org.h2.Driver");
    }
}

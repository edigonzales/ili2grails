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
        updater.ensureDevelopmentDataSourceUrl(yamlPath, "jdbc:postgresql://localhost:5432/testdb");

        String updated = Files.readString(yamlPath);
        assertThat(updated).contains("jdbc:postgresql://localhost:5432/testdb");
        assertThat(updated).contains("dbCreate: \"none\"");
        assertThat(updated).contains("org.hibernate.dialect.PostgreSQLDialect");
        assertThat(updated).contains("username: \"edit\"");
        assertThat(updated).contains("password: \"secret\"");
        assertThat(updated).doesNotContain("org.h2.Driver");
    }
}

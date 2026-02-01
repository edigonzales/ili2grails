package ch.interlis.generator.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsBuildGradleUpdaterTest {

    @Test
    void addsSqliteDependenciesWhenMissing(@TempDir Path tempDir) throws Exception {
        Path buildGradle = tempDir.resolve("build.gradle");
        Files.writeString(buildGradle, String.join("\n",
            "dependencies {",
            "    implementation \"org.grails:grails-core:7.0.6\"",
            "}",
            ""
        ));

        new GrailsBuildGradleUpdater().ensureJtsDependency(buildGradle);

        String updated = Files.readString(buildGradle);
        assertThat(updated).contains("org.locationtech.jts:jts-core");
        assertThat(updated).contains("org.xerial:sqlite-jdbc:3.43.0.0");
        assertThat(updated).contains("hibernate-community-dialects:6.6.41.Final");
    }
}

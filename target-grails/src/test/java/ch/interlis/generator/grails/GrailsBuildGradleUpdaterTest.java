package ch.interlis.generator.grails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsBuildGradleUpdaterTest {

    @Test
    void addsJtsDependencyWhenMissing(@TempDir Path tempDir) throws Exception {
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
        assertThat(updated).contains("org.postgresql:postgresql:42.7.7");
        assertThat(updated).doesNotContain("sqlite-jdbc");
        assertThat(updated).doesNotContain("sqlite-dialect");
        assertThat(updated).doesNotContain("hibernate-spatial");
    }

    @Test
    void addsSpatialDependencyWhenGeometryIsEnabled(@TempDir Path tempDir) throws Exception {
        Path buildGradle = tempDir.resolve("build.gradle");
        Files.writeString(buildGradle, String.join("\n",
            "dependencies {",
            "    implementation \"org.grails:grails-core:7.0.6\"",
            "    implementation \"org.hibernate.orm:hibernate-spatial\"",
            "}",
            ""
        ));

        new GrailsBuildGradleUpdater().ensureDependencies(buildGradle, true);

        String updated = Files.readString(buildGradle);
        assertThat(updated).contains("org.hibernate:hibernate-spatial:5.6.15.Final");
        assertThat(updated).doesNotContain("org.hibernate.orm:hibernate-spatial");
    }
}

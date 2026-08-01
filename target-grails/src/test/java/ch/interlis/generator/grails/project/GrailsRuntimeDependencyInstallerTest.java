package ch.interlis.generator.grails.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsRuntimeDependencyInstallerTest {

    @TempDir
    Path tempDir;

    private static final RuntimeCoordinates COORDINATES = RuntimeCoordinates.ili2grailsRuntime();

    @Test
    void installsManagedDependencyBlockIdempotently() throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            plugins {
                id 'org.grails.grails-web'
            }

            dependencies {
                implementation "org.apache.grails:grails-core"
            }
            """, StandardCharsets.UTF_8);

        GrailsRuntimeDependencyInstaller installer = new GrailsRuntimeDependencyInstaller();
        installer.install(buildFile, COORDINATES);
        installer.install(buildFile, COORDINATES);

        String content = Files.readString(buildFile);
        assertThat(content).contains("// <ili2grails-runtime-dependency>");
        assertThat(content).contains("implementation \"" + COORDINATES.notation() + "\"");
        assertThat(content).contains("// </ili2grails-runtime-dependency>");
        assertThat(content).containsOnlyOnce(COORDINATES.notation());
        assertThat(content).containsOnlyOnce("dependencies {");
    }

    @Test
    void updatesVersionInPlaceWithoutDuplicates() throws Exception {
        Path buildFile = tempDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            dependencies {
                implementation "ch.interlis.generator:ili2grails-runtime:9.9.9"
            }
            """, StandardCharsets.UTF_8);

        GrailsRuntimeDependencyInstaller installer = new GrailsRuntimeDependencyInstaller();
        installer.install(buildFile, COORDINATES);

        String content = Files.readString(buildFile);
        assertThat(content).containsOnlyOnce(COORDINATES.notation());
        assertThat(content).doesNotContain("9.9.9");
    }

    @Test
    void missingBuildFileIsSkipped() throws Exception {
        GrailsRuntimeDependencyInstaller installer = new GrailsRuntimeDependencyInstaller();
        GrailsRuntimeDependencyInstaller.DependencyUpdateResult result =
            installer.install(tempDir.resolve("missing.gradle"), COORDINATES);
        assertThat(result.updated()).isFalse();
    }

    @Test
    void managedBlockMatchesSpecifiedMarkers() {
        String block = GrailsRuntimeDependencyInstaller.managedBlock(COORDINATES);
        assertThat(block)
            .contains("// <ili2grails-runtime-dependency>")
            .contains("// </ili2grails-runtime-dependency>")
            .contains("implementation \"" + COORDINATES.notation() + "\"");
    }
}

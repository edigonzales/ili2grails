package ch.interlis.generator.grails.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyRuntimeScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void classifiesUnmodifiedModifiedAndUnknownFiles() throws Exception {
        Path projectDir = tempDir.resolve("legacy-app");
        Path knownFile = projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisFormSupport.groovy");
        Path modifiedFile = projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisTableModel.groovy");
        Path unknownFile = projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/MyCustomSupport.groovy");
        Files.createDirectories(knownFile.getParent());

        Files.copy(
            LegacyRuntimeScannerTest.class.getClassLoader().getResourceAsStream(
                LegacyRuntimeScanner.LEGACY_RESOURCE_ROOT
                    + "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisFormSupport.groovy"),
            knownFile);
        Files.writeString(modifiedFile, "class InterlisTableModel { /* user change */ }",
            StandardCharsets.UTF_8);
        Files.writeString(unknownFile, "class MyCustomSupport { }", StandardCharsets.UTF_8);

        LegacyRuntimeScanResult result = new LegacyRuntimeScanner().scan(projectDir);

        assertThat(result.knownUnmodifiedFiles()).extracting(match -> match.relativePath().toString())
            .contains("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisFormSupport.groovy");
        assertThat(result.modifiedFiles()).extracting(match -> match.relativePath().toString())
            .contains("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisTableModel.groovy");
        assertThat(result.unknownRuntimeFiles()).contains(
            Path.of("src/main/groovy/ch/interlis/generator/grails/runtime/MyCustomSupport.groovy"));
        assertThat(result.requiresManualIntervention()).isTrue();
    }

    @Test
    void missingLegacyFilesAreIgnored() throws Exception {
        Path projectDir = tempDir.resolve("fresh-app");
        Files.createDirectories(projectDir);

        LegacyRuntimeScanResult result = new LegacyRuntimeScanner().scan(projectDir);

        assertThat(result.knownUnmodifiedFiles()).isEmpty();
        assertThat(result.modifiedFiles()).isEmpty();
        assertThat(result.unknownRuntimeFiles()).isEmpty();
        assertThat(result.requiresManualIntervention()).isFalse();
    }

    @Test
    void legacyIndexResourceIsPresentAndComplete() throws IOException {
        assertThat(LegacyRuntimeScanner.legacyResources()).isNotEmpty();
        for (String resource : LegacyRuntimeScanner.legacyResources()) {
            assertThat(LegacyRuntimeScannerTest.class.getClassLoader()
                .getResource(resource))
                .as(resource)
                .isNotNull();
        }
    }
}

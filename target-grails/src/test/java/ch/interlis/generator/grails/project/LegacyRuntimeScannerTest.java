package ch.interlis.generator.grails.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyRuntimeScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void classifiesUnmodifiedModifiedAndUnknownFiles() throws Exception {
        Path projectDir = tempDir.resolve("legacy-app");
        Path knownFile = projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisMessageSupport.groovy");
        Path modifiedFile = projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisTableModel.groovy");
        Path unknownFile = projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/MyCustomSupport.groovy");
        Files.createDirectories(knownFile.getParent());

        Files.copy(
            LegacyRuntimeScannerTest.class.getClassLoader().getResourceAsStream(
                "legacy-runtime-v1/InterlisMessageSupport.groovy"),
            knownFile);
        Files.writeString(modifiedFile, "class InterlisTableModel { /* user change */ }",
            StandardCharsets.UTF_8);
        Files.writeString(unknownFile, "class MyCustomSupport { }", StandardCharsets.UTF_8);

        LegacyRuntimeScanResult result = new LegacyRuntimeScanner().scan(projectDir);

        assertThat(result.knownUnmodifiedFiles()).extracting(match -> match.relativePath().toString())
            .contains("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisMessageSupport.groovy");
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
    void legacyHashResourceIsPresentAndValid() throws IOException {
        Map<String, Set<String>> hashes = LegacyRuntimeScanner.legacyHashes();

        assertThat(hashes).isNotEmpty()
            .containsKey("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisFormSupport.groovy");
        assertThat(hashes.values()).allSatisfy(values ->
            assertThat(values).allMatch(hash -> hash.matches("[0-9a-f]{64}")));
    }

    @Test
    void parserAllowsMultipleKnownHashesForOnePath() throws Exception {
        String path = "src/main/groovy/example/Legacy.groovy";
        Map<String, Set<String>> hashes = LegacyRuntimeScanner.parseLegacyHashes(
            "a".repeat(64) + "  " + path + "\n"
                + "b".repeat(64) + "  " + path + "\n");

        assertThat(hashes.get(path)).containsExactlyInAnyOrder("a".repeat(64), "b".repeat(64));
    }

    @Test
    void parserRejectsInvalidHashesAndUnsafePaths() {
        assertThatThrownBy(() -> LegacyRuntimeScanner.parseLegacyHashes(
            "not-a-hash  src/main/groovy/Legacy.groovy\n"))
            .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> LegacyRuntimeScanner.parseLegacyHashes(
            "a".repeat(64) + "  ../outside.groovy\n"))
            .isInstanceOf(IOException.class);
    }
}

package ch.interlis.generator.grails.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyRuntimeMigratorTest {

    @TempDir
    Path tempDir;

    private LegacyRuntimeScanResult scan(Path projectDir) throws Exception {
        return new LegacyRuntimeScanner().scan(projectDir);
    }

    @Test
    void strictPolicyDeletesOnlyKnownUnmodifiedFiles() throws Exception {
        Path projectDir = tempDir.resolve("app");
        Path known = projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisFormSupport.groovy");
        Path modified = projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisTableModel.groovy");
        Files.createDirectories(known.getParent());
        Files.copy(
            LegacyRuntimeScannerTest.class.getClassLoader().getResourceAsStream(
                LegacyRuntimeScanner.LEGACY_RESOURCE_ROOT
                    + "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisFormSupport.groovy"),
            known);
        Files.writeString(modified, "class InterlisTableModel { }", StandardCharsets.UTF_8);

        LegacyMigrationResult result = new LegacyRuntimeMigrator().migrate(
            projectDir, scan(projectDir), LegacyMigrationPolicy.STRICT);

        assertThat(result.deletedFiles())
            .contains("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisFormSupport.groovy");
        assertThat(known).doesNotExist();
        assertThat(modified).exists();
        assertThat(result.diagnostics())
            .anyMatch(diagnostic -> diagnostic.code().equals("LEGACY_RUNTIME_MODIFIED"));
    }

    @Test
    void reportOnlyPolicyNeverDeletes() throws Exception {
        Path projectDir = tempDir.resolve("app");
        Path known = projectDir.resolve(
            "grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationQueryService.groovy");
        Files.createDirectories(known.getParent());
        Files.copy(
            LegacyRuntimeScannerTest.class.getClassLoader().getResourceAsStream(
                LegacyRuntimeScanner.LEGACY_RESOURCE_ROOT
                    + "grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationQueryService.groovy"),
            known);

        LegacyMigrationResult result = new LegacyRuntimeMigrator().migrate(
            projectDir, scan(projectDir), LegacyMigrationPolicy.REPORT_ONLY);

        assertThat(result.deletedFiles()).isEmpty();
        assertThat(known).exists();
    }
}

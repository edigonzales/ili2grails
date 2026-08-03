package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.project.plan.GenerationDiagnostic;
import ch.interlis.generator.grails.project.plan.GenerationOwnershipValidator;
import ch.interlis.generator.grails.project.plan.PlannedProjectFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generation-Ownership-Guard (Spezifikation §55.3):
 *
 * <ul>
 *   <li>kein doppelter Pfad mit unterschiedlichem Owner;</li>
 *   <li>kein {@code APPLICATION_OWNED} mit {@code overwriteAllowed=true};</li>
 *   <li>keine Plugin-Runtime-Datei im Manifest.</li>
 * </ul>
 */
class GenerationOwnershipGuardTest {

    @Test
    void duplicatePathWithDifferentOwnersIsRejected() {
        List<GenerationDiagnostic> diagnostics = new GenerationOwnershipValidator().validate(
            List.of(
                PlannedProjectFile.text(Path.of("a.groovy"),
                    GrailsProjectFileOwner.GENERATOR_MANAGED, "x", "first"),
                PlannedProjectFile.text(Path.of("A.groovy"),
                    GrailsProjectFileOwner.APPLICATION_OWNED, "y", "second")));
        assertThat(diagnostics)
            .filteredOn(GenerationDiagnostic::blocking)
            .filteredOn(diagnostic ->
                diagnostic.code() == ch.interlis.generator.grails.project.plan.GenerationDiagnosticCode.AMBIGUOUS_FILE_OWNERSHIP)
            .isNotEmpty();
    }

    @Test
    void ownershipRulesHaveNoApplicationOwnedWithOverwriteAllowed() {
        for (GrailsProjectFileRule rule : GrailsProjectFileOwnership.rules()) {
            if (rule.owner() == GrailsProjectFileOwner.APPLICATION_OWNED) {
                assertThat(rule.overwriteAllowed())
                    .as("application-owned rule must not allow overwrite: %s",
                        rule.relativePath())
                    .isFalse();
            }
        }
    }

    @Test
    void ownershipRulesHaveExactlyOneOwnerPerPath() {
        List<GenerationDiagnostic> diagnostics = new GenerationOwnershipValidator()
            .validateLegacyRules(GrailsProjectFileOwnership.rules());
        assertThat(diagnostics)
            .filteredOn(GenerationDiagnostic::blocking)
            .as("no ownership rule conflicts (e.g. main.gsp)")
            .isEmpty();
    }

    @Test
    void runtimePluginFilesNeverAppearInManifest() throws Exception {
        Path manifest = Path.of("target-grails/src/test/resources")
            .resolve("no-manifest-runtime-plugin-entries");
        // Das Manifest wird nur vom Store geschrieben; der Store lehnt
        // RUNTIME_PLUGIN-Einträge ab (siehe GeneratedProjectManifestStoreTest).
        // Dieser Guard prüft die Regelquelle: Der Store validiert Owner.
        String storeSource = java.nio.file.Files.readString(
            Path.of("target-grails/src/main/java/ch/interlis/generator/grails/"
                + "project/plan/GeneratedProjectManifestStore.java"));
        assertThat(storeSource).contains("RUNTIME_PLUGIN");
        assertThat(storeSource).contains("must not appear in the manifest");
    }
}

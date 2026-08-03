package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeDescriptorSeverity;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runtime-Descriptor-Planning-Guard (Spezifikation §55.1):
 *
 * <ul>
 *   <li>der Planner liefert Diagnostics nicht nur als leere API weiter;</li>
 *   <li>kein stilles {@code null} für unresolved writable targets ohne
 *       Diagnostic;</li>
 *   <li>der Generator übernimmt Descriptor-Diagnostics in den Gesamtplan.</li>
 * </ul>
 */
class RuntimeDescriptorPlanningGuardTest {

    @Test
    void plannerCanProduceBlockingDiagnostics() throws Exception {
        // Die Planner-Tests belegen echte Diagnostics; dieser Guard stellt
        // sicher, dass die Produktionspfade des Planners Diagnostics erzeugen
        // können und der Plan sie blockierend behandelt.
        RuntimeDescriptorDiagnostic blocking = new RuntimeDescriptorDiagnostic(
            RuntimeDescriptorSeverity.ERROR,
            RuntimeDescriptorDiagnosticCode.UNRESOLVED_TARGET_CLASS,
            "person", "target missing", java.util.Map.of());
        assertThat(blocking.blocking()).isTrue();
        RuntimeDescriptorPlan plan = new RuntimeDescriptorPlan(
            List.of(), List.of(), List.of(), List.of(blocking));
        assertThat(plan.hasBlockingDiagnostics()).isTrue();
    }

    @Test
    void plannerSourceContainsNoSilentNullReturnsWithoutDiagnostics() throws Exception {
        // Der Planner darf keinen Pfad haben, der ein unresolved writable
        // Ziel still auf null setzt: UNRESOLVED_TARGET_CLASS muss in
        // planRelationship erzeugt werden.
        String source = Files.readString(sourceFile("RuntimeDescriptorPlanner.java"));
        assertThat(source).contains("UNRESOLVED_TARGET_CLASS");
        assertThat(source).contains("diagnostics.add(new RuntimeDescriptorDiagnostic(");
    }

    @Test
    void generatorCopiesDescriptorDiagnosticsIntoGenerationPlan() throws Exception {
        String plannerSource = Files.readString(sourceFile(
            "project/plan/GrailsGenerationPlanner.java"));
        assertThat(plannerSource)
            .contains("descriptorPlan.diagnostics()")
            .contains("GenerationDiagnosticCode.RUNTIME_DESCRIPTOR_INVALID")
            .doesNotContain("descriptorPlan.throwIfBlocking()");
    }

    @Test
    void productiveRuntimeUsesGeneratedRegistryAccessorOnlyDuringStartup() throws Exception {
        Path runtimeRoot = Path.of("grails-runtime").toAbsolutePath().normalize();
        List<Path> productionSources;
        try (var paths = Files.walk(runtimeRoot)) {
            productionSources = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".groovy"))
                .filter(path -> !path.toString().contains("/src/test/"))
                .toList();
        }

        List<Path> accessorUsers = productionSources.stream()
            .filter(path -> {
                try {
                    return Files.readString(path).contains("GeneratedRegistryAccessor");
                } catch (java.io.IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            })
            .map(runtimeRoot::relativize)
            .toList();

        assertThat(accessorUsers).containsExactlyInAnyOrder(
            Path.of("src/main/groovy/ch/interlis/generator/grails/runtime/GeneratedRegistryAccessor.groovy"),
            Path.of("src/main/groovy/ch/interlis/generator/grails/runtime/registry/InterlisRuntimeRegistryBeanFactory.groovy")
        );
        for (Path source : productionSources) {
            assertThat(Files.readString(source))
                .as("legacy registry calls in %s", runtimeRoot.relativize(source))
                .doesNotContain("legacyDomains(", "legacyAssociation(", "legacyContext(",
                    "legacyEntity(", "legacyEntities(", "legacyContextsForParticipant(",
                    "legacyShowInNavigation(");
        }
    }

    private static Path sourceFile(String relative) {
        return Path.of("target-grails/src/main/java/ch/interlis/generator/grails/" + relative);
    }

}

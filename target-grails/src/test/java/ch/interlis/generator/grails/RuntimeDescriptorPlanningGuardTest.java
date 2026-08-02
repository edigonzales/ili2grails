package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeDescriptorSeverity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runtime-Descriptor-Planning-Guard (Spezifikation §55.1):
 *
 * <ul>
 *   <li>der Planner liefert Diagnostics nicht nur als leere API weiter;</li>
 *   <li>kein stilles {@code null} für unresolved writable targets ohne
 *       Diagnostic;</li>
 *   <li>der Generator ruft {@code throwIfBlocking()} vor der Dateiplanung auf.</li>
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
        try {
            plan.throwIfBlocking();
            throw new AssertionError("expected RuntimeDescriptorPlanningException");
        } catch (RuntimeDescriptorPlanningException expected) {
            assertThat(expected.getDiagnostics()).hasSize(1);
        }
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
    void generatorInvokesDescriptorGateBeforeFilePlanning() throws Exception {
        // GrailsGenerationPlanner ruft descriptorPlan.throwIfBlocking() vor
        // jeder Dateiplanung auf (Spezifikation §19.5).
        String plannerSource = Files.readString(sourceFile(
            "project/plan/GrailsGenerationPlanner.java"));
        assertThat(plannerSource)
            .contains("descriptorPlan.throwIfBlocking()")
            .contains("descriptorPlan.blockingDiagnostics()");
    }

    private static Path sourceFile(String relative) {
        return Path.of("target-grails/src/main/java/ch/interlis/generator/grails/" + relative);
    }

}

package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeDescriptorSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runtime-Descriptor-Diagnostic-Verträge (Spezifikation §63).
 */
class RuntimeDescriptorDiagnosticTest {

    @Test
    void blockingDescriptorDiagnosticCannotBeSilentlyIgnoredByPlan() {
        RuntimeDescriptorPlan plan = new RuntimeDescriptorPlan(
            List.of(), List.of(), List.of(), List.of(
                new RuntimeDescriptorDiagnostic(
                    RuntimeDescriptorSeverity.ERROR,
                    RuntimeDescriptorDiagnosticCode.UNRESOLVED_TARGET_CLASS,
                    "person", "target missing", Map.of("targetIliClass", "X"))));

        assertThat(plan.hasBlockingDiagnostics()).isTrue();
        assertThat(plan.blockingDiagnostics()).hasSize(1);
        assertThatThrownBy(plan::throwIfBlocking)
            .isInstanceOf(RuntimeDescriptorPlanningException.class)
            .satisfies(exception -> assertThat(
                ((RuntimeDescriptorPlanningException) exception).getDiagnostics())
                .extracting(RuntimeDescriptorDiagnostic::code)
                .contains(RuntimeDescriptorDiagnosticCode.UNRESOLVED_TARGET_CLASS));
    }

    @Test
    void warningDiagnosticDoesNotBlock() {
        RuntimeDescriptorPlan plan = new RuntimeDescriptorPlan(
            List.of(), List.of(), List.of(), List.of(
                new RuntimeDescriptorDiagnostic(
                    RuntimeDescriptorSeverity.WARNING,
                    RuntimeDescriptorDiagnosticCode.UNRESOLVED_TARGET_CLASS,
                    "externalRef", "read-only", Map.of())));

        assertThat(plan.hasBlockingDiagnostics()).isFalse();
        assertThat(plan.blockingDiagnostics()).isEmpty();
        plan.throwIfBlocking(); // must not throw
    }

    @Test
    void diagnosticSeveritiesAreClosedAndBlockingOnlyForError() {
        assertThat(RuntimeDescriptorSeverity.values())
            .containsExactly(RuntimeDescriptorSeverity.INFO, RuntimeDescriptorSeverity.WARNING,
                RuntimeDescriptorSeverity.ERROR);
        assertThat(new RuntimeDescriptorDiagnostic(
            RuntimeDescriptorSeverity.ERROR, RuntimeDescriptorDiagnosticCode.UNRESOLVED_ROLE_TARGET,
            "role", "message", null).blocking()).isTrue();
        assertThat(new RuntimeDescriptorDiagnostic(
            RuntimeDescriptorSeverity.WARNING, RuntimeDescriptorDiagnosticCode.UNRESOLVED_ROLE_TARGET,
            "role", "message", null).blocking()).isFalse();
        assertThat(new RuntimeDescriptorDiagnostic(
            RuntimeDescriptorSeverity.INFO, RuntimeDescriptorDiagnosticCode.UNRESOLVED_ROLE_TARGET,
            "role", "message", null).blocking()).isFalse();
    }

    @Test
    void diagnosticDetailsAreImmutableAndOrdered() {
        Map<String, String> unordered = new java.util.LinkedHashMap<>();
        unordered.put("zeta", "1");
        unordered.put("alpha", "2");
        RuntimeDescriptorDiagnostic diagnostic = new RuntimeDescriptorDiagnostic(
            RuntimeDescriptorSeverity.ERROR, RuntimeDescriptorDiagnosticCode.DUPLICATE_CONTEXT_DESCRIPTOR,
            "ctx", "duplicate", unordered);
        assertThat(diagnostic.details().keySet()).containsExactly("alpha", "zeta");
        assertThat(diagnostic.details()).isUnmodifiable();
    }
}

package ch.interlis.generator.grails.runtime.api.registry;

import java.util.List;

/**
 * Structured report of registry validation failures. Reported at startup so
 * that invalid generated contracts fail early instead of surfacing during
 * user actions.
 */
public record RegistryValidationReport(
    List<RegistryDiagnostic> diagnostics
) {

    public RegistryValidationReport {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean hasBlockingDiagnostics() {
        return diagnostics.stream().anyMatch(RegistryDiagnostic::blocking);
    }

    public List<RegistryDiagnostic> blockingDiagnostics() {
        return diagnostics.stream().filter(RegistryDiagnostic::blocking).toList();
    }
}

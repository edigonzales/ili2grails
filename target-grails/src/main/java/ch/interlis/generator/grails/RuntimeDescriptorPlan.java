package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;

import java.util.List;

/**
 * Typed runtime descriptor plan: the single source for the generated typed
 * registries.
 *
 * <p>Blockierende Diagnostics (ERROR) verhindern jede Dateiplanung:
 * {@link #throwIfBlocking()} muss vom Generator vor dem ersten Write
 * aufgerufen werden (Spezifikation §19.5).</p>
 */
public record RuntimeDescriptorPlan(
    List<DomainDescriptor> domains,
    List<AssociationDescriptor> associations,
    List<AssociationContextDescriptor> contexts,
    List<RuntimeDescriptorDiagnostic> diagnostics
) {

    public RuntimeDescriptorPlan {
        domains = domains == null ? List.of() : List.copyOf(domains);
        associations = associations == null ? List.of() : List.copyOf(associations);
        contexts = contexts == null ? List.of() : List.copyOf(contexts);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean hasBlockingDiagnostics() {
        return diagnostics.stream().anyMatch(RuntimeDescriptorDiagnostic::blocking);
    }

    public List<RuntimeDescriptorDiagnostic> blockingDiagnostics() {
        return diagnostics.stream().filter(RuntimeDescriptorDiagnostic::blocking).toList();
    }

    public void throwIfBlocking() {
        if (hasBlockingDiagnostics()) {
            throw new RuntimeDescriptorPlanningException(
                "Runtime descriptor planning has blocking diagnostics; no files may be written:\n  - "
                    + blockingDiagnostics().stream()
                        .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
                        .reduce((left, right) -> left + "\n  - " + right)
                        .orElse("unknown planning failure"),
                blockingDiagnostics());
        }
    }
}

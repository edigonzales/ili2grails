package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;

import java.util.List;

/**
 * Typed runtime descriptor plan: the single source for the generated typed
 * registries.
 *
 * <p>Blockierende Diagnostics (ERROR) werden in den vollständigen
 * {@code GenerationPlan} übernommen und verhindern dort jeden Write.</p>
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

}

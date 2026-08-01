package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;

import java.util.List;

/**
 * Typed runtime descriptor plan: the single source for the generated typed
 * registries.
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
}

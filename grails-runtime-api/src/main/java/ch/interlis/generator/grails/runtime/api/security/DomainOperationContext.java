package ch.interlis.generator.grails.runtime.api.security;

import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;

/**
 * Typed context for a CRUD operation on a domain.
 *
 * <p>All names originate from validated generated descriptors, never from
 * client-supplied class or property names.</p>
 */
public record DomainOperationContext(
    DomainOperation operation,
    String domainClassName,
    String iliName
) {

    public DomainOperationContext {
        operation = operation == null ? DomainOperation.VIEW : operation;
    }

    public static DomainOperationContext of(DomainOperation operation,
                                            DomainDescriptor descriptor) {
        return new DomainOperationContext(operation, descriptor.domainClassName(), descriptor.iliName());
    }
}

package ch.interlis.generator.grails.runtime.api.display;

import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;

/**
 * Resolves the display label of a domain instance. The default implementation
 * uses the generated display fields and falls back to the record id.
 */
public interface InterlisDisplayLabelResolver {

    String labelFor(Object domainInstance, DomainDescriptor descriptor);
}

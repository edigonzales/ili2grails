package ch.interlis.generator.grails.runtime.api.registry;

import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Lookup contract for generated domain descriptors.
 */
public interface DomainRegistry {

    Collection<DomainDescriptor> domains();

    Optional<DomainDescriptor> byIliName(String iliName);

    Optional<DomainDescriptor> byDomainClassName(String qualifiedClassName);

    List<DomainDescriptor> byModel(String modelName);
}

package ch.interlis.generator.grails.runtime.api.registry;

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Lookup contract for generated association descriptors and contexts.
 */
public interface AssociationRegistry {

    Collection<AssociationDescriptor> associations();

    Optional<AssociationDescriptor> association(String name);

    Collection<AssociationContextDescriptor> contexts();

    Optional<AssociationContextDescriptor> context(String id);

    List<AssociationContextDescriptor> contextsForParticipant(String domainClassName);
}

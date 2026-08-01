package ch.interlis.generator.grails.runtime.api.registry;

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Unified, immutable runtime registry built from the generated domain and
 * association registries.
 *
 * <p>Runtime services inject this registry; they never import generated
 * static maps directly. Class resolution is delegated to a cached resolver.</p>
 */
public final class InterlisRuntimeRegistry {

    private final DomainRegistry domainRegistry;
    private final AssociationRegistry associationRegistry;
    private final RuntimeClassResolver classResolver;
    private final Map<String, DomainDescriptor> domainsByIliName;
    private final Map<String, DomainDescriptor> domainsByClassName;
    private final Map<String, AssociationDescriptor> associationsByName;
    private final Map<String, AssociationContextDescriptor> contextsById;
    private final Map<String, List<AssociationContextDescriptor>> contextsByParticipant;
    private final Map<String, Class<?>> resolvedClasses;

    public InterlisRuntimeRegistry(DomainRegistry domainRegistry,
                                   AssociationRegistry associationRegistry,
                                   RuntimeClassResolver classResolver) {
        this.domainRegistry = Objects.requireNonNull(domainRegistry, "domainRegistry");
        this.associationRegistry = Objects.requireNonNull(associationRegistry, "associationRegistry");
        this.classResolver = Objects.requireNonNull(classResolver, "classResolver");

        this.domainsByIliName = new LinkedHashMap<>();
        this.domainsByClassName = new LinkedHashMap<>();
        Set<String> iliNames = new LinkedHashSet<>();
        Set<String> classNames = new LinkedHashSet<>();
        for (DomainDescriptor domain : domainRegistry.domains()) {
            if (!iliNames.add(domain.iliName())) {
                throw new IllegalArgumentException(
                    "Duplicate domain iliName in registry: " + domain.iliName());
            }
            if (domain.domainClassName() != null && !domain.domainClassName().isBlank()
                && !classNames.add(domain.domainClassName())) {
                throw new IllegalArgumentException(
                    "Duplicate domainClassName in registry: " + domain.domainClassName());
            }
            domainsByIliName.put(domain.iliName(), domain);
            if (domain.domainClassName() != null && !domain.domainClassName().isBlank()) {
                domainsByClassName.put(domain.domainClassName(), domain);
            }
        }

        this.associationsByName = new LinkedHashMap<>();
        Set<String> associationNames = new LinkedHashSet<>();
        for (AssociationDescriptor association : associationRegistry.associations()) {
            if (!associationNames.add(association.associationName())) {
                throw new IllegalArgumentException(
                    "Duplicate association name in registry: " + association.associationName());
            }
            associationsByName.put(association.associationName(), association);
        }

        this.contextsById = new LinkedHashMap<>();
        Set<String> contextIds = new LinkedHashSet<>();
        Map<String, List<AssociationContextDescriptor>> contextsByParticipantBuilder = new LinkedHashMap<>();
        for (AssociationContextDescriptor context : associationRegistry.contexts()) {
            if (!contextIds.add(context.id())) {
                throw new IllegalArgumentException(
                    "Duplicate association context id in registry: " + context.id());
            }
            AssociationDescriptor association = associationsByName.get(context.associationName());
            if (association == null) {
                throw new IllegalArgumentException(
                    "Context '" + context.id() + "' references unknown association '"
                        + context.associationName() + "'");
            }
            validateContext(association, context);
            contextsById.put(context.id(), context);
            if (context.participantDomainClassName() != null
                && !context.participantDomainClassName().isBlank()) {
                contextsByParticipantBuilder
                    .computeIfAbsent(context.participantDomainClassName(), key -> new java.util.ArrayList<>())
                    .add(context);
            }
        }
        Map<String, List<AssociationContextDescriptor>> immutableContexts = new LinkedHashMap<>();
        contextsByParticipantBuilder.forEach((key, value) ->
            immutableContexts.put(key, java.util.Collections.unmodifiableList(
                value.stream().sorted(java.util.Comparator.comparing(AssociationContextDescriptor::id)).toList())));
        this.contextsByParticipant = java.util.Collections.unmodifiableMap(immutableContexts);
        this.resolvedClasses = new java.util.concurrent.ConcurrentHashMap<>();
    }

    private void validateContext(AssociationDescriptor association, AssociationContextDescriptor context) {
        if (context.fixedRoleName() != null && !context.fixedRoleName().isBlank()
            && association.role(context.fixedRoleName()).isEmpty()) {
            throw new IllegalArgumentException(
                "Context '" + context.id() + "' fixed role '" + context.fixedRoleName()
                    + "' is not a role of association '" + association.associationName() + "'");
        }
        for (String editableRole : context.editableRoleNames()) {
            if (association.role(editableRole).isEmpty()) {
                throw new IllegalArgumentException(
                    "Context '" + context.id() + "' editable role '" + editableRole
                        + "' is not a role of association '" + association.associationName() + "'");
            }
        }
    }

    public DomainDescriptor requireDomain(Class<?> domainType) {
        Objects.requireNonNull(domainType, "domainType");
        DomainDescriptor descriptor = domainsByClassName.get(domainType.getName());
        if (descriptor == null) {
            throw new IllegalArgumentException(
                "No UI registry entry found for domainClassName '" + domainType.getName() + "'");
        }
        return descriptor;
    }

    public Optional<DomainDescriptor> domainByIliName(String iliName) {
        return Optional.ofNullable(domainsByIliName.get(iliName));
    }

    public Optional<DomainDescriptor> domainByClassName(String qualifiedClassName) {
        return Optional.ofNullable(domainsByClassName.get(qualifiedClassName));
    }

    public AssociationDescriptor requireAssociation(String name) {
        AssociationDescriptor descriptor = associationsByName.get(name);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown association: " + name);
        }
        return descriptor;
    }

    public Optional<AssociationDescriptor> association(String name) {
        return Optional.ofNullable(associationsByName.get(name));
    }

    public AssociationContextDescriptor requireContext(Class<?> participantType, String contextId) {
        Objects.requireNonNull(participantType, "participantType");
        if (contextId == null || contextId.isBlank()) {
            throw new IllegalArgumentException("contextId must not be null or blank");
        }
        AssociationContextDescriptor context = contextsById.get(contextId);
        if (context == null) {
            throw new IllegalArgumentException("Unknown association context: " + contextId);
        }
        if (context.participantDomainClassName() == null
            || !context.participantDomainClassName().equals(participantType.getName())) {
            throw new IllegalArgumentException(
                "Context " + contextId + " does not belong to domain " + participantType.getName()
                    + ", expected " + context.participantDomainClassName());
        }
        return context;
    }

    public Optional<AssociationContextDescriptor> context(String id) {
        return Optional.ofNullable(contextsById.get(id));
    }

    public List<AssociationContextDescriptor> contextsForParticipant(String domainClassName) {
        return contextsByParticipant.getOrDefault(domainClassName, List.of());
    }

    public Class<?> resolveDomainClass(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return null;
        }
        return resolvedClasses.computeIfAbsent(qualifiedName, classResolver::resolve);
    }

    public Collection<DomainDescriptor> domains() {
        return java.util.Collections.unmodifiableCollection(domainsByIliName.values());
    }

    public Collection<AssociationDescriptor> associations() {
        return java.util.Collections.unmodifiableCollection(associationsByName.values());
    }
}

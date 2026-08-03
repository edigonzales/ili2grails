package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipDescriptor
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry

/** Resolves generated inverse relationships and applies restrictive UI overrides. */
final class InterlisInverseRelationshipSupport {

    private InterlisInverseRelationshipSupport() {
    }

    static List<InverseRelationshipDescriptor> descriptors(
        InterlisRuntimeRegistry runtimeRegistry,
        def overridesService,
        Class ownerType
    ) {
        DomainDescriptor domain = runtimeRegistry.requireDomain(ownerType)
        return domain.inverseRelationships().values().collect { InverseRelationshipDescriptor generated ->
            overridesService.applyInverseRelationshipOverrides(
                generated, overridesService.overridesFor(domain))
        }.findAll { InverseRelationshipDescriptor descriptor ->
            descriptor.visible()
        }.sort { left, right -> left.name() <=> right.name() }
    }

    static InverseRelationshipDescriptor requireDescriptor(
        InterlisRuntimeRegistry runtimeRegistry,
        def overridesService,
        Class ownerType,
        String relationshipName
    ) {
        if (ownerType == null || relationshipName == null || relationshipName.isBlank()) {
            throw new InverseRelationshipNotFoundException("relationship parameter is required")
        }
        DomainDescriptor domain = runtimeRegistry.requireDomain(ownerType)
        InverseRelationshipDescriptor generated = domain.inverseRelationships().get(relationshipName)
        if (generated == null) {
            throw new InverseRelationshipNotFoundException(
                "Unknown inverse relationship '${relationshipName}' for ${ownerType.name}")
        }
        return overridesService.applyInverseRelationshipOverrides(
            generated, overridesService.overridesFor(domain))
    }

    static Class resolveRelatedClass(InterlisRuntimeRegistry runtimeRegistry,
                                     InverseRelationshipDescriptor descriptor) {
        return descriptor == null
            ? null
            : runtimeRegistry.resolveDomainClass(descriptor.relatedDomainClassName())
    }

    static String controllerForClass(InterlisRuntimeRegistry runtimeRegistry, Class domainType) {
        if (domainType == null) {
            return null
        }
        return runtimeRegistry.domainByClassName(domainType.name)
            .map { it.controllerName() }
            .orElse(null)
    }

    static Object readProperty(Object instance, String propertyName) {
        if (instance == null || propertyName == null || propertyName.isBlank()) {
            return null
        }
        try {
            return instance."${propertyName}"
        } catch (MissingPropertyException ignored) {
            return null
        }
    }

    static class InverseRelationshipNotFoundException extends IllegalArgumentException {
        InverseRelationshipNotFoundException(String message) {
            super(message)
        }
    }
}

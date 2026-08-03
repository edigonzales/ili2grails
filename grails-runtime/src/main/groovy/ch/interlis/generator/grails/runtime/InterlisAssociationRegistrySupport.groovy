package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry

final class InterlisAssociationRegistrySupport {

    private InterlisAssociationRegistrySupport() {
    }

    static List<AssociationContextDescriptor> contextsForParticipant(
        InterlisRuntimeRegistry runtimeRegistry, Class domainType) {
        if (domainType == null) {
            return []
        }
        return runtimeRegistry.contextsForParticipant(domainType.name)
    }

    static AssociationContextDescriptor requireContext(InterlisRuntimeRegistry runtimeRegistry,
                                                        Class participantType,
                                                        String contextId) {
        if (participantType == null) {
            throw new IllegalArgumentException("participantType must not be null")
        }
        if (contextId == null || contextId.isBlank()) {
            throw new IllegalArgumentException("contextId must not be null or blank")
        }
        AssociationContextDescriptor context = runtimeRegistry.context(contextId).orElse(null)
        if (context == null) {
            throw new AssociationContextNotFoundException("Unknown association context: ${contextId}")
        }
        String participantDomainClass = context.participantDomainClassName()
        if (participantDomainClass == null || participantDomainClass != participantType.name) {
            throw new AssociationOwnershipException(
                "Context ${contextId} does not belong to domain ${participantType.name}, " +
                "expected ${participantDomainClass ?: 'null'}"
            )
        }
        String associationName = context.associationName()
        AssociationDescriptor association = runtimeRegistry.association(associationName).orElse(null)
        if (association == null) {
            throw new AssociationContextNotFoundException("Association ${associationName} not found in registry for context ${contextId}")
        }
        String fixedRole = context.fixedRoleName()
        if (association.role(fixedRole).isEmpty()) {
            throw new AssociationContextNotFoundException("Fixed role '${fixedRole}' not found in association ${associationName}")
        }
        String fixedProperty = context.fixedPropertyName()
        if (fixedProperty == null || fixedProperty.isBlank()) {
            throw new AssociationContextNotFoundException("Fixed property not resolved for context ${contextId}")
        }
        return context
    }

    static AssociationDescriptor requireAssociation(InterlisRuntimeRegistry runtimeRegistry,
                                                     String associationName) {
        if (associationName == null || associationName.isBlank()) {
            throw new IllegalArgumentException("associationName must not be null or blank")
        }
        AssociationDescriptor association = runtimeRegistry.association(associationName).orElse(null)
        if (association == null) {
            throw new AssociationContextNotFoundException("Unknown association: ${associationName}")
        }
        return association
    }

    static Class resolveDomainClass(InterlisRuntimeRegistry runtimeRegistry, String qualifiedClassName) {
        if (runtimeRegistry == null || qualifiedClassName == null || qualifiedClassName.isBlank()) {
            return null
        }
        try {
            return runtimeRegistry.resolveDomainClass(qualifiedClassName)
        } catch (Exception ignored) {
            return null
        }
    }

    static Class resolveAssociationClass(InterlisRuntimeRegistry runtimeRegistry,
                                         AssociationContextDescriptor context) {
        if (runtimeRegistry == null || context == null) {
            return null
        }
        AssociationDescriptor association = runtimeRegistry.association(context.associationName()).orElse(null)
        if (association == null) {
            return null
        }
        return resolveDomainClass(runtimeRegistry, association.domainClassName())
    }

    static AssociationRoleDescriptor role(AssociationDescriptor association, String roleName) {
        if (association == null || roleName == null) {
            return null
        }
        return association.role(roleName).orElse(null)
    }

    static List<AssociationRoleDescriptor> editableRoles(AssociationDescriptor association,
                                                         AssociationContextDescriptor context) {
        if (association == null || context == null) {
            return []
        }
        return association.roles().findAll { context.editableRoleNames().contains(it.name()) }
    }

    static boolean isAssociationDomain(InterlisRuntimeRegistry runtimeRegistry, Class domainType) {
        if (domainType == null) {
            return false
        }
        return runtimeRegistry.domainByClassName(domainType.name)
            .map { it.kind() == DomainKind.ASSOCIATION }
            .orElse(false)
    }

    static boolean showInNavigation(InterlisRuntimeRegistry runtimeRegistry, Class domainType) {
        if (domainType == null) {
            return false
        }
        return runtimeRegistry.domainByClassName(domainType.name)
            .map { it.navigationVisible() }
            .orElse(true)
    }

    static class AssociationContextNotFoundException extends RuntimeException {
        AssociationContextNotFoundException(String message) {
            super(message)
        }
    }

    static class AssociationOwnershipException extends RuntimeException {
        AssociationOwnershipException(String message) {
            super(message)
        }
    }
}

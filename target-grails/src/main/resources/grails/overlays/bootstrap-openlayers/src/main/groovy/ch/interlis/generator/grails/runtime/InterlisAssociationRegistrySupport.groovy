package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.generated.InterlisAssociationRegistry

final class InterlisAssociationRegistrySupport {

    private InterlisAssociationRegistrySupport() {
    }

    static List<Map<String, Object>> contextsForParticipant(Class domainType) {
        if (domainType == null) {
            return []
        }
        return InterlisAssociationRegistry.contextsForParticipant(domainType.name)
    }

    static Map<String, Object> requireContext(Class participantType, String contextId) {
        if (participantType == null) {
            throw new IllegalArgumentException("participantType must not be null")
        }
        if (contextId == null || contextId.isBlank()) {
            throw new IllegalArgumentException("contextId must not be null or blank")
        }
        Map<String, Object> context = InterlisAssociationRegistry.context(contextId)
        if (context == null) {
            throw new AssociationContextNotFoundException("Unknown association context: ${contextId}")
        }
        String participantDomainClass = context.participantDomainClass
        if (participantDomainClass == null || participantDomainClass != participantType.name) {
            throw new AssociationOwnershipException(
                "Context ${contextId} does not belong to domain ${participantType.name}, " +
                "expected ${participantDomainClass ?: 'null'}"
            )
        }
        String associationName = context.associationName
        Map<String, Object> association = InterlisAssociationRegistry.association(associationName)
        if (association == null) {
            throw new AssociationContextNotFoundException("Association ${associationName} not found in registry for context ${contextId}")
        }
        String fixedRole = context.fixedRole
        List<Map<String, Object>> roles = association.roles as List<Map<String, Object>>
        if (roles == null || roles.every { it.name != fixedRole }) {
            throw new AssociationContextNotFoundException("Fixed role '${fixedRole}' not found in association ${associationName}")
        }
        String fixedProperty = context.fixedProperty
        if (fixedProperty == null || fixedProperty.isBlank()) {
            throw new AssociationContextNotFoundException("Fixed property not resolved for context ${contextId}")
        }
        return context
    }

    static Map<String, Object> requireAssociation(String associationName) {
        if (associationName == null || associationName.isBlank()) {
            throw new IllegalArgumentException("associationName must not be null or blank")
        }
        Map<String, Object> association = InterlisAssociationRegistry.association(associationName)
        if (association == null) {
            throw new AssociationContextNotFoundException("Unknown association: ${associationName}")
        }
        return association
    }

    static Class resolveDomainClass(def grailsApplication, String qualifiedClassName) {
        if (grailsApplication == null || qualifiedClassName == null || qualifiedClassName.isBlank()) {
            return null
        }
        try {
            def artefact = grailsApplication.getDomainClass(qualifiedClassName)
            return artefact?.clazz
        } catch (Exception ignored) {
            return null
        }
    }

    static Class resolveAssociationClass(def grailsApplication, Map<String, Object> context) {
        if (grailsApplication == null || context == null) {
            return null
        }
        String associationName = context.associationName
        Map<String, Object> association = InterlisAssociationRegistry.association(associationName)
        if (association == null) {
            return null
        }
        String domainQualifiedName = association.domainClassQualifiedName
        return resolveDomainClass(grailsApplication, domainQualifiedName)
    }

    static Map<String, Object> role(Map<String, Object> association, String roleName) {
        if (association == null || roleName == null) {
            return null
        }
        List<Map<String, Object>> roles = association.roles as List<Map<String, Object>>
        return roles?.find { it.name == roleName }
    }

    static List<Map<String, Object>> editableRoles(Map<String, Object> association, Map<String, Object> context) {
        if (association == null || context == null) {
            return []
        }
        List<Map<String, Object>> roles = association.roles as List<Map<String, Object>>
        if (roles == null) {
            return []
        }
        String fixedRole = context.fixedRole
        List<String> editableRoleNames = context.editableRoles as List<String>
        if (editableRoleNames == null) {
            return []
        }
        return roles.findAll { editableRoleNames.contains(it.name) }
    }

    static boolean isAssociationDomain(Class domainType) {
        if (domainType == null) {
            return false
        }
        def entity = InterlisAssociationRegistry.ENTITIES[domainType.name]
        return entity != null && entity.kind == 'ASSOCIATION'
    }

    static boolean showInNavigation(Class domainType) {
        if (domainType == null) {
            return false
        }
        return InterlisAssociationRegistry.showInNavigation(domainType.name)
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

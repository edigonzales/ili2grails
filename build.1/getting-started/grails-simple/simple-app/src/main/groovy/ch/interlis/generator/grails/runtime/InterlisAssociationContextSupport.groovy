package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.generated.InterlisAssociationRegistry

final class InterlisAssociationContextSupport {

    private InterlisAssociationContextSupport() {
    }

    static Map<String, Object> prepareCreateContext(def grailsApplication,
                                                      Class domainType,
                                                      Map params) {
        if (domainType == null || params == null) {
            return [:]
        }
        String contextId = params.associationContext?.toString()
        String ownerIdStr = params.associationOwnerId?.toString()
        if (contextId == null || contextId.isBlank() || ownerIdStr == null || ownerIdStr.isBlank()) {
            return [:]
        }

        Map<String, Object> context = InterlisAssociationRegistry.CONTEXTS[contextId]
        if (context == null) {
            throw new InterlisAssociationRegistrySupport.AssociationContextNotFoundException(
                    "Unknown association context: ${contextId}")
        }
        Map<String, Object> association = InterlisAssociationRegistry.association(context.associationName)

        if (InterlisAssociationRegistrySupport.isAssociationDomain(domainType)) {
            verifyContextMatchesAssociation(domainType, context, association)
        }

        String participantDomainClass = context.participantDomainClass
        Class participantType = InterlisAssociationRegistrySupport.resolveDomainClass(grailsApplication,
                participantDomainClass)
        if (participantType == null) {
            throw new InterlisAssociationRegistrySupport.AssociationContextNotFoundException(
                    "Could not resolve participant domain: ${participantDomainClass}")
        }

        if (!InterlisAssociationRegistrySupport.isAssociationDomain(domainType)) {
            InterlisAssociationRegistrySupport.requireContext(domainType, contextId)
        }

        Object owner = loadOwner(participantType, ownerIdStr)
        if (owner == null) {
            throw new InterlisAssociationRegistrySupport.AssociationContextNotFoundException(
                    "Owner ${participantType.simpleName}(id=${ownerIdStr}) not found for context ${contextId}")
        }

        return buildContextState(grailsApplication, context, association, owner)
    }

    static Map<String, Object> prepareEditContext(def grailsApplication,
                                                   Class associationType,
                                                   Object associationInstance,
                                                   Map params) {
        if (associationInstance == null || params == null) {
            return [:]
        }
        String contextId = params.associationContext?.toString()
        String ownerIdStr = params.associationOwnerId?.toString()
        if (contextId == null || contextId.isBlank() || ownerIdStr == null || ownerIdStr.isBlank()) {
            return [:]
        }

        Map<String, Object> context = InterlisAssociationRegistry.CONTEXTS[contextId]
        if (context == null) {
            throw new InterlisAssociationRegistrySupport.AssociationContextNotFoundException(
                    "Unknown association context: ${contextId}")
        }
        Map<String, Object> association = InterlisAssociationRegistry.association(context.associationName)
        verifyContextMatchesAssociation(associationType, context, association)

        String participantDomainClass = context.participantDomainClass
        Class participantType = InterlisAssociationRegistrySupport.resolveDomainClass(grailsApplication,
                participantDomainClass)
        if (participantType == null) {
            throw new InterlisAssociationRegistrySupport.AssociationContextNotFoundException(
                    "Could not resolve participant domain: ${participantDomainClass}")
        }

        Object owner = loadOwner(participantType, ownerIdStr)
        if (owner == null) {
            throw new InterlisAssociationRegistrySupport.AssociationContextNotFoundException(
                    "Owner ${participantType.simpleName}(id=${ownerIdStr}) not found")
        }

        verifyOwnership(associationInstance, context, owner)

        return buildContextState(grailsApplication, context, association, owner)
    }

    static void applyFixedRole(Object associationInstance, Map<String, Object> contextState) {
        if (associationInstance == null || contextState == null || contextState.isEmpty()) {
            return
        }
        String fixedProperty = contextState.fixedProperty
        Object owner = contextState.owner
        if (fixedProperty == null || fixedProperty.isBlank() || owner == null) {
            return
        }
        try {
            associationInstance."${fixedProperty}" = owner
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to set fixed role property '${fixedProperty}' on ${associationInstance.getClass().simpleName}: " +
                            e.message, e)
        }
    }

    static Map<String, Object> redirectTarget(Map<String, Object> contextState) {
        if (contextState == null || contextState.isEmpty()) {
            return null
        }
        String participantDomainClass = contextState.participantDomainClass
        Object ownerId = contextState.ownerId
        if (participantDomainClass == null || ownerId == null) {
            return null
        }
        int lastDot = participantDomainClass.lastIndexOf('.')
        String controllerName = lastDot >= 0
                ? participantDomainClass.substring(lastDot + 1)
                : participantDomainClass
        controllerName = controllerName[0].toLowerCase() + controllerName[1..-1]
        return [
                controller: controllerName,
                action    : 'show',
                id        : ownerId
        ]
    }

    static void verifyContextMatchesAssociation(Class associationType,
                                                 Map<String, Object> context,
                                                 Map<String, Object> association) {
        if (association == null) {
            throw new InterlisAssociationRegistrySupport.AssociationContextNotFoundException(
                    "Association not found for context ${context?.id}")
        }
        String expectedDomainClass = association.domainClassQualifiedName
        if (expectedDomainClass != null && expectedDomainClass != associationType.name) {
            throw new InterlisAssociationRegistrySupport.AssociationContextNotFoundException(
                    "Context ${context?.id} does not belong to association ${associationType.name}")
        }
    }

    static List<String> hiddenRelationshipFields(Map<String, Object> contextState) {
        if (contextState == null || contextState.isEmpty()) {
            return []
        }
        String fixedProperty = contextState.fixedProperty
        return fixedProperty != null ? [fixedProperty] : []
    }

    static Map<String, String> fixedRelationshipLabels(Map<String, Object> contextState) {
        if (contextState == null || contextState.isEmpty()) {
            return [:]
        }
        String fixedProperty = contextState.fixedProperty
        Object owner = contextState.owner
        String ownerLabel = contextState.ownerLabel
        if (fixedProperty == null || ownerLabel == null) {
            return [:]
        }
        return [(fixedProperty): ownerLabel]
    }

    // ----- private helpers -----

    private static Object loadOwner(Class participantType, String ownerIdStr) {
        try {
            def id = safeParseId(participantType, ownerIdStr)
            return id != null ? participantType.get(id) : null
        } catch (Exception ignored) {
            return null
        }
    }

    private static Object safeParseId(Class domainType, String idStr) {
        if (idStr == null || idStr.isBlank()) {
            return null
        }
        try {
            def idProperty = domainType.metaClass.getMetaProperty('id')
            if (idProperty == null) {
                return Long.valueOf(idStr)
            }
            Class idType = idProperty.type
            if (idType == Long || idType == Long.TYPE) {
                return Long.valueOf(idStr)
            }
            if (idType == Integer || idType == Integer.TYPE) {
                return Integer.valueOf(idStr)
            }
            return idStr
        } catch (Exception ignored) {
            return Long.valueOf(idStr)
        }
    }

    private static void verifyOwnership(Object associationInstance,
                                         Map<String, Object> context,
                                         Object owner) {
        String fixedProperty = context.fixedProperty
        if (fixedProperty == null || fixedProperty.isBlank()) {
            throw new InterlisAssociationRegistrySupport.AssociationContextNotFoundException(
                    "Fixed property not resolved for context ${context.id}")
        }
        try {
            Object currentOwner = associationInstance."${fixedProperty}"
            if (currentOwner == null) {
                throw new InterlisAssociationRegistrySupport.AssociationOwnershipException(
                        "Association instance has no owner for fixed role '${fixedProperty}'")
            }
            Object currentId = currentOwner.id
            if (currentId == null) {
                throw new InterlisAssociationRegistrySupport.AssociationOwnershipException(
                        "Owner has no id for fixed role '${fixedProperty}'")
            }
            if (currentId.toString() != owner.id?.toString()) {
                throw new InterlisAssociationRegistrySupport.AssociationOwnershipException(
                        "Association instance ${associationInstance.id} does not belong to " +
                                "participant ${context.participantDomainClass}(id=${owner.id}), " +
                                "expected ${owner.id} but got ${currentId}")
            }
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            throw e
        } catch (Exception e) {
            throw new InterlisAssociationRegistrySupport.AssociationOwnershipException(
                    "Failed to verify ownership: " + e.message, e)
        }
    }

    private static Map<String, Object> buildContextState(def grailsApplication,
                                                          Map<String, Object> context,
                                                          Map<String, Object> association,
                                                          Object owner) {
        String ownerLabel = owner != null
                ? InterlisRelationshipOptions.optionLabel(grailsApplication, owner)
                : "?"
        return [
                contextId              : context.id,
                associationName        : context.associationName,
                ownerId                : owner?.id,
                owner                  : owner,
                ownerLabel             : ownerLabel,
                participantDomainClass : context.participantDomainClass,
                fixedRoleName          : context.fixedRole,
                fixedProperty          : context.fixedProperty
        ]
    }
}

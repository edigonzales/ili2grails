package ch.interlis.generator.grails.runtime

/**
 * Validates the direct 1:n context used when a new related record is created
 * from an owner page. The request supplies only a relationship field and an
 * owner id; all classes and writable properties are resolved from generated
 * metadata.
 */
final class InterlisInverseRelationshipContextSupport {

    private InterlisInverseRelationshipContextSupport() {
    }

    static Map<String, Object> prepareCreateContext(def grailsApplication,
                                                      Class relatedType,
                                                      Map params) {
        String field = params?.relationshipField?.toString()
        String ownerId = params?.relationshipOwnerId?.toString()
        boolean supplied = (field != null && !field.isBlank()) || (ownerId != null && !ownerId.isBlank())
        if (!supplied) {
            return [:]
        }
        if (field == null || field.isBlank() || ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException(
                "relationshipField and relationshipOwnerId must be supplied together"
            )
        }
        if (relatedType == null) {
            throw new IllegalArgumentException("Related domain type is required")
        }

        Map<String, Object> relationshipMeta = InterlisUiDescriptorSupport.staticDomainMap(
            relatedType,
            "interlisRelationshipMeta"
        )[field] as Map<String, Object>
        if (relationshipMeta == null) {
            throw new IllegalArgumentException(
                "Unknown relationship field '${field}' for ${relatedType.name}"
            )
        }
        String ownerClassName = relationshipMeta.targetClass?.toString()
        Class ownerType = resolveDomainClass(grailsApplication, ownerClassName)
        if (ownerType == null) {
            // Generated relationship metadata may contain a simple domain name
            // while the runtime mapping context is keyed by the fully qualified
            // class name. The property type is an additional safe fallback.
            ownerType = relatedType.metaClass.getMetaProperty(field)?.type as Class
        }
        if (ownerType == null) {
            throw new IllegalArgumentException(
                "Could not resolve owner domain '${ownerClassName}' for ${relatedType.name}.${field}"
            )
        }
        Map<String, Object> inverseMeta = InterlisInverseRelationshipSupport.descriptors(
            grailsApplication,
            ownerType
        ).find { Map<String, Object> candidate ->
            candidate.relatedDomainClass?.toString() == relatedType.name
                && candidate.relatedProperty?.toString() == field
                && candidate.visible == true
                && candidate.writable == true
        } as Map<String, Object>
        if (inverseMeta == null) {
            throw new IllegalArgumentException(
                "Relationship ${relatedType.name}.${field} is not an editable inverse context"
            )
        }

        Object owner = loadOwner(ownerType, ownerId)
        if (owner == null) {
            throw new IllegalArgumentException(
                "Owner ${ownerType.simpleName}(id=${ownerId}) not found for ${relatedType.name}.${field}"
            )
        }
        return [
            contextKind         : "DIRECT_RELATIONSHIP",
            contextId           : "inverse:${ownerType.name}:${inverseMeta.name ?: field}",
            owner               : owner,
            ownerId             : owner.id,
            ownerType           : ownerType,
            ownerDomainClass    : ownerType.name,
            ownerLabel          : InterlisRelationshipOptions.optionLabel(grailsApplication, owner),
            relatedDomainClass  : relatedType.name,
            fixedProperty       : field,
            relationshipField   : field,
            relationshipName    : inverseMeta.name?.toString(),
            label               : inverseMeta.label?.toString()
        ]
    }

    static void applyFixedRelationship(Object instance, Map<String, Object> contextState) {
        if (instance == null || contextState == null || contextState.isEmpty()) {
            return
        }
        String property = contextState.fixedProperty?.toString()
        Object owner = contextState.owner
        if (property == null || property.isBlank() || owner == null) {
            return
        }
        if (instance.metaClass.hasProperty(instance, property) == null) {
            throw new IllegalArgumentException(
                "Relationship field '${property}' is not available on ${instance.class.name}"
            )
        }
        instance."${property}" = owner
    }

    static Map<String, Object> redirectTarget(def grailsApplication,
                                               Map<String, Object> contextState) {
        Class ownerType = contextState?.ownerType as Class
        String controller = InterlisInverseRelationshipSupport.controllerForClass(ownerType)
        if (controller == null || contextState?.ownerId == null) {
            return null
        }
        return [controller: controller, action: "show", id: contextState.ownerId]
    }

    private static Class resolveDomainClass(def grailsApplication, String className) {
        if (className == null || className.isBlank()) {
            return null
        }
        def persistentEntity = grailsApplication?.mappingContext?.getPersistentEntity(className)
        Class resolved = persistentEntity?.javaClass as Class
        if (resolved != null) {
            return resolved
        }
        resolved = grailsApplication?.mappingContext?.persistentEntities?.find { entity ->
            Class candidate = entity?.javaClass as Class
            candidate != null && (candidate.name == className || candidate.simpleName == className)
        }?.javaClass as Class
        if (resolved != null) {
            return resolved
        }
        try {
            return grailsApplication?.classLoader?.loadClass(className) as Class
        } catch (Exception ignored) {
            return null
        }
    }

    private static Object loadOwner(Class ownerType, String ownerId) {
        try {
            Object id = parseId(ownerType, ownerId)
            return id == null ? null : ownerType.get(id)
        } catch (Exception ignored) {
            return null
        }
    }

    private static Object parseId(Class ownerType, String value) {
        def idProperty = ownerType.metaClass.getMetaProperty("id")
        Class idType = idProperty?.type as Class
        if (idType == Integer || idType == Integer.TYPE) {
            return Integer.valueOf(value)
        }
        if (idType == Long || idType == Long.TYPE || idType == null) {
            return Long.valueOf(value)
        }
        return value
    }
}

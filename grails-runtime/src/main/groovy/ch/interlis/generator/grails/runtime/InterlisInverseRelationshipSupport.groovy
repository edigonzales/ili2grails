package ch.interlis.generator.grails.runtime

/**
 * Resolves generated inverse-relationship metadata and applies runtime UI
 * overrides. Runtime configuration may hide or downgrade generated behavior,
 * but it can never make an unsafe relationship writable.
 */
final class InterlisInverseRelationshipSupport {

    private static final Set<String> ALLOWED_MODES = [
        "auto", "editable", "read-only", "off"
    ] as Set<String>

    private InterlisInverseRelationshipSupport() {
    }

    static List<Map<String, Object>> descriptors(def grailsApplication, Class ownerType) {
        Map<String, Map<String, Object>> generated = generatedMetadata(ownerType)
        Map<String, Object> configuredDomain =
            InterlisUiDescriptorSupport.configuredDomainForType(grailsApplication, ownerType)
        Map<String, Object> configuredRelationships = asMap(configuredDomain.relationships)
        validateConfiguredRelationships(ownerType, generated, configuredRelationships)

        return generated.collect { String name, Map<String, Object> raw ->
            effectiveDescriptor(name, raw, asMap(configuredRelationships[name]))
        }.findAll { Map<String, Object> descriptor ->
            descriptor.visible == true
        }.sort { Map<String, Object> left, Map<String, Object> right ->
            left.name.toString() <=> right.name.toString()
        } as List<Map<String, Object>>
    }

    static Map<String, Object> requireDescriptor(def grailsApplication,
                                                 Class ownerType,
                                                 String relationshipName) {
        if (ownerType == null || relationshipName == null || relationshipName.isBlank()) {
            throw new InverseRelationshipNotFoundException("relationship parameter is required")
        }
        Map<String, Map<String, Object>> generated = generatedMetadata(ownerType)
        Map<String, Object> raw = generated[relationshipName]
        if (raw == null) {
            throw new InverseRelationshipNotFoundException(
                "Unknown inverse relationship '${relationshipName}' for ${ownerType.name}"
            )
        }
        Map<String, Object> configuredDomain =
            InterlisUiDescriptorSupport.configuredDomainForType(grailsApplication, ownerType)
        Map<String, Object> configuredRelationships = asMap(configuredDomain.relationships)
        validateConfiguredRelationships(ownerType, generated, configuredRelationships)
        return effectiveDescriptor(
            relationshipName,
            raw,
            asMap(configuredRelationships[relationshipName])
        )
    }

    static Class resolveRelatedClass(def grailsApplication, Map<String, Object> descriptor) {
        String className = descriptor?.relatedDomainClass?.toString()
        if (className == null || className.isBlank()) {
            return null
        }
        def persistentEntity = grailsApplication?.mappingContext?.getPersistentEntity(className)
        Class resolved = persistentEntity?.javaClass as Class
        if (resolved != null) {
            return resolved
        }
        try {
            return grailsApplication?.classLoader?.loadClass(className) as Class
        } catch (Exception ignored) {
            return null
        }
    }

    static String controllerForClass(Class domainType) {
        if (domainType == null) {
            return null
        }
        Map<String, Object> entry = GeneratedRegistryAccessor.uiRegistryType().legacyDomains().find {
            it.domainClassName?.toString() == domainType.name
        } as Map<String, Object>
        return entry?.controller?.toString()
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

    private static Map<String, Map<String, Object>> generatedMetadata(Class ownerType) {
        Map raw = InterlisUiDescriptorSupport.staticDomainMap(
            ownerType,
            "interlisInverseRelationshipMeta"
        )
        return raw.collectEntries { Object key, Object value ->
            [(key.toString()): asMap(value)]
        } as Map<String, Map<String, Object>>
    }

    private static Map<String, Object> effectiveDescriptor(String name,
                                                           Map<String, Object> generated,
                                                           Map<String, Object> configured) {
        String mode = configured.containsKey("mode")
            ? configured.mode?.toString()
            : "auto"
        if (!ALLOWED_MODES.contains(mode)) {
            throw new IllegalArgumentException(
                "Invalid relationship mode '${mode}' for '${name}'; expected auto, editable, read-only or off"
            )
        }
        String configuredLabel = configured.containsKey("label")
            ? configured.label?.toString()
            : null
        if (configured.containsKey("label") && (configuredLabel == null || configuredLabel.isBlank())) {
            throw new IllegalArgumentException(
                "Invalid relationship label for '${name}': label must not be blank"
            )
        }
        boolean generatedWritable = generated.writable == true
        boolean visible = mode != "off"
        boolean writable = generatedWritable && (mode == "auto" || mode == "editable")
        Map<String, Object> result = new LinkedHashMap<>(generated)
        result.putAll([
            name: name,
            label: configuredLabel ?: generated.label?.toString() ?: humanize(name),
            mode: mode,
            visible: visible,
            writable: writable
        ])
        return result
    }

    private static void validateConfiguredRelationships(
        Class ownerType,
        Map<String, Map<String, Object>> generated,
        Map<String, Object> configured
    ) {
        configured.each { String name, Object raw ->
            if (!generated.containsKey(name)) {
                throw new IllegalArgumentException(
                    "Unknown relationship '${name}' in ili2grails.ui.domains for ${ownerType.name}"
                )
            }
            if (!(raw instanceof Map)) {
                throw new IllegalArgumentException(
                    "Invalid relationship configuration '${name}' for ${ownerType.name}: expected a map"
                )
            }
        }
    }

    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? value as Map<String, Object> : [:]
    }

    private static String humanize(String value) {
        if (value == null) {
            return ""
        }
        return value.replaceAll("([a-z])([A-Z])", '$1 $2')
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .findAll { !it.isBlank() }
            .collect { String part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1) }
            .join(' ')
    }

    static class InverseRelationshipNotFoundException extends IllegalArgumentException {
        InverseRelationshipNotFoundException(String message) {
            super(message)
        }
    }
}

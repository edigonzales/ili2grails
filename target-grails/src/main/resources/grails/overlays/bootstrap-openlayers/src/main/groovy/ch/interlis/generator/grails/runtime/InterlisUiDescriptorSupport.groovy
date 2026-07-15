package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.generated.InterlisUiRegistry

import java.time.temporal.Temporal

/**
 * Builds the framework-neutral UI descriptor consumed by later Bootstrap UI
 * phases. It reads generated metadata and GORM metadata, but does not own
 * relationship or association semantics.
 */
final class InterlisUiDescriptorSupport {

    private static final List<String> DISPLAY_FIELD_PREFERENCES = [
        "name", "bezeichnung", "label", "title", "code", "ident"
    ]
    private static final List<String> PREFERRED_LABEL_LANGUAGES = ["de-CH", "de", "en"]
    private static final int DEFAULT_DISPLAY_FIELD_LIMIT = 1
    private static final int DEFAULT_COMPACT_FIELD_LIMIT = 4

    private InterlisUiDescriptorSupport() {
    }

    static Map<String, Object> descriptor(def grailsApplication, Class domainType) {
        if (domainType == null) {
            throw new IllegalArgumentException("domainType must not be null")
        }

        Map<String, Object> registryEntry = InterlisUiRegistry.domainForClassName(domainType.name)
        if (registryEntry == null) {
            throw new IllegalArgumentException(
                "No UI registry entry found for domainClassName '" + domainType.name + "'"
            )
        }

        String iliName = registryEntry.iliName?.toString()
        Map<String, Object> configuredDomain = configuredDomain(grailsApplication, iliName)
        List<Map<String, Object>> properties = propertyDescriptors(grailsApplication, domainType)
        Set<String> knownFields = properties.collect { it.name as String } as LinkedHashSet<String>
        knownFields.add("id")

        Map<String, Map<String, Object>> filters = filterDefinitions(properties)
        Map<String, Object> listConfig = asMap(configuredDomain.list)
        Map<String, Object> formConfig = asMap(configuredDomain.form)

        List<String> defaultColumns = defaultColumns(properties, domainType)
        List<String> defaultSearchFields = defaultSearchFields(properties, domainType)
        List<String> defaultProminentFilters = filters.keySet().take(3) as List<String>
        List<Map<String, Object>> defaultSections = [[
            title : "Allgemein",
            fields: editableFields(properties)
        ]]

        List<String> columns = configuredList(
            listConfig, "columns", defaultColumns, knownFields, iliName, "list.columns"
        )
        List<String> searchFields = configuredList(
            listConfig, "searchFields", defaultSearchFields, knownFields, iliName, "list.searchFields"
        )
        List<String> prominentFilters = configuredList(
            listConfig, "prominentFilters", defaultProminentFilters,
            filters.keySet() as Set<String>, iliName, "list.prominentFilters"
        )
        List<Map<String, Object>> sections = configuredSections(
            formConfig, defaultSections, knownFields, iliName
        )

        String label = configuredDomain.containsKey("label")
            ? requireText(configuredDomain.label, iliName, "label")
            : registryEntry.label?.toString()

        return [
            registry    : registryEntry,
            label       : label,
            appTitle    : appTitle(grailsApplication),
            list        : [
                columns          : columns,
                searchFields     : searchFields,
                prominentFilters : prominentFilters,
                filters          : filters
            ],
            form        : [sections: sections],
            detail      : [sections: sections],
            relationships: staticDomainMap(domainType, "interlisRelationshipMeta"),
            geometry    : staticDomainMap(domainType, "geometryMeta")
        ]
    }

    static String appTitle(def grailsApplication) {
        Object configured = grailsApplication?.config?.ili2grails?.ui?.appTitle
        String configuredText = configured?.toString()
        return configuredText == null || configuredText.isBlank()
            ? "INTERLIS CRUD"
            : configuredText
    }

    private static Map<String, Object> configuredDomain(def grailsApplication, String iliName) {
        Object rawDomains = grailsApplication?.config?.ili2grails?.ui?.domains
        List<?> domains = normalizeList(rawDomains)
        Map<String, Object> match = [:]

        domains.eachWithIndex { Object rawDomain, int index ->
            Map<String, Object> domain = asMap(rawDomain)
            String configuredIliName = domain.iliName?.toString()
            if (configuredIliName == null || configuredIliName.isBlank()) {
                throw new IllegalArgumentException(
                    "Invalid ili2grails.ui.domains[" + index + "]: iliName is required"
                )
            }
            if (InterlisUiRegistry.domain(configuredIliName) == null) {
                throw new IllegalArgumentException(
                    "Unknown iliName '" + configuredIliName + "' in ili2grails.ui.domains[" + index + "]"
                )
            }
            if (configuredIliName == iliName) {
                match = domain
            }
        }
        return match
    }

    private static List<Map<String, Object>> propertyDescriptors(def grailsApplication, Class domainType) {
        def entity = grailsApplication?.mappingContext?.getPersistentEntity(domainType.name)
        List<?> persistentProperties = entity?.persistentProperties ?: []
        Map<String, Object> constrained = [:]
        try {
            constrained = asMap(domainType.constrainedProperties)
        } catch (MissingPropertyException ignored) {
        }
        List<String> names = []
        names.addAll(persistentProperties.collect { it.name?.toString() }.findAll { it })
        names.addAll(constrained.keySet().collect { it.toString() })
        names.addAll(staticDomainMap(domainType, "interlisFieldMeta").keySet().collect { it.toString() })
        names.addAll(staticDomainMap(domainType, "interlisRelationshipMeta").keySet().collect { it.toString() })
        names.addAll(staticDomainMap(domainType, "geometryMeta").keySet().collect { it.toString() })
        names = names.unique()

        Map<String, Object> persistentByName = persistentProperties
            .findAll { it.name != null }
            .collectEntries { [(it.name.toString()): it] }

        return names.collect { String name ->
            def persistent = persistentByName[name]
            Class type = persistent?.type instanceof Class
                ? persistent.type as Class
                : domainType.metaClass?.getMetaProperty(name)?.type
            Map<String, Object> constraints = asMap(constrained[name])
            Map<String, Object> fieldMeta = staticDomainMap(domainType, "interlisFieldMeta")[name] ?: [:]
            boolean relationship = relationshipProperty(persistent, domainType, name)
            boolean geometry = staticDomainMap(domainType, "geometryMeta").containsKey(name)
                || (type != null && type.name?.contains("Geometry"))
            boolean collection = type != null && Collection.isAssignableFrom(type)
            boolean enumType = type != null && type.isEnum()
            if (!enumType && staticDomainMap(domainType, "interlisFieldMeta")[name]?.enumType != null) {
                enumType = true
            }
            [
                name        : name,
                type        : type,
                typeName    : type?.name,
                coreType    : fieldMeta.coreType?.toString(),
                constraints : constraints,
                relationship: relationship,
                geometry    : geometry,
                collection  : collection,
                enum        : enumType
            ]
        }
    }

    private static boolean relationshipProperty(def persistent, Class domainType, String name) {
        if (staticDomainMap(domainType, "interlisRelationshipMeta").containsKey(name)) {
            return true
        }
        try {
            return persistent?.isAssociation() == true
        } catch (MissingMethodException ignored) {
            return false
        }
    }

    private static List<String> defaultColumns(List<Map<String, Object>> properties, Class domainType) {
        List<String> result = []
        if (properties.any { it.name == "id" } || domainType.metaClass?.getMetaProperty("id") != null) {
            result.add("id")
        }
        List<String> displayFields = displayFields(domainType, properties)
        result.addAll(displayFields.take(DEFAULT_DISPLAY_FIELD_LIMIT))
        result.addAll(properties
            .findAll { compactScalar(it) && !result.contains(it.name) }
            .collect { it.name }
            .take(DEFAULT_COMPACT_FIELD_LIMIT))
        return result.unique()
    }

    private static List<String> defaultSearchFields(List<Map<String, Object>> properties, Class domainType) {
        Map<String, Object> displayMeta = staticDomainMap(domainType, "interlisDisplayMeta")
        List<String> configured = normalizeList(displayMeta.searchFields).collect { it.toString() }
        List<String> candidates = configured ?: properties
            .findAll { textProperty(it) }
            .collect { it.name }
        return candidates.findAll { String name ->
            properties.any { it.name == name && textProperty(it) }
        }.unique()
    }

    private static List<String> displayFields(Class domainType, List<Map<String, Object>> properties) {
        Map<String, Object> displayMeta = staticDomainMap(domainType, "interlisDisplayMeta")
        List<String> generated = normalizeList(displayMeta.displayFields).collect { it.toString() }
        if (generated) {
            return generated.findAll { name -> properties.any { it.name == name && compactScalar(it) } }
        }
        return DISPLAY_FIELD_PREFERENCES.findAll { preferred ->
            properties.any { it.name?.toString()?.equalsIgnoreCase(preferred) && compactScalar(it) }
        }.collect { preferred ->
            properties.find { it.name?.toString()?.equalsIgnoreCase(preferred) }.name
        }
    }

    private static Map<String, Map<String, Object>> filterDefinitions(List<Map<String, Object>> properties) {
        Map<String, Map<String, Object>> definitions = [:]
        properties.findAll { !it.geometry && !it.collection && it.name != "id" && it.name != "version" }
            .each { Map<String, Object> property ->
                String type = filterType(property)
                if (type == null) {
                    return
                }
                Map<String, Object> definition = [
                    name     : property.name,
                    type     : type,
                    className: property.typeName
                ]
                if (property.relationship) {
                    definition.targetClass = property.typeName
                }
                definitions[property.name] = definition
            }
        return definitions
    }

    private static String filterType(Map<String, Object> property) {
        if (property.relationship) {
            return "relationship"
        }
        if (property.enum) {
            return "enum"
        }
        Class type = property.type as Class
        if (type == null) {
            String coreType = staticCoreType(property)
            return coreType in ["TEXT", "MTEXT"] ? "text" : null
        }
        if (CharSequence.isAssignableFrom(type)) {
            return "text"
        }
        if (Number.isAssignableFrom(type)) {
            return "number"
        }
        if (type == Boolean || type == Boolean.TYPE) {
            return "boolean"
        }
        if (Date.isAssignableFrom(type) || Temporal.isAssignableFrom(type)
            || type.name in ["java.time.LocalDate", "java.time.LocalDateTime"]) {
            return "date"
        }
        return null
    }

    private static String staticCoreType(Map<String, Object> property) {
        return property.coreType?.toString()
    }

    private static boolean compactScalar(Map<String, Object> property) {
        if (property.geometry || property.collection || property.relationship
            || property.name in ["id", "version"]) {
            return false
        }
        if (property.enum) {
            return true
        }
        Class type = property.type as Class
        if (type == null) {
            return staticCoreType(property) in ["TEXT", "MTEXT", "NUMERIC", "BOOLEAN", "DATE", "DATETIME", "TIME", "ENUM"]
        }
        if (CharSequence.isAssignableFrom(type)) {
            Object maxSize = property.constraints?.maxSize
            return !(maxSize instanceof Number) || maxSize.intValue() <= 255
        }
        return Number.isAssignableFrom(type)
            || type == Boolean || type == Boolean.TYPE
            || Date.isAssignableFrom(type) || Temporal.isAssignableFrom(type)
    }

    private static boolean textProperty(Map<String, Object> property) {
        if (property.relationship || property.geometry || property.collection
            || property.name in ["id", "version"]) {
            return false
        }
        if (property.enum) {
            return false
        }
        Class type = property.type as Class
        return type != null
            ? CharSequence.isAssignableFrom(type)
            : staticCoreType(property) in ["TEXT", "MTEXT"]
    }

    private static List<String> editableFields(List<Map<String, Object>> properties) {
        return properties
            .findAll { it.name != "id" && it.name != "version" }
            .collect { it.name }
    }

    private static List<String> configuredList(Map<String, Object> config,
                                               String key,
                                               List<String> defaults,
                                               Set<String> knownFields,
                                               String iliName,
                                               String section) {
        if (!config.containsKey(key)) {
            return defaults
        }
        List<String> values = normalizeList(config[key]).collect { it.toString() }
        values.each { String field ->
            if (!knownFields.contains(field)) {
                invalidField(iliName, field, section)
            }
        }
        return values.unique()
    }

    private static List<Map<String, Object>> configuredSections(Map<String, Object> config,
                                                                List<Map<String, Object>> defaults,
                                                                Set<String> knownFields,
                                                                String iliName) {
        if (!config.containsKey("sections")) {
            return defaults
        }
        List<?> rawSections = normalizeList(config.sections)
        List<Map<String, Object>> sections = []
        rawSections.eachWithIndex { Object rawSection, int index ->
            Map<String, Object> section = asMap(rawSection)
            String title = section.title?.toString()
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException(
                    "Invalid form.sections[" + index + "] for iliName '" + iliName + "': title is required"
                )
            }
            List<String> fields = normalizeList(section.fields).collect { it.toString() }
            fields.each { String field ->
                if (!knownFields.contains(field)) {
                    invalidField(iliName, field, "form.sections[" + index + "]")
                }
            }
            sections << [title: title, fields: fields.unique()]
        }
        return sections
    }

    private static String requireText(Object value, String iliName, String section) {
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(
                "Invalid " + section + " for iliName '" + iliName + "': value must not be blank"
            )
        }
        return value.toString()
    }

    private static void invalidField(String iliName, String field, String section) {
        throw new IllegalArgumentException(
            "Unknown field '" + field + "' for iliName '" + iliName + "' in " + section
        )
    }

    private static Map<String, Object> staticDomainMap(Class domainType, String fieldName) {
        try {
            def field = domainType.getDeclaredField(fieldName)
            field.accessible = true
            return (field.get(null) ?: [:]) as Map<String, Object>
        } catch (NoSuchFieldException ignored) {
            return [:]
        } catch (IllegalAccessException ignored) {
            return [:]
        }
    }

    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? value as Map<String, Object> : [:]
    }

    private static List<?> normalizeList(Object value) {
        if (value == null) {
            return []
        }
        if (value instanceof Collection) {
            return value as List
        }
        if (value instanceof Map) {
            return [value]
        }
        throw new IllegalArgumentException("Expected a list or map configuration value but got " + value.class.name)
    }
}

package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.generated.InterlisUiRegistry

import java.time.temporal.Temporal
import java.util.regex.Pattern

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
    private static final String DEFAULT_BASE_DATA_TITLE = "Basisdaten"
    private static final String DEFAULT_LINKED_RECORDS_TITLE = "Verknüpfte Datensätze"

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
        List<String> defaultProminentFilters = []
        List<Map<String, Object>> defaultSections = defaultFormSections(properties)

        List<String> columns = configuredList(
            listConfig, "columns", defaultColumns, knownFields, iliName, "list.columns"
        )
        List<Map<String, Object>> searchDefinitions = searchDefinitions(
            listConfig, defaultSearchFields, properties, iliName
        )
        List<String> searchFields = searchDefinitions.collect { it.path as String }
        List<String> sortableColumns = configuredList(
            listConfig, "sortableColumns", columns, knownFields, iliName, "list.sortableColumns"
        )
        if (!sortableColumns.contains("id")) {
            sortableColumns = ["id"] + sortableColumns
        }
        String displayField = configuredDisplayField(listConfig, columns, properties, iliName)
        List<String> displayFields = configuredDisplayFields(listConfig, domainType, properties, iliName)
        List<String> prominentFilters = configuredList(
            listConfig, "prominentFilters", defaultProminentFilters,
            filters.keySet() as Set<String>, iliName, "list.prominentFilters"
        )
        filters = configuredFilterOverrides(listConfig, filters, iliName)
        List<Map<String, Object>> sections = configuredSections(
            formConfig, defaultSections, properties, knownFields, iliName
        )
        sections = localizedDefaultSectionTitles(grailsApplication, sections)
        List<Map<String, Object>> detailSections = detailSections(sections, properties)

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
                searchDefinitions: searchDefinitions,
                sortableColumns  : sortableColumns,
                displayField     : displayField,
                displayFields    : displayFields,
                prominentFilters : prominentFilters,
                filters          : filters
            ],
            form        : [sections: sections],
            detail      : [sections: detailSections],
            fieldMeta   : staticDomainMap(domainType, "interlisFieldMeta"),
            relationships: staticDomainMap(domainType, "interlisRelationshipMeta"),
            geometry    : staticDomainMap(domainType, "geometryMeta")
        ]
    }

    static List<String> displayFieldsFor(def grailsApplication, Class domainType) {
        if (domainType == null) {
            return []
        }
        Map<String, Object> registryEntry = InterlisUiRegistry.domainForClassName(domainType.name)
        if (registryEntry == null) {
            return []
        }
        String iliName = registryEntry.iliName?.toString()
        Map<String, Object> configuredDomain = configuredDomain(grailsApplication, iliName)
        return configuredDisplayFields(
            asMap(configuredDomain.list),
            domainType,
            propertyDescriptors(grailsApplication, domainType),
            iliName
        )
    }

    static String appTitle(def grailsApplication) {
        Object configured = grailsApplication?.config?.ili2grails?.ui?.appTitle
        String configuredText = configured?.toString()
        if (configuredText == null) {
            return "INTERLIS CRUD"
        }
        return configuredText.isBlank() ? "INTERLIS CRUD" : configuredText
    }

    static String appLogo(def grailsApplication) {
        Object configured = grailsApplication?.config?.ili2grails?.ui?.appLogo
        if (configured == null) {
            return null
        }
        String configuredText = configured.toString()
        return configuredText == null || configuredText.isBlank() ? null : configuredText
    }

    static String appLogoIcon(def grailsApplication) {
        String logo = appLogo(grailsApplication)
        if (logo != null) {
            return null
        }
        Object configured = grailsApplication?.config?.ili2grails?.ui?.appLogoIcon
        if (configured == null) {
            return "grid"
        }
        String configuredText = configured.toString()
        return configuredText == null || configuredText.isBlank() ? "grid" : configuredText
    }

    static Map<String, Object> configuredDomainForType(def grailsApplication, Class domainType) {
        Map<String, Object> registryEntry = InterlisUiRegistry.domainForClassName(domainType?.name)
        if (registryEntry == null) {
            return [:]
        }
        return configuredDomain(grailsApplication, registryEntry.iliName?.toString())
    }

    private static Map<String, Object> configuredDomain(def grailsApplication, String iliName) {
        Object rawDomains = grailsApplication?.config?.ili2grails?.ui?.domains
        List<?> domains = normalizeList(rawDomains)
        Map<String, Object> match = [:]

        domains.eachWithIndex { Object rawDomain, int index ->
            Map<String, Object> domain = asMap(rawDomain)
            String configuredIliName = domain?.iliName?.toString()
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
                    className: property.typeName,
                    propertyType: property.type,
                    label    : humanize(property.name),
                    labelCode: null
                ]
                if (property.relationship) {
                    definition.targetClass = property.typeName
                    definition.targetType = property.type
                    definition.targetDisplayField = relationshipDisplayField(property.type)
                    definition.optionUrl = "relationshipOptions"
                }
                if (property.enum) {
                    definition.options = enumOptions(property.type, property.name)
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

    private static List<Map<String, Object>> enumOptions(Class enumType, String fieldName) {
        if (enumType == null || !enumType.isEnum()) {
            return []
        }
        return enumType.enumConstants.collect { Object constant ->
            String value = ((Enum) constant).name()
            [
                value    : value,
                label    : humanize(value),
                labelCode: fieldName + "." + value + ".label"
            ]
        }
    }

    private static String relationshipDisplayField(Class targetType) {
        if (targetType == null) {
            return "id"
        }
        Map<String, Object> displayMeta = staticDomainMap(targetType, "interlisDisplayMeta")
        List<String> configured = normalizeList(displayMeta.displayFields).collect { it.toString() }
        return configured ? configured.first() : "id"
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

    private static List<Map<String, Object>> searchDefinitions(Map<String, Object> config,
                                                                 List<String> defaults,
                                                                 List<Map<String, Object>> properties,
                                                                 String iliName) {
        Set<String> knownFields = properties.collect { it.name as String } as LinkedHashSet<String>
        List<String> fields = configuredList(
            config, "searchFields", defaults, knownFields, iliName, "list.searchFields", true
        )
        Map<String, Map<String, Object>> byName = properties.collectEntries { [(it.name): it] }
        return fields.collect { String path ->
            if (!path.contains('.')) {
                Map<String, Object> property = byName[path]
                if (property == null || !textProperty(property)) {
                    throw new IllegalArgumentException(
                        "Search field '" + path + "' for iliName '" + iliName + "' must be a direct text property"
                    )
                }
                return [path: path, criteriaPath: path, relationship: false, field: path]
            }
            List<String> parts = path.split('\\.') as List<String>
            if (parts.size() != 2 || parts.any { it == null || it.isBlank() }) {
                throw new IllegalArgumentException(
                    "Search path '" + path + "' for iliName '" + iliName +
                        "' must contain exactly one whitelisted relationship hop"
                )
            }
            Map<String, Object> relationship = byName[parts[0]]
            Class targetType = relationship?.type as Class
            if (relationship == null || !relationship.relationship || relationship.collection || targetType == null) {
                throw new IllegalArgumentException(
                    "Search path '" + path + "' for iliName '" + iliName + "' must start with a to-one relationship"
                )
            }
            List<Map<String, Object>> targetProperties = propertyDescriptors(null, targetType)
            Map<String, Object> targetProperty = targetProperties.find { it.name == parts[1] }
            if (targetProperty == null || !textProperty(targetProperty)) {
                throw new IllegalArgumentException(
                    "Search path '" + path + "' for iliName '" + iliName +
                        "' must end in a text property of " + targetType.name
                )
            }
            String alias = parts[0] + "Search"
            return [
                path             : path,
                criteriaPath     : alias + "." + parts[1],
                relationship    : true,
                relationshipField: parts[0],
                field            : parts[1],
                alias            : alias,
                targetClass      : targetType.name
            ]
        }
    }

    private static String configuredDisplayField(Map<String, Object> config,
                                                  List<String> columns,
                                                  List<Map<String, Object>> properties,
                                                  String iliName) {
        String configured = config != null && config.containsKey("displayField")
            ? config.displayField?.toString()
            : null
        String candidate = configured ?: columns.find { it != "id" } ?: "id"
        if (!columns.contains(candidate)) {
            invalidField(iliName, candidate, "list.displayField")
        }
        Map<String, Object> property = properties.find { it.name == candidate }
        if (candidate != "id" && (property == null || property.relationship || property.collection || property.geometry)) {
            throw new IllegalArgumentException(
                "Display field '" + candidate + "' for iliName '" + iliName + "' must be a scalar list column"
            )
        }
        return candidate
    }

    private static List<String> configuredDisplayFields(Map<String, Object> config,
                                                        Class domainType,
                                                        List<Map<String, Object>> properties,
                                                        String iliName) {
        if (config == null || !config.containsKey("displayFields")) {
            return displayFields(domainType, properties)
        }
        List<String> configured = normalizeList(config.displayFields).collect { it.toString() }
        if (configured.isEmpty() || configured.size() > 2) {
            throw new IllegalArgumentException(
                "list.displayFields for iliName '${iliName}' must contain one or two fields"
            )
        }
        Set<String> knownFields = properties.collect { it.name as String } as LinkedHashSet<String>
        knownFields.add("id")
        configured.each { String field ->
            if (!knownFields.contains(field)) {
                invalidField(iliName, field, "list.displayFields")
            }
            if (field == "version") {
                throw new IllegalArgumentException(
                    "Display field '${field}' for iliName '${iliName}' is not a valid scalar field"
                )
            }
            if (field == "id") {
                return
            }
            Map<String, Object> property = properties.find { it.name == field }
            if (property == null || !compactScalar(property)) {
                throw new IllegalArgumentException(
                    "Display field '${field}' for iliName '${iliName}' must be a direct scalar field"
                )
            }
        }
        return configured.unique()
    }

    private static Map<String, Map<String, Object>> configuredFilterOverrides(Map<String, Object> config,
                                                                                Map<String, Map<String, Object>> defaults,
                                                                                String iliName) {
        if (config == null || !config.containsKey("filters")) {
            return defaults
        }
        Map<String, Object> overrides = asMap(config.filters)
        overrides.each { String field, Object rawOverride ->
            if (!defaults.containsKey(field)) {
                invalidField(iliName, field, "list.filters")
            }
            Map<String, Object> override = asMap(rawOverride)
            Map<String, Object> definition = defaults[field]
            if (override.containsKey("type") && override.type?.toString() != definition.type) {
                throw new IllegalArgumentException(
                    "Filter type for '" + field + "' in iliName '" + iliName + "' cannot change from " + definition.type
                )
            }
            ["label", "labelCode"].each { String key ->
                if (override.containsKey(key)) {
                    definition[key] = requireText(override[key], iliName, "list.filters." + field + "." + key)
                }
            }
        }
        return defaults
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

    private static List<String> editableFormFields(List<Map<String, Object>> properties) {
        return properties
            .findAll {
                it.name != "id" && it.name != "version"
                    && it.geometry != true && it.collection != true
            }
            .collect { it.name }
    }

    private static List<Map<String, Object>> defaultFormSections(List<Map<String, Object>> properties) {
        List<Map<String, Object>> sections = []
        List<String> fields = properties
            .findAll { editableFormField(it) }
            .collect { it.name }

        if (!fields.isEmpty()) {
            sections << [title: DEFAULT_BASE_DATA_TITLE, fields: fields]
        }
        return sections
    }

    private static boolean editableScalarFormField(Map<String, Object> property) {
        return editableFormField(property) && property.relationship != true
    }

    private static boolean editableRelationshipFormField(Map<String, Object> property) {
        return editableFormField(property) && property.relationship == true
    }

    private static boolean editableFormField(Map<String, Object> property) {
        return property.name != "id" && property.name != "version"
            && property.geometry != true && property.collection != true
    }

    private static List<String> configuredList(Map<String, Object> config,
                                               String key,
                                               List<String> defaults,
                                               Set<String> knownFields,
                                               String iliName,
                                               String section,
                                               boolean allowSearchPaths = false) {
        Object configuredValue = configuredValue(config, key)
        if (configuredValue == null) {
            return defaults
        }
        List<String> values = normalizeList(configuredValue).collect { it.toString() }
        values.each { String field ->
            if (!knownFields.contains(field) && !(allowSearchPaths && field.contains('.'))) {
                invalidField(iliName, field, section)
            }
        }
        return values.unique()
    }

    private static List<Map<String, Object>> configuredSections(Map<String, Object> config,
                                                                List<Map<String, Object>> defaults,
                                                                List<Map<String, Object>> properties,
                                                                Set<String> knownFields,
                                                                String iliName) {
        Object configuredValue = configuredValue(config, "sections")
        if (configuredValue == null) {
            return defaults
        }
        Set<String> editableFields = editableFormFields(properties) as LinkedHashSet<String>
        List<?> rawSections = normalizeList(configuredValue)
        List<Map<String, Object>> sections = []
        Set<String> coveredFields = new LinkedHashSet<String>()
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
            List<String> sectionFields = fields.unique().findAll { String field ->
                editableFields.contains(field) && !coveredFields.contains(field)
            }
            coveredFields.addAll(sectionFields)
            if (!sectionFields.isEmpty()) {
                sections << [title: title, fields: sectionFields]
            }
        }
        List<String> remainingFields = editableFields.findAll { !coveredFields.contains(it) }.toList()
        if (!remainingFields.isEmpty()) {
            sections << [title: "Weitere Felder", fields: remainingFields]
        }
        if (sections.isEmpty()) {
            sections = [[title: "Allgemein", fields: []]]
        }
        return sections
    }

    private static Object configuredValue(Map<String, Object> config, String key) {
        if (config == null) {
            return null
        }
        if (config.containsKey(key)) {
            return config[key]
        }

        Pattern indexedKey = Pattern.compile(Pattern.quote(key) + "\\[(\\d+)]")
        Map<Integer, Object> indexedValues = [:]
        config.each { Object rawKey, Object value ->
            def matcher = indexedKey.matcher(rawKey.toString())
            if (matcher.matches()) {
                indexedValues[Integer.valueOf(matcher.group(1))] = value
            }
        }
        return indexedValues.isEmpty()
            ? null
            : indexedValues.keySet().sort().collect { Integer index -> indexedValues[index] }
    }

    private static List<Map<String, Object>> detailSections(List<Map<String, Object>> sections,
                                                            List<Map<String, Object>> properties) {
        Set<String> scalarFields = properties
            .findAll { scalarDetailProperty(it) }
            .collect { it.name as String } as LinkedHashSet<String>

        List<Map<String, Object>> configured = sections.collect { Map<String, Object> section ->
            [
                title : section.title,
                fields: (section.fields ?: []).collect { it.toString() }
                    .findAll { scalarFields.contains(it) }
                    .unique()
            ]
        }.findAll { Map<String, Object> section -> !section.fields.isEmpty() }

        if (configured.isEmpty()) {
            return scalarFields.isEmpty()
                ? []
                : [[title: DEFAULT_BASE_DATA_TITLE, fields: scalarFields.toList()]]
        }

        Set<String> coveredFields = new LinkedHashSet<String>()
        configured.each { Map<String, Object> section ->
            coveredFields.addAll(section.fields as Collection<String>)
        }
        List<String> remainingFields = scalarFields.findAll { !coveredFields.contains(it) }.toList()
        if (!remainingFields.isEmpty()) {
            configured << [title: DEFAULT_BASE_DATA_TITLE, fields: remainingFields]
        }
        return configured
    }

    private static List<Map<String, Object>> localizedDefaultSectionTitles(def grailsApplication,
                                                                            List<Map<String, Object>> sections) {
        return sections.collect { Map<String, Object> section ->
            String title = section.title?.toString()
            String localizedTitle = localizedDefaultSectionTitle(grailsApplication, title)
            [title: localizedTitle, fields: section.fields]
        }
    }

    private static String localizedDefaultSectionTitle(def grailsApplication, String title) {
        if (title == DEFAULT_BASE_DATA_TITLE) {
            return localizedMessage(grailsApplication, "ili2grails.ui.baseData", "Basisdaten", "Basic data")
        }
        if (title == DEFAULT_LINKED_RECORDS_TITLE) {
            return localizedMessage(
                grailsApplication,
                "ili2grails.ui.linkedRecords",
                "Verknüpfte Datensätze",
                "Linked records"
            )
        }
        if (title == "Allgemein") {
            return localizedMessage(grailsApplication, "ili2grails.form.general", "Allgemein", "General")
        }
        if (title == "Weitere Felder") {
            return localizedMessage(
                grailsApplication,
                "ili2grails.form.additionalFields",
                "Weitere Felder",
                "Additional fields"
            )
        }
        return title
    }

    private static String localizedMessage(def grailsApplication,
                                           String code,
                                           String germanDefault,
                                           String englishDefault) {
        String language = grailsApplication?.config?.ili2grails?.language?.toString()
        String fallback = language == "en" ? englishDefault : germanDefault
        try {
            def source = grailsApplication?.mainContext?.getBean(
                "org.springframework.context.MessageSource"
            )
            return source?.getMessage(code, null, fallback, Locale.forLanguageTag(language ?: "de-CH")) ?: fallback
        } catch (Exception ignored) {
            return fallback
        }
    }

    private static boolean scalarDetailProperty(Map<String, Object> property) {
        return !(property?.name in ["id", "version"])
            && property?.geometry != true
            && property?.collection != true
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

    static Map<String, Object> staticDomainMap(Class domainType, String fieldName) {
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
            return value.isEmpty() ? [] : [value]
        }
        throw new IllegalArgumentException("Expected a list or map configuration value but got " + value.class.name)
    }
}

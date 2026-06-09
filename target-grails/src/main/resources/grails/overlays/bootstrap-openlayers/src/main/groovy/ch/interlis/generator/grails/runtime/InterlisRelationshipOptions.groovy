package ch.interlis.generator.grails.runtime

final class InterlisRelationshipOptions {

    private static final List<String> DISPLAY_FIELD_PREFERENCES = [
        "name",
        "bezeichnung",
        "label",
        "title",
        "code",
        "ident"
    ]

    private InterlisRelationshipOptions() {
    }

    static List<String> relationshipFields(def grailsApplication, Class domainType, Collection<String> geometryFields) {
        Set<String> excluded = new LinkedHashSet<>(geometryFields ?: [])
        excluded.add("id")
        excluded.add("version")
        return persistentProperties(grailsApplication, domainType)
            .findAll { property -> isEditableRelationshipProperty(grailsApplication, property, excluded) }
            .collect { property -> propertyName(property) }
            .sort()
    }

    static Map<String, Object> optionPage(def grailsApplication,
                                          Class domainType,
                                          String field,
                                          String query,
                                          Integer max,
                                          Integer offset,
                                          Collection<String> geometryFields) {
        Class targetType = relationshipTargetType(grailsApplication, domainType, field)
        if (targetType == null) {
            return [results: [], pagination: [more: false, total: 0, nextOffset: offset]]
        }
        Collection<String> targetGeometryFields = geometryFieldsFor(targetType)
        List<String> displayFields = displayFieldsFor(grailsApplication, targetType)
        List<String> searchColumns = searchFieldsFor(grailsApplication, targetType, targetGeometryFields, displayFields)
        String sortField = sortableFieldFor(grailsApplication, targetType, displayFields)
        Map<String, Object> pagination = [
            max: max,
            offset: offset,
            sort: sortField ?: "id",
            order: "asc"
        ]
        List<Object> records
        Number total
        if (query != null && !searchColumns.isEmpty()) {
            String pattern = "%" + query + "%"
            def results = targetType.createCriteria().list(
                max: pagination.max,
                offset: pagination.offset,
                sort: pagination.sort,
                order: pagination.order
            ) {
                or {
                    searchColumns.each { String column ->
                        ilike(column, pattern)
                    }
                }
            }
            records = results as List<Object>
            total = results.totalCount ?: records.size()
        } else if (query != null) {
            records = []
            total = 0
        } else {
            records = targetType.list(pagination) as List<Object>
            total = targetType.count()
        }
        List<Map<String, String>> options = records.collect { Object record ->
            [
                id: record?.id?.toString(),
                label: optionLabel(record, displayFields)
            ]
        }.findAll { Map<String, String> option ->
            option.id != null
        }
        return [
            results: options,
            pagination: [
                more: pagination.offset + options.size() < total,
                total: total,
                nextOffset: pagination.offset + options.size()
            ]
        ]
    }

    static String displayLabel(Object value) {
        if (value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return null
        }
        if (readDisplayProperty(value, "id") == null
            && readDisplayProperty(value, "name") == null
            && readDisplayProperty(value, "bezeichnung") == null
            && readDisplayProperty(value, "label") == null
            && readDisplayProperty(value, "title") == null
            && readDisplayProperty(value, "code") == null
            && readDisplayProperty(value, "ident") == null) {
            return null
        }
        return optionLabel(value, displayFieldsFor(value.getClass()))
    }

    static String optionLabel(Object value) {
        return optionLabel(value, displayFieldsFor(value?.getClass()))
    }

    static String optionLabel(Object value, Collection<String> displayFields) {
        if (value == null) {
            return ""
        }
        List<String> parts = []
        (displayFields ?: []).take(2).each { String propertyName ->
            Object propertyValue = readDisplayProperty(value, propertyName)
            if (propertyValue != null && !propertyValue.toString().isBlank()) {
                parts.add(propertyValue.toString())
            }
        }
        if (!parts.isEmpty()) {
            return parts.join(" - ")
        }
        for (String propertyName : DISPLAY_FIELD_PREFERENCES) {
            Object propertyValue = readDisplayProperty(value, propertyName)
            if (propertyValue != null && !propertyValue.toString().isBlank()) {
                return propertyValue.toString()
            }
        }
        Object id = readDisplayProperty(value, "id")
        if (id != null) {
            return id.toString()
        }
        return value.toString()
    }

    private static boolean isEditableRelationshipProperty(def grailsApplication, def property, Set<String> excluded) {
        String name = propertyName(property)
        if (property == null || name == null || excluded.contains(name)) {
            return false
        }
        Class type = propertyType(property)
        if (!associationProperty(property) && !persistentEntityType(grailsApplication, type)) {
            return false
        }
        if (booleanProperty(property, "isOneToMany", "oneToMany")
            || booleanProperty(property, "isManyToMany", "manyToMany")) {
            return false
        }
        return type != null
    }

    private static List<String> displayFieldsFor(def grailsApplication, Class targetType) {
        Map<String, Object> displayMeta = staticDomainMap(targetType, "interlisDisplayMeta") as Map<String, Object>
        List<String> configured = asStringList(displayMeta?.get("displayFields"))
        List<String> persistent = persistentPropertyNames(grailsApplication, targetType)
        List<String> validConfigured = configured.findAll { String field -> persistent.contains(field) }
        if (!validConfigured.isEmpty()) {
            return validConfigured
        }
        return fallbackDisplayFields(grailsApplication, targetType)
    }

    private static List<String> displayFieldsFor(Class targetType) {
        Map<String, Object> displayMeta = staticDomainMap(targetType, "interlisDisplayMeta") as Map<String, Object>
        return asStringList(displayMeta?.get("displayFields"))
    }

    private static List<String> searchFieldsFor(def grailsApplication,
                                                Class targetType,
                                                Collection<String> geometryFields,
                                                Collection<String> displayFields) {
        Map<String, Object> displayMeta = staticDomainMap(targetType, "interlisDisplayMeta") as Map<String, Object>
        List<String> configured = asStringList(displayMeta?.get("searchFields"))
        List<String> candidates = []
        candidates.addAll(displayFields ?: [])
        candidates.addAll(configured)
        candidates.addAll(InterlisTableModel.searchableColumns(grailsApplication, targetType, geometryFields))
        return candidates.findAll { String field ->
            textPersistentProperty(grailsApplication, targetType, field)
        }.unique()
    }

    private static String sortableFieldFor(def grailsApplication, Class targetType, Collection<String> displayFields) {
        List<String> persistent = persistentPropertyNames(grailsApplication, targetType)
        return (displayFields ?: []).find { String field -> persistent.contains(field) }
    }

    private static Collection<String> geometryFieldsFor(Class targetType) {
        Map<String, Object> geometryMeta = staticDomainMap(targetType, "geometryMeta") as Map<String, Object>
        return geometryMeta.keySet().collect { it.toString() }
    }

    private static List<String> fallbackDisplayFields(def grailsApplication, Class targetType) {
        List<String> persistent = persistentPropertyNames(grailsApplication, targetType)
        List<String> preferred = DISPLAY_FIELD_PREFERENCES.collectMany { String preference ->
            persistent.findAll { String field -> normalizedName(field) == preference }
        }
        if (!preferred.isEmpty()) {
            return preferred.unique().take(2)
        }
        return persistent.findAll { String field ->
            textPersistentProperty(grailsApplication, targetType, field)
        }.take(2)
    }

    private static List<String> persistentPropertyNames(def grailsApplication, Class targetType) {
        return persistentProperties(grailsApplication, targetType)
            .collect { property -> propertyName(property) }
            .findAll { String name -> name != null && name != "id" && name != "version" }
    }

    private static boolean textPersistentProperty(def grailsApplication, Class targetType, String field) {
        def property = persistentProperties(grailsApplication, targetType).find { candidate ->
            propertyName(candidate) == field
        }
        Class type = propertyType(property)
        return type != null && CharSequence.isAssignableFrom(type)
    }

    private static Class relationshipTargetType(def grailsApplication, Class domainType, String field) {
        def property = persistentProperties(grailsApplication, domainType).find { candidate ->
            propertyName(candidate) == field
        }
        return propertyType(property)
    }

    private static Collection persistentProperties(def grailsApplication, Class targetType) {
        if (targetType == null) {
            return []
        }
        def entity = grailsApplication?.mappingContext?.getPersistentEntity(targetType.name)
        if (entity?.persistentProperties != null) {
            return entity.persistentProperties
        }
        def domainClass = grailsApplication?.getDomainClass(targetType.name)
        try {
            return domainClass?.persistentProperties ?: []
        } catch (MissingPropertyException ignored) {
            return []
        }
    }

    private static String propertyName(def property) {
        return property?.name?.toString()
    }

    private static Class propertyType(def property) {
        def type = property?.type
        return type instanceof Class ? type as Class : null
    }

    private static boolean associationProperty(def property) {
        return booleanProperty(property, "isAssociation", "association")
    }

    private static boolean persistentEntityType(def grailsApplication, Class type) {
        return type != null && grailsApplication?.mappingContext?.getPersistentEntity(type.name) != null
    }

    private static boolean booleanProperty(def property, String methodName, String propertyName) {
        if (property == null) {
            return false
        }
        try {
            return property."${methodName}"() == true
        } catch (MissingMethodException ignored) {
            return property.hasProperty(propertyName) != null && property."${propertyName}" == true
        }
    }

    private static Object readDisplayProperty(Object value, String propertyName) {
        if (value == null || propertyName == null) {
            return null
        }
        try {
            if (value.hasProperty(propertyName) == null) {
                return null
            }
            return value."${propertyName}"
        } catch (Exception ignored) {
            return null
        }
    }

    private static Map staticDomainMap(Class domainType, String fieldName) {
        if (domainType == null || fieldName == null) {
            return [:]
        }
        try {
            def field = domainType.getDeclaredField(fieldName)
            field.accessible = true
            return (field.get(null) ?: [:]) as Map
        } catch (NoSuchFieldException ignored) {
            return [:]
        } catch (IllegalAccessException ignored) {
            return [:]
        }
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof Collection)) {
            return []
        }
        return value.collect { it?.toString() }
            .findAll { String field -> field != null && !field.isBlank() }
            .unique()
    }

    private static String normalizedName(String value) {
        return value == null ? "" : value.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT)
    }
}

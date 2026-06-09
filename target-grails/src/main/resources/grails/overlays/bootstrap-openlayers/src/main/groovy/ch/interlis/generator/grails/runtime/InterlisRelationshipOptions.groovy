package ch.interlis.generator.grails.runtime

final class InterlisRelationshipOptions {

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
            return [results: [], pagination: [more: false, total: 0]]
        }
        Map<String, Object> pagination = [
            max: max,
            offset: offset,
            sort: "id",
            order: "asc"
        ]
        List<Object> records
        Number total
        List<String> searchColumns = InterlisTableModel.searchableColumns(grailsApplication, targetType, geometryFields)
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
        } else {
            records = targetType.list(pagination) as List<Object>
            total = targetType.count()
        }
        List<Map<String, String>> options = records.collect { Object record ->
            [
                id: record?.id?.toString(),
                label: optionLabel(record)
            ]
        }.findAll { Map<String, String> option ->
            option.id != null
        }.sort { Map<String, String> left, Map<String, String> right ->
            String leftLabel = left.label?.toLowerCase(Locale.ROOT) ?: ""
            String rightLabel = right.label?.toLowerCase(Locale.ROOT) ?: ""
            int labelCompare = leftLabel <=> rightLabel
            labelCompare != 0 ? labelCompare : ((left.id ?: "") <=> (right.id ?: ""))
        }
        return [
            results: options,
            pagination: [
                more: pagination.offset + options.size() < total,
                total: total
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
            && readDisplayProperty(value, "title") == null) {
            return null
        }
        return optionLabel(value)
    }

    static String optionLabel(Object value) {
        if (value == null) {
            return ""
        }
        for (String propertyName : ["name", "bezeichnung", "label", "title"]) {
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
}

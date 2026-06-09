package ch.interlis.generator.grails.runtime

final class InterlisRelationshipOptions {

    private InterlisRelationshipOptions() {
    }

    static List<String> relationshipFields(def grailsApplication, Class domainType, Collection<String> geometryFields) {
        def domainClass = grailsApplication?.getDomainClass(domainType.name)
        if (domainClass == null) {
            return []
        }
        Set<String> excluded = new LinkedHashSet<>(geometryFields ?: [])
        excluded.add("id")
        excluded.add("version")
        return domainClass.persistentProperties
            .findAll { property -> isEditableRelationshipProperty(property, excluded) }
            .collect { property -> property.name.toString() }
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

    private static boolean isEditableRelationshipProperty(def property, Set<String> excluded) {
        if (property == null || property.name == null || excluded.contains(property.name.toString())) {
            return false
        }
        if (!property.isAssociation()) {
            return false
        }
        if (property.isOneToMany() || property.isManyToMany()) {
            return false
        }
        return property.type instanceof Class
    }

    private static Class relationshipTargetType(def grailsApplication, Class domainType, String field) {
        def domainClass = grailsApplication?.getDomainClass(domainType.name)
        def property = domainClass?.persistentProperties?.find { candidate ->
            candidate.name?.toString() == field
        }
        return property?.type instanceof Class ? property.type : null
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

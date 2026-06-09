package ch.interlis.generator.grails.runtime

final class InterlisTableModel {

    private InterlisTableModel() {
    }

    static List<String> tableColumns(def grailsApplication, Class domainType, Collection<String> geometryFields) {
        Map<String, Object> constrained = (domainType.constrainedProperties ?: [:]) as Map<String, Object>
        List<String> columns = constrained.keySet().collect { it.toString() }
        Set<String> excluded = new LinkedHashSet<>(geometryFields ?: [])
        excluded.add("version")
        columns = columns.findAll { String column -> !excluded.contains(column) }
        if (columns.remove("id")) {
            columns.add(0, "id")
        }
        return columns
    }

    static List<String> searchableColumns(def grailsApplication, Class targetType, Collection<String> geometryFields) {
        def domainClass = targetType != null ? grailsApplication?.getDomainClass(targetType.name) : null
        if (domainClass == null) {
            return []
        }
        Set<String> excluded = new LinkedHashSet<>(geometryFields ?: [])
        excluded.add("id")
        excluded.add("version")
        return domainClass.persistentProperties
            .findAll { property ->
                property?.name != null
                    && !excluded.contains(property.name.toString())
                    && !property.isAssociation()
                    && property.type instanceof Class
                    && CharSequence.isAssignableFrom(property.type)
            }
            .collect { property -> property.name.toString() }
            .sort()
    }

    static List<Map<String, Object>> filterableColumns(def grailsApplication,
                                                       Class targetType,
                                                       Collection<String> geometryFields) {
        def domainClass = targetType != null ? grailsApplication?.getDomainClass(targetType.name) : null
        if (domainClass == null) {
            return []
        }
        Set<String> excluded = new LinkedHashSet<>(geometryFields ?: [])
        excluded.add("id")
        excluded.add("version")
        return domainClass.persistentProperties
            .findAll { property ->
                property?.name != null
                    && !excluded.contains(property.name.toString())
                    && !property.isAssociation()
                    && property.type instanceof Class
            }
            .collect { property ->
                [
                    name: property.name.toString(),
                    type: filterType(property.type as Class),
                    className: (property.type as Class).name
                ]
            }
            .sort { left, right -> left.name <=> right.name }
    }

    static Map<String, Map<String, Object>> filterDefinitions(def grailsApplication,
                                                              Class targetType,
                                                              Collection<String> geometryFields) {
        return filterableColumns(grailsApplication, targetType, geometryFields)
            .collectEntries { Map<String, Object> field -> [(field.name): field] }
    }

    private static String filterType(Class type) {
        if (type == null) {
            return "text"
        }
        if (CharSequence.isAssignableFrom(type)) {
            return "text"
        }
        if (Number.isAssignableFrom(type)) {
            return "number"
        }
        if (Boolean.isAssignableFrom(type) || type == Boolean.TYPE) {
            return "boolean"
        }
        if (Enum.isAssignableFrom(type)) {
            return "enum"
        }
        if (Date.isAssignableFrom(type) || type.name == "java.time.LocalDate") {
            return "date"
        }
        return "text"
    }
}

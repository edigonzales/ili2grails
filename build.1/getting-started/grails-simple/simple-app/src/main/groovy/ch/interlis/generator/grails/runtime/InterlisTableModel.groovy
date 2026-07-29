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
        Set<String> excluded = new LinkedHashSet<>(geometryFields ?: [])
        excluded.add("id")
        excluded.add("version")
        return persistentProperties(grailsApplication, targetType)
            .findAll { property ->
                String name = propertyName(property)
                Class type = propertyType(property)
                name != null
                    && !excluded.contains(name)
                    && !relationshipProperty(grailsApplication, property)
                    && type != null
                    && CharSequence.isAssignableFrom(type)
            }
            .collect { property -> propertyName(property) }
            .sort()
    }

    static List<Map<String, Object>> filterableColumns(def grailsApplication,
                                                       Class targetType,
                                                       Collection<String> geometryFields) {
        Set<String> excluded = new LinkedHashSet<>(geometryFields ?: [])
        excluded.add("id")
        excluded.add("version")
        return persistentProperties(grailsApplication, targetType)
            .findAll { property ->
                String name = propertyName(property)
                Class type = propertyType(property)
                name != null
                    && !excluded.contains(name)
                    && !relationshipProperty(grailsApplication, property)
                    && type != null
            }
            .collect { property ->
                Class type = propertyType(property)
                [
                    name: propertyName(property),
                    type: filterType(type),
                    className: type.name
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
        try {
            return property?.isAssociation() == true
        } catch (MissingMethodException ignored) {
            return property?.hasProperty("association") != null && property.association == true
        }
    }

    private static boolean relationshipProperty(def grailsApplication, def property) {
        Class type = propertyType(property)
        return associationProperty(property)
            || (type != null && grailsApplication?.mappingContext?.getPersistentEntity(type.name) != null)
    }
}

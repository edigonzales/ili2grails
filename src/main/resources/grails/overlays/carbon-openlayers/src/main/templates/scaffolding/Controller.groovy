<%=packageName ? "package ${packageName}" : ''%>

import grails.validation.ValidationException
import org.locationtech.jts.io.WKTReader

import java.time.temporal.TemporalAccessor

import static org.springframework.http.HttpStatus.*

class ${className}Controller {

    ${className}Service ${propertyName}Service

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index() {
        List<${className}> records = ${propertyName}Service.list([:])
        List<String> columns = tableColumns()
        respond records, model: [
            ${propertyName}Count: records.size(),
            tableColumns: columns,
            tableRows: tableRows(records, columns)
        ]
    }

    def show(Long id) {
        def ${propertyName} = ${propertyName}Service.get(id)
        if (${propertyName} == null) {
            notFound()
            return
        }
        Map<String, Object> model = [:]
        model.putAll(geometryModel(${propertyName}))
        model.putAll(detailModel(${propertyName}))
        respond ${propertyName}, model: model
    }

    def create() {
        def ${propertyName} = new ${className}(params)
        bindGeometryFromParams(${propertyName})
        respond ${propertyName}, model: geometryModel(${propertyName})
    }

    def save(${className} ${propertyName}) {
        if (${propertyName} == null) {
            notFound()
            return
        }

        bindGeometryFromParams(${propertyName})
        if (${propertyName}.hasErrors()) {
            respond ${propertyName}.errors, view:'create', model: geometryModel(${propertyName})
            return
        }

        try {
            ${propertyName}Service.save(${propertyName})
        } catch (ValidationException e) {
            respond ${propertyName}.errors, view:'create', model: geometryModel(${propertyName})
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: '${propertyName}.label', default: '${className}'), ${propertyName}.id])
                redirect ${propertyName}
            }
            '*' { respond ${propertyName}, [status: CREATED] }
        }
    }

    def edit(Long id) {
        def ${propertyName} = ${propertyName}Service.get(id)
        if (${propertyName} == null) {
            notFound()
            return
        }
        respond ${propertyName}, model: geometryModel(${propertyName})
    }

    def update(${className} ${propertyName}) {
        if (${propertyName} == null) {
            notFound()
            return
        }

        bindGeometryFromParams(${propertyName})
        if (${propertyName}.hasErrors()) {
            respond ${propertyName}.errors, view:'edit', model: geometryModel(${propertyName})
            return
        }

        try {
            ${propertyName}Service.save(${propertyName})
        } catch (ValidationException e) {
            respond ${propertyName}.errors, view:'edit', model: geometryModel(${propertyName})
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: '${propertyName}.label', default: '${className}'), ${propertyName}.id])
                redirect ${propertyName}
            }
            '*'{ respond ${propertyName}, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        ${propertyName}Service.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: '${propertyName}.label', default: '${className}'), id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: '${propertyName}.label', default: '${className}'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }

    private Map<Object, Map<String, String>> tableRows(List<${className}> records, List<String> columns) {
        Map<Object, Map<String, String>> rows = [:]
        records.each { ${className} entity ->
            Map<String, String> values = [:]
            columns.each { String column ->
                values[column] = renderFieldValue(entity?."\${column}")
            }
            rows[entity?.id] = values
        }
        return rows
    }

    private Map<String, Object> detailModel(${className} instance) {
        List<String> columns = tableColumns()
        Map<String, String> values = [:]
        columns.each { String column ->
            values[column] = renderFieldValue(instance?."\${column}")
        }
        return [
            detailColumns: columns,
            detailValues: values
        ]
    }

    private List<String> tableColumns() {
        Map<String, Object> constrained = (${className}.constrainedProperties ?: [:]) as Map<String, Object>
        List<String> columns = constrained.keySet().collect { it.toString() }
        Set<String> excluded = new LinkedHashSet<>(geometryFields())
        excluded.add("version")
        columns = columns.findAll { String column -> !excluded.contains(column) }
        if (columns.remove("id")) {
            columns.add(0, "id")
        }
        return columns
    }

    private String renderFieldValue(Object value) {
        if (value == null) {
            return ""
        }
        if (value instanceof Enum) {
            return ((Enum) value).name()
        }
        if (value instanceof TemporalAccessor) {
            return value.toString()
        }
        if (value instanceof Date) {
            return value.format("yyyy-MM-dd HH:mm:ss")
        }
        if (value instanceof Collection) {
            return ((Collection) value).collect { Object item -> item?.toString() ?: "" }
                .findAll { String item -> item != null && !item.isBlank() }
                .join(", ")
        }
        return value.toString()
    }

    private void bindGeometryFromParams(${className} instance) {
        if (instance == null) {
            return
        }
        List<String> fields = geometryFields()
        if (fields.isEmpty()) {
            return
        }
        WKTReader wktReader = new WKTReader()
        fields.each { String field ->
            String paramName = field + "Wkt"
            if (!params.containsKey(paramName)) {
                return
            }
            String wktValue = params.get(paramName)
            if (wktValue == null || wktValue.trim().isEmpty()) {
                instance."\${field}" = null
                return
            }
            try {
                def geometry = wktReader.read(wktValue)
                Integer srid = geometrySrid(field)
                if (srid != null) {
                    geometry.setSRID(srid)
                }
                String expectedKind = geometryKind(field)
                if (!isGeometryTypeAllowed(geometry, expectedKind)) {
                    instance.errors.rejectValue(
                        field,
                        "default.invalid.geometry.type.message",
                        [field, expectedKind, geometry.getGeometryType()] as Object[],
                        "Invalid geometry type for field \${field}. Expected \${expectedKind}, got \${geometry.getGeometryType()}"
                    )
                    return
                }
                instance."\${field}" = geometry
            } catch (Exception e) {
                instance.errors.rejectValue(
                    field,
                    "default.invalid.geometry.message",
                    [field] as Object[],
                    "Invalid geometry for field \${field}"
                )
            }
        }
    }

    private Map<String, Object> geometryModel(${className} instance) {
        List<String> fields = geometryFields()
        Map<String, String> values = [:]
        Map<String, String> kinds = [:]
        Map<String, Integer> srids = [:]

        fields.each { String field ->
            Object currentValue = instance?."\${field}"
            values[field] = currentValue != null ? currentValue.toText() : ""
            kinds[field] = geometryKind(field)
            srids[field] = geometrySrid(field)
        }

        return [
            geometryFields: fields,
            geometryValues: values,
            geometryKinds: kinds,
            geometrySrids: srids
        ]
    }

    private List<String> geometryFields() {
        Map<String, Map<String, Object>> meta = (${className}.geometryMeta ?: [:]) as Map<String, Map<String, Object>>
        return meta.keySet().collect { it.toString() }.sort()
    }

    private Integer geometrySrid(String field) {
        Map<String, Map<String, Object>> meta = (${className}.geometryMeta ?: [:]) as Map<String, Map<String, Object>>
        Object configuredSrid = meta[field]?.get("srid")
        if (configuredSrid instanceof Number) {
            return ((Number) configuredSrid).intValue()
        }
        return grailsApplication?.config?.getProperty("interlis.geometry.defaultSrid", Integer, 2056)
    }

    private String geometryKind(String field) {
        Map<String, Map<String, Object>> meta = (${className}.geometryMeta ?: [:]) as Map<String, Map<String, Object>>
        Object configuredKind = meta[field]?.get("kind")
        return configuredKind != null ? configuredKind.toString() : "GEOMETRY"
    }

    private boolean isGeometryTypeAllowed(def geometry, String expectedKind) {
        if (geometry == null) {
            return true
        }
        String normalizedExpected = normalizeGeometryKind(expectedKind)
        if ("GEOMETRY".equals(normalizedExpected)) {
            return true
        }
        String actualType = geometry.getGeometryType()
        String normalizedActual = normalizeGeometryKind(actualType)
        return normalizedExpected.equals(normalizedActual)
    }

    private String normalizeGeometryKind(String rawKind) {
        if (rawKind == null) {
            return "GEOMETRY"
        }
        String normalized = rawKind.toUpperCase()
        if (normalized.isBlank()) {
            return "GEOMETRY"
        }
        return normalized
    }
}

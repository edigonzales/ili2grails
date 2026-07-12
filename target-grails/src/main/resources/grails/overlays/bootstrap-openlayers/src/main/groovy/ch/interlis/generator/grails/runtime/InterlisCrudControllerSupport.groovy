package ch.interlis.generator.grails.runtime

import grails.converters.JSON
import grails.validation.ValidationException
import groovy.util.logging.Slf4j
import org.locationtech.jts.geom.Geometry
import org.springframework.dao.DataIntegrityViolationException

import java.beans.Introspector
import java.time.LocalDate
import java.time.temporal.TemporalAccessor

import static org.springframework.http.HttpStatus.*

@Slf4j
abstract class InterlisCrudControllerSupport<T> {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"

    protected abstract Class<T> domainType()

    protected abstract Object crudService()

    protected abstract Object associationQueryService()

    protected abstract Object associationCommandService()

    def index(Integer max, Integer offset) {
        applySecurityHeaders()
        Map<String, Object> pagination = paginationParams(max, offset)
        String query = normalizedQuery(params.q)
        List<String> columns = tableColumns()
        Map<String, Object> page = pagedRecords(query, pagination)
        List<T> records = page.records as List<T>
        respond records, model: [
            (modelKey() + "List"): records,
            (modelKey() + "Count"): page.total,
            tableColumns: columns,
            tableRows: tableRows(records, columns),
            typedFilters: filterFields(),
            activeFilters: activeFilters(),
            q: query,
            max: pagination.max,
            offset: pagination.offset,
            sort: pagination.sort,
            order: pagination.order
        ]
    }

    def show(Long id) {
        applySecurityHeaders()
        T instance = crudService().get(id) as T
        if (instance == null) {
            notFound()
            return
        }
        Map<String, Object> model = [:]
        model.putAll(geometryModel(instance))
        model.putAll(relationshipModel(instance))
        model.putAll(detailModel(instance))
        model.putAll(associationModel(instance))
        respond instance, model: model
    }

    def create() {
        applySecurityHeaders()
        T instance = domainType().newInstance(domainBindParams()) as T
        Map<String, Object> contextState = associationContextState(instance)
        if (!contextState.isEmpty()) {
            applyAssociationContext(instance, contextState)
        }
        InterlisGeometryBinder.bindGeometryFromParams(instance, params, geometryMeta(), grailsApplication, this)
        Map<String, Object> model = formModelWithContext(instance, contextState)
        model.put(modelKey(), instance)
        render view: "create", model: model
    }

    def save() {
        applySecurityHeaders()
        T instance = domainType().newInstance(domainBindParams()) as T
        InterlisGeometryBinder.bindGeometryFromParams(instance, params, geometryMeta(), grailsApplication, this)
        Map<String, Object> contextState = loadContextStateFromParams()
        if (!contextState.isEmpty()) {
            applyAssociationContext(instance, contextState)
        }
        if (instance.hasErrors()) {
            respond instance.errors, view: "create", model: formModelWithContext(instance, contextState)
            return
        }

        try {
            crudService().save(instance)
        } catch (ValidationException ignored) {
            respond instance.errors, view: "create", model: formModelWithContext(instance, contextState)
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(
                    code: "default.created.message",
                    args: [message(code: modelKey() + ".label", default: domainType().simpleName), instance.id]
                )
                Map<String, Object> redirectTarget = contextualRedirectTarget(instance, contextState)
                if (redirectTarget != null) {
                    redirect redirectTarget
                } else {
                    redirect instance
                }
            }
            "*" { respond instance, [status: CREATED] }
        }
    }

    def edit(Long id) {
        applySecurityHeaders()
        T instance = crudService().get(id) as T
        if (instance == null) {
            notFound()
            return
        }
        Map<String, Object> contextState = associationContextState(instance)
        respond instance, model: formModelWithContext(instance, contextState)
    }

    def update(Long id) {
        applySecurityHeaders()
        T instance = crudService().get(id) as T
        if (instance == null) {
            notFound()
            return
        }

        bindData(instance, params)
        InterlisGeometryBinder.bindGeometryFromParams(instance, params, geometryMeta(), grailsApplication, this)
        Map<String, Object> contextState = loadContextStateFromParams()
        if (!contextState.isEmpty()) {
            applyAssociationContext(instance, contextState)
        }
        if (instance.hasErrors()) {
            respond instance.errors, view: "edit", model: formModelWithContext(instance, contextState)
            return
        }

        try {
            crudService().save(instance)
        } catch (ValidationException ignored) {
            respond instance.errors, view: "edit", model: formModelWithContext(instance, contextState)
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(
                    code: "default.updated.message",
                    args: [message(code: modelKey() + ".label", default: domainType().simpleName), instance.id]
                )
                Map<String, Object> redirectTarget = contextualRedirectTarget(instance, contextState)
                if (redirectTarget != null) {
                    redirect redirectTarget
                } else {
                    redirect instance
                }
            }
            "*" { respond instance, [status: OK] }
        }
    }

    def delete(Long id) {
        applySecurityHeaders()
        if (id == null) {
            notFound()
            return
        }

        try {
            crudService().delete(id)
        } catch (DataIntegrityViolationException ignored) {
            request.withFormat {
                form multipartForm {
                    flash.message = "Datensatz ${id} kann nicht geloescht werden, weil abhaengige Daten vorhanden sind."
                    redirect action: "index", method: "GET"
                }
                "*" { render status: CONFLICT }
            }
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(
                    code: "default.deleted.message",
                    args: [message(code: modelKey() + ".label", default: domainType().simpleName), id]
                )
                redirect action: "index", method: "GET"
            }
            "*" { render status: NO_CONTENT }
        }
    }

    def relationshipOptions() {
        applySecurityHeaders()
        Map<String, Object> page = relationshipOptionPage(
            params.field?.toString(),
            normalizedQuery(params.q),
            boundedMax(params.int("max")),
            safeOffset(params.int("offset"))
        )
        render page as JSON
    }

    def associationPage(Long id) {
        applySecurityHeaders()
        T instance = crudService().get(id) as T
        if (instance == null) {
            notFound()
            return
        }
        String contextId = params.context?.toString()
        if (contextId == null || contextId.isBlank()) {
            response.status = BAD_REQUEST.value()
            render([error: "context parameter required"] as JSON)
            return
        }
        try {
            Map<String, Object> page = associationQueryService().page(
                domainType(),
                instance.id as java.io.Serializable,
                contextId,
                boundedMax(params.int("max")),
                safeOffset(params.int("offset")),
                params.sort?.toString(),
                params.order?.toString()
            )
            render page as JSON
        } catch (InterlisAssociationRegistrySupport.AssociationContextNotFoundException e) {
            response.status = BAD_REQUEST.value()
            render([error: e.message] as JSON)
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            response.status = BAD_REQUEST.value()
            render([error: e.message] as JSON)
        } catch (Exception e) {
            log.error("associationPage failed for ${domainType().simpleName}#${id} context ${contextId}: ${e.message}", e)
            response.status = INTERNAL_SERVER_ERROR.value()
            render([error: "Fehler beim Laden der Assoziationsdaten."] as JSON)
        }
    }

    def associationOptions(Long id) {
        applySecurityHeaders()
        T instance = crudService().get(id) as T
        if (instance == null) {
            notFound()
            return
        }
        String contextId = params.context?.toString()
        String roleName = params.role?.toString()
        if (contextId == null || contextId.isBlank() || roleName == null || roleName.isBlank()) {
            response.status = BAD_REQUEST.value()
            render([results: [], pagination: [more: false, total: 0, nextOffset: 0]] as JSON)
            return
        }
        try {
            Map<String, Object> page = associationQueryService().optionPage(
                domainType(),
                contextId,
                roleName,
                normalizedQuery(params.q),
                boundedMax(params.int("max")),
                safeOffset(params.int("offset"))
            )
            render page as JSON
        } catch (InterlisAssociationRegistrySupport.AssociationContextNotFoundException e) {
            response.status = BAD_REQUEST.value()
            render([error: e.message] as JSON)
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            response.status = BAD_REQUEST.value()
            render([error: e.message] as JSON)
        } catch (Exception e) {
            log.warn("associationOptions failed for ${domainType().simpleName}#${id} context ${contextId}: ${e.message}", e)
            render([results: [], pagination: [more: false, total: 0, nextOffset: 0]] as JSON)
        }
    }

    def associationCreate(Long id) {
        applySecurityHeaders()
        T instance = crudService().get(id) as T
        if (instance == null) {
            notFound()
            return
        }
        String contextId = params.context?.toString()
        String targetRoleName = params.role?.toString()
        Long targetId = params.long("targetId")
        if (contextId == null || contextId.isBlank()) {
            response.status = BAD_REQUEST.value()
            render([success: false, status: 400, code: "MISSING_CONTEXT",
                    message: "Der Assoziationskontext fehlt."] as JSON)
            return
        }
        Map<String, Object> result
        try {
            result = associationCommandService().createQuickLink(
                domainType(),
                instance.id as java.io.Serializable,
                contextId,
                targetRoleName,
                targetId as java.io.Serializable
            )
        } catch (InterlisAssociationRegistrySupport.AssociationContextNotFoundException e) {
            log.info("associationCreate context not found for ${domainType().simpleName}#${id}: ${e.message}")
            result = [success: false, status: 404, code: "CONTEXT_NOT_FOUND",
                      message: "Der Assoziationskontext wurde nicht gefunden."]
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            log.warn("associationCreate ownership mismatch for ${domainType().simpleName}#${id}: ${e.message}")
            result = [success: false, status: 404, code: "OWNERSHIP_MISMATCH",
                      message: "Die Zuordnung geh&ouml;rt nicht zu diesem Datensatz."]
        } catch (Exception e) {
            log.error("associationCreate failed for ${domainType().simpleName}#${id} context ${contextId}: ${e.message}", e)
            result = [success: false, status: 500, code: "INTERNAL_ERROR",
                      message: "Die Zuordnung konnte nicht erstellt werden."]
        }
        respondAssociationCommand(instance, result)
    }

    def associationDelete(Long id) {
        applySecurityHeaders()
        T instance = crudService().get(id) as T
        if (instance == null) {
            notFound()
            return
        }
        String contextId = params.context?.toString()
        Long associationId = params.long("associationId")
        if (contextId == null || contextId.isBlank() || associationId == null) {
            response.status = BAD_REQUEST.value()
            render([success: false, status: 400, code: "MISSING_PARAMS",
                    message: "Kontext und Assoziations-ID werden ben&ouml;tigt."] as JSON)
            return
        }
        Map<String, Object> result
        try {
            result = associationCommandService().deleteLink(
                domainType(),
                instance.id as java.io.Serializable,
                contextId,
                associationId as java.io.Serializable
            )
        } catch (InterlisAssociationRegistrySupport.AssociationContextNotFoundException e) {
            log.info("associationDelete context not found for ${domainType().simpleName}#${id}: ${e.message}")
            result = [success: false, status: 404, code: "CONTEXT_NOT_FOUND",
                      message: "Der Assoziationskontext wurde nicht gefunden."]
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            log.warn("associationDelete ownership mismatch for ${domainType().simpleName}#${id}: ${e.message}")
            result = [success: false, status: 404, code: "OWNERSHIP_MISMATCH",
                      message: "Die Zuordnung geh&ouml;rt nicht zu diesem Datensatz."]
        } catch (Exception e) {
            log.error("associationDelete failed for ${domainType().simpleName}#${id} context ${contextId}: ${e.message}", e)
            result = [success: false, status: 500, code: "INTERNAL_ERROR",
                      message: "Die Zuordnung kann nicht entfernt werden."]
        }
        respondAssociationCommand(instance, result)
    }

    protected void respondAssociationCommand(T instance, Map<String, Object> result) {
        boolean success = result?.success == true
        int status = (result?.status ?: (success ? 200 : 400)) as int
        String userMessage = result?.message?.toString()
        request.withFormat {
            form multipartForm {
                if (userMessage != null && !userMessage.isBlank()) {
                    flash.message = userMessage
                }
                redirect action: "show", id: instance.id, method: "GET"
            }
            "*" {
                response.status = status
                render result as JSON
            }
        }
    }

    protected void respondAssociationError(int status, String code, String message) {
        request.withFormat {
            form multipartForm {
                flash.message = message
                redirect action: "index", method: "GET"
            }
            "*" {
                response.status = status
                render([success: false, status: status, code: code, message: message] as JSON)
            }
        }
    }

    protected Map<String, Object> associationModel(T instance) {
        if (instance == null) {
            return [associationSections: []]
        }
        try {
            return [
                associationSections: associationQueryService().sections(
                    domainType(),
                    instance.id as java.io.Serializable,
                    associationPageSize()
                )
            ]
        } catch (Exception e) {
            log.warn("associationModel failed for ${domainType().simpleName}#${instance.id}: ${e.message}", e)
            return [associationSections: [], associationDiagnostic: "Assoziationsdaten konnten nicht geladen werden."]
        }
    }

    protected Integer associationPageSize() {
        return 10
    }

    protected void notFound() {
        applySecurityHeaders()
        request.withFormat {
            form multipartForm {
                flash.message = message(
                    code: "default.not.found.message",
                    args: [message(code: modelKey() + ".label", default: domainType().simpleName), params.id]
                )
                redirect action: "index", method: "GET"
            }
            "*" { render status: NOT_FOUND }
        }
    }

    protected void applySecurityHeaders() {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY)
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
    }

    protected Map<String, Object> formModel(T instance) {
        Map<String, Object> model = [:]
        model.putAll(geometryModel(instance))
        model.putAll(relationshipModel(instance))
        model.put("fieldMeta", fieldMeta())
        return model
    }

    protected Map<String, Object> formModelWithContext(T instance, Map<String, Object> contextState) {
        Map<String, Object> model = formModel(instance)
        model.put("hiddenRelationshipFields", [])
        model.put("fixedRelationshipLabels", [:])
        model.put("associationContextState", null)
        if (contextState != null && !contextState.isEmpty()) {
            model.put("hiddenRelationshipFields",
                InterlisAssociationContextSupport.hiddenRelationshipFields(contextState))
            model.put("fixedRelationshipLabels",
                InterlisAssociationContextSupport.fixedRelationshipLabels(contextState))
            model.put("associationContextState", contextState)
        }
        return model
    }

    protected Map<String, Object> associationContextState(T instance) {
        try {
            return InterlisAssociationContextSupport.prepareCreateContext(
                grailsApplication, domainType(), params)
        } catch (Exception e) {
            log.info("Association context not applied for ${domainType().simpleName}: ${e.message}")
            return [:]
        }
    }

    protected void applyAssociationContext(T instance, Map<String, Object> state) {
        InterlisAssociationContextSupport.applyFixedRole(instance, state)
    }

    protected Map<String, Object> contextualRedirectTarget(T instance, Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return null
        }
        return InterlisAssociationContextSupport.redirectTarget(state)
    }

    protected Map<String, Object> loadContextStateFromParams() {
        String contextId = params.associationContext?.toString()
        String ownerIdStr = params.associationOwnerId?.toString()
        if (contextId == null || contextId.isBlank() || ownerIdStr == null || ownerIdStr.isBlank()) {
            return [:]
        }
        try {
            return InterlisAssociationContextSupport.prepareCreateContext(
                grailsApplication, domainType(), params)
        } catch (Exception e) {
            log.warn("Failed to re-load association context for ${domainType().simpleName}: ${e.message}")
            return [:]
        }
    }

    protected Map domainBindParams() {
        Map filtered = new java.util.LinkedHashMap(params)
        filtered.remove("associationContext")
        filtered.remove("associationOwnerId")
        return filtered
    }

    protected Map<String, Object> paginationParams(Integer maxParam, Integer offsetParam) {
        return [
            max: boundedMax(maxParam),
            offset: safeOffset(offsetParam),
            sort: safeSort(params.sort),
            order: safeOrder(params.order)
        ]
    }

    protected Integer boundedMax(Integer value) {
        Integer requested = value ?: 25
        return Math.min(Math.max(requested, 1), 100)
    }

    protected Integer safeOffset(Integer value) {
        return Math.max(value ?: 0, 0)
    }

    protected String safeSort(Object value) {
        String requested = value?.toString()
        if (requested != null && tableColumns().contains(requested)) {
            return requested
        }
        return "id"
    }

    protected String safeOrder(Object value) {
        String requested = value?.toString()?.toLowerCase(Locale.ROOT)
        return requested == "desc" ? "desc" : "asc"
    }

    protected String normalizedQuery(Object value) {
        String query = value?.toString()?.trim()
        return query ?: null
    }

    protected Map<String, Object> pagedRecords(String query, Map<String, Object> pagination) {
        Map<String, Object> filters = activeFilters()
        if (query != null || !filters.isEmpty()) {
            return searchedRecords(query, filters, pagination)
        }
        List<T> records = crudService().list(pagination) as List<T>
        return [
            records: records,
            total: crudService().count()
        ]
    }

    protected Map<String, Object> searchedRecords(String query,
                                                  Map<String, Object> filters,
                                                  Map<String, Object> pagination) {
        List<String> columns = InterlisTableModel.searchableColumns(grailsApplication, domainType(), geometryFields())
        if (query != null && columns.isEmpty() && filters.isEmpty()) {
            return [records: [], total: 0]
        }
        String pattern = query != null ? "%" + query + "%" : null
        Map<String, Map<String, Object>> filterDefinitions = InterlisTableModel.filterDefinitions(
            grailsApplication,
            domainType(),
            geometryFields()
        )
        def results = domainType().createCriteria().list(
            max: pagination.max,
            offset: pagination.offset,
            sort: pagination.sort,
            order: pagination.order
        ) {
            if (pattern != null) {
                or {
                    columns.each { String column ->
                        ilike(column, pattern)
                    }
                }
            }
            filters.each { String field, Object rawValue ->
                Map<String, Object> definition = filterDefinitions[field]
                Object value = coerceFilterValue(rawValue, definition)
                if (value == null) {
                    return
                }
                if ((definition?.type ?: "text") == "text") {
                    ilike(field, "%" + value.toString() + "%")
                } else {
                    eq(field, value)
                }
            }
        }
        return [
            records: results as List<T>,
            total: results.totalCount ?: results.size()
        ]
    }

    protected Map<Object, Map<String, String>> tableRows(List<T> records, List<String> columns) {
        Map<Object, Map<String, String>> rows = [:]
        records.each { T entity ->
            Map<String, String> values = [:]
            columns.each { String column ->
                values[column] = renderFieldValue(entity?."${column}")
            }
            rows[entity?.id] = values
        }
        return rows
    }

    protected Map<String, Object> detailModel(T instance) {
        List<String> columns = tableColumns()
        Map<String, String> values = [:]
        columns.each { String column ->
            values[column] = renderFieldValue(instance?."${column}")
        }
        return [
            detailColumns: columns,
            detailValues: values
        ]
    }

    protected List<String> tableColumns() {
        return InterlisTableModel.tableColumns(grailsApplication, domainType(), geometryFields())
    }

    protected List<Map<String, Object>> filterFields() {
        return InterlisTableModel.filterableColumns(grailsApplication, domainType(), geometryFields())
    }

    protected Map<String, Object> activeFilters() {
        Map<String, Object> filters = [:]
        Object rawFilterParams = params.filter
        if (rawFilterParams == null) {
            return filters
        }
        Map<String, Map<String, Object>> definitions = InterlisTableModel.filterDefinitions(
            grailsApplication,
            domainType(),
            geometryFields()
        )
        definitions.keySet().each { String field ->
            Object value = rawFilterParams[field]
            if (value != null && !value.toString().trim().isEmpty()) {
                filters[field] = value.toString().trim()
            }
        }
        return filters
    }

    protected Object coerceFilterValue(Object value, Map<String, Object> definition) {
        if (value == null) {
            return null
        }
        String type = definition?.type?.toString()
        String className = definition?.className?.toString()
        String raw = value.toString().trim()
        if (raw.isEmpty()) {
            return null
        }
        try {
            if (type == "number") {
                return raw.contains(".") ? new BigDecimal(raw) : Long.valueOf(raw)
            }
            if (type == "boolean") {
                return raw == "true"
            }
            if (type == "date") {
                LocalDate date = LocalDate.parse(raw)
                if (className == "java.sql.Date") {
                    return java.sql.Date.valueOf(date)
                }
                if (className == "java.util.Date") {
                    return java.sql.Date.valueOf(date)
                }
                return date
            }
            return raw
        } catch (Exception ignored) {
            return null
        }
    }

    protected String renderFieldValue(Object value) {
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
            return ((Collection) value).collect { Object item -> renderFieldValue(item) }
                .findAll { String item -> item != null && !item.isBlank() }
                .join(", ")
        }
        if (value instanceof Geometry) {
            return value.geometryType
        }
        String relationshipLabel = InterlisRelationshipOptions.displayLabel(value)
        if (relationshipLabel != null) {
            return relationshipLabel
        }
        return value.toString()
    }

    protected Map<String, Object> relationshipModel(T instance) {
        List<String> fields = relationshipFields()
        Map<String, List<Map<String, String>>> options = [:]
        Map<String, String> values = [:]
        Map<String, Boolean> required = [:]

        fields.each { String field ->
            options[field] = relationshipOptionPage(field, null, 25, 0).results as List<Map<String, String>>
            Map<String, String> selected = selectedRelationshipOption(instance, field)
            if (selected != null && options[field].every { Map<String, String> option -> option.id != selected.id }) {
                options[field] = [selected] + options[field]
            }
            values[field] = selectedRelationshipId(instance, field)
            required[field] = relationshipFieldRequired(field)
        }

        return [
            relationshipFields: fields,
            relationshipOptions: options,
            relationshipValues: values,
            relationshipRequired: required
        ]
    }

    protected List<String> relationshipFields() {
        return InterlisRelationshipOptions.relationshipFields(grailsApplication, domainType(), geometryFields())
    }

    protected Map<String, Object> relationshipOptionPage(String field, String query, Integer max, Integer offset) {
        return InterlisRelationshipOptions.optionPage(
            grailsApplication,
            domainType(),
            field,
            query,
            boundedMax(max),
            safeOffset(offset),
            geometryFields()
        )
    }

    protected String selectedRelationshipId(T instance, String field) {
        if (instance == null || field == null) {
            return null
        }
        Object selected = instance."${field}"
        return selected?.id?.toString()
    }

    protected Map<String, String> selectedRelationshipOption(T instance, String field) {
        if (instance == null || field == null) {
            return null
        }
        Object selected = instance."${field}"
        String id = selected?.id?.toString()
        if (id == null) {
            return null
        }
        return [
            id: id,
            label: InterlisRelationshipOptions.optionLabel(selected)
        ]
    }

    protected boolean relationshipFieldRequired(String field) {
        Object constrained = (domainType().constrainedProperties ?: [:])?.get(field)
        if (constrained == null) {
            return false
        }
        try {
            return constrained.hasProperty("nullable") != null && constrained.nullable == false
        } catch (Exception ignored) {
            return false
        }
    }

    protected Map<String, Object> geometryModel(T instance) {
        List<String> fields = geometryFields()
        Map<String, String> values = [:]
        Map<String, String> kinds = [:]
        Map<String, Integer> srids = [:]

        fields.each { String field ->
            Object currentValue = instance?."${field}"
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

    protected List<String> geometryFields() {
        Map<String, Map<String, Object>> meta = geometryMeta()
        return meta.keySet().collect { it.toString() }.sort()
    }

    protected Integer geometrySrid(String field) {
        Object configuredSrid = geometryMeta()[field]?.get("srid")
        if (configuredSrid instanceof Number) {
            return ((Number) configuredSrid).intValue()
        }
        return grailsApplication?.config?.getProperty("interlis.geometry.defaultSrid", Integer, 2056)
    }

    protected String geometryKind(String field) {
        Object configuredKind = geometryMeta()[field]?.get("kind")
        return configuredKind != null ? configuredKind.toString() : "GEOMETRY"
    }

    protected Map<String, Map<String, Object>> geometryMeta() {
        return staticDomainMap("geometryMeta")
    }

    protected Map<String, Map<String, Object>> fieldMeta() {
        return staticDomainMap("interlisFieldMeta")
    }

    protected Map<String, Map<String, Object>> staticDomainMap(String fieldName) {
        try {
            def field = domainType().getDeclaredField(fieldName)
            field.accessible = true
            return (field.get(null) ?: [:]) as Map<String, Map<String, Object>>
        } catch (NoSuchFieldException ignored) {
            return [:]
        } catch (IllegalAccessException ignored) {
            return [:]
        }
    }

    protected String modelKey() {
        return Introspector.decapitalize(domainType().simpleName)
    }
}

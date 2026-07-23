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
        Map<String, Object> descriptor = uiDescriptor()
        Map<String, Object> listQuery = InterlisListQuerySupport.parse(params, descriptor)
        if (max != null) {
            listQuery.max = boundedMax(max)
        }
        if (offset != null) {
            listQuery.offset = safeOffset(offset)
        }
        listQuery.params = InterlisListQuerySupport.urlParams(listQuery)
        List<String> columns = tableColumns()
        Map<String, Object> page = InterlisListQuerySupport.page(
            crudService(), domainType(), descriptor, listQuery
        )
        List<T> records = page.records as List<T>
        List<Map<String, Object>> filters = filterFields()
        Map<String, Object> filterOptions = relationshipFilterOptions(filters, listQuery)
        listQuery.chips = InterlisListQuerySupport.activeFilterChips(listQuery, filterOptions)
        respond records, model: [
            (modelKey() + "List"): records,
            (modelKey() + "Count"): page.total,
            uiDescriptor: descriptor,
            listQuery: listQuery,
            listQueryWarnings: listQuery.warnings,
            tableColumns: columns,
            tableRows: tableRows(records, columns),
            displayColumn: descriptor.list.displayField,
            typedFilters: filters,
            prominentFilterFields: filters.findAll { listQueryProminent(descriptor, it.name) },
            advancedFilterFields: filters.findAll { !listQueryProminent(descriptor, it.name) },
            filterOptions: filterOptions,
            activeFilters: listQuery.activeFilters,
            activeFilterChips: listQuery.chips,
            domainHasRecords: page.domainHasRecords == true,
            hasActiveListQuery: listQuery.q != null || !listQuery.filters.isEmpty(),
            sortUrls: columns.collectEntries { String column ->
                [(column): InterlisListQuerySupport.sortParams(listQuery, column)]
            },
            pagination: InterlisListQuerySupport.paginationModel(listQuery, page.total as Number),
            q: listQuery.q,
            max: listQuery.max,
            offset: listQuery.offset,
            sort: listQuery.sort,
            order: listQuery.order,
            listUrlParams: listQuery.params
        ]
    }

    def show(Long id) {
        applySecurityHeaders()
        T instance = crudService().get(id) as T
        if (instance == null) {
            notFound()
            return
        }
        Map<String, Object> descriptor = uiDescriptor()
        Map<String, Object> model = [:]
        model.putAll(geometryModel(instance))
        model.putAll(relationshipModel(instance))
        model.putAll(detailModel(instance))
        model.putAll(associationModel(instance))
        model.putAll(InterlisWorkspaceSupport.showModel(grailsApplication, domainType(), instance, descriptor))
        model.put("uiDescriptor", descriptor)
        respond instance, model: model
    }

    def create() {
        applySecurityHeaders()
        T instance = domainType().newInstance(domainBindParams()) as T
        Map<String, Object> contextState = associationContextState(instance)
        if (contextState == null) {
            respondAssociationError(
                BAD_REQUEST.value(), "invalid_association_context",
                "Der Kontext der Assoziation ist ungültig."
            )
            return
        }
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
        String submitMode = InterlisFormSupport.submitMode(params.submitMode)
        T instance = domainType().newInstance(domainBindParams()) as T
        Map<String, Object> contextState = loadContextStateFromParams()
        if (contextState == null) {
            respondAssociationError(
                BAD_REQUEST.value(), "invalid_association_context",
                "Der Kontext der Assoziation ist ungültig."
            )
            return
        }
        if (!contextState.isEmpty()) {
            applyAssociationContext(instance, contextState)
        }
        InterlisGeometryBinder.bindGeometryFromParams(instance, params, geometryMeta(), grailsApplication, this)
        if (instance.hasErrors()) {
            renderValidationForm("create", instance, contextState)
            return
        }

        try {
            crudService().save(instance)
        } catch (ValidationException ignored) {
            renderValidationForm("create", instance, contextState)
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(
                    code: "default.created.message",
                    args: [message(code: modelKey() + ".label", default: domainType().simpleName), instance.id]
                )
                Map<String, Object> redirectTarget = InterlisFormSupport.saveAndContinue(submitMode)
                    ? InterlisFormSupport.continueRedirect(instance, contextState)
                    : contextualRedirectTarget(instance, contextState)
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
        Map<String, Object> contextState = associationContextState(instance, true)
        if (contextState == null) {
            respondAssociationError(
                BAD_REQUEST.value(), "invalid_association_context",
                "Der Kontext der Assoziation ist ungültig oder gehört nicht zum Datensatz."
            )
            return
        }
        respond instance, model: formModelWithContext(instance, contextState)
    }

    def update(Long id) {
        applySecurityHeaders()
        T instance = crudService().get(id) as T
        if (instance == null) {
            notFound()
            return
        }

        String submitMode = InterlisFormSupport.submitMode(params.submitMode)
        Map<String, Object> contextState = loadContextStateFromParams(instance, true)
        if (contextState == null) {
            respondAssociationError(
                BAD_REQUEST.value(), "invalid_association_context",
                "Der Kontext der Assoziation ist ungültig oder gehört nicht zum Datensatz."
            )
            return
        }
        bindData(instance, domainBindParams())
        InterlisGeometryBinder.bindGeometryFromParams(instance, params, geometryMeta(), grailsApplication, this)
        if (!contextState.isEmpty()) {
            applyAssociationContext(instance, contextState)
        }
        if (instance.hasErrors()) {
            renderValidationForm("edit", instance, contextState)
            return
        }

        try {
            crudService().save(instance)
        } catch (ValidationException ignored) {
            renderValidationForm("edit", instance, contextState)
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(
                    code: "default.updated.message",
                    args: [message(code: modelKey() + ".label", default: domainType().simpleName), instance.id]
                )
                Map<String, Object> redirectTarget = InterlisFormSupport.saveAndContinue(submitMode)
                    ? InterlisFormSupport.continueRedirect(instance, contextState)
                    : contextualRedirectTarget(instance, contextState)
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
        } catch (Exception failure) {
            if (!isDeleteIntegrityConflict(failure)) {
                throw failure
            }
            String conflictMessage = InterlisMessageSupport.text(
                grailsApplication,
                "ili2grails.runtime.deleteIntegrity",
                "Datensatz ${id} kann nicht gelöscht werden, weil eine Datenbank-Integritätsbedingung das Löschen verhindert.",
                [id] as Object[]
            )
            request.withFormat {
                form multipartForm {
                    flash.message = conflictMessage
                    redirect action: "index", method: "GET"
                }
                "*" {
                    response.status = CONFLICT.value()
                    render([error: conflictMessage] as JSON)
                }
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

    private static boolean isDeleteIntegrityConflict(Throwable failure) {
        Throwable current = failure
        while (current != null) {
            if (current instanceof DataIntegrityViolationException ||
                current.class.name in [
                    'org.hibernate.exception.ConstraintViolationException',
                    'org.postgresql.util.PSQLException'
                ] && current.message?.toLowerCase()?.contains('foreign key constraint')) {
                return true
            }
            current = current.cause
        }
        return false
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
                    message: InterlisMessageSupport.text(grailsApplication, "ili2grails.association.error.MISSING_CONTEXT", "Der Assoziationskontext fehlt.")] as JSON)
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
                      message: InterlisMessageSupport.text(grailsApplication,
                          "ili2grails.association.error.CONTEXT_NOT_FOUND", "Der Assoziationskontext wurde nicht gefunden.")]
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            log.warn("associationCreate ownership mismatch for ${domainType().simpleName}#${id}: ${e.message}")
            result = [success: false, status: 404, code: "OWNERSHIP_MISMATCH",
                      message: InterlisMessageSupport.text(grailsApplication,
                          "ili2grails.association.error.OWNERSHIP_MISMATCH", "Die Zuordnung gehört nicht zu diesem Datensatz.")]
        } catch (Exception e) {
            log.error("associationCreate failed for ${domainType().simpleName}#${id} context ${contextId}: ${e.message}", e)
            result = [success: false, status: 500, code: "INTERNAL_ERROR",
                      message: InterlisMessageSupport.text(grailsApplication,
                          "ili2grails.association.error.INTERNAL_ERROR", "Die Zuordnung konnte nicht erstellt werden.")]
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
                    message: InterlisMessageSupport.text(grailsApplication,
                        "ili2grails.association.error.MISSING_PARAMS", "Kontext und Assoziations-ID werden benötigt.")] as JSON)
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
                      message: InterlisMessageSupport.text(grailsApplication,
                          "ili2grails.association.error.CONTEXT_NOT_FOUND", "Der Assoziationskontext wurde nicht gefunden.")]
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            log.warn("associationDelete ownership mismatch for ${domainType().simpleName}#${id}: ${e.message}")
            result = [success: false, status: 404, code: "OWNERSHIP_MISMATCH",
                      message: InterlisMessageSupport.text(grailsApplication,
                          "ili2grails.association.error.OWNERSHIP_MISMATCH", "Die Zuordnung gehört nicht zu diesem Datensatz.")]
        } catch (Exception e) {
            log.error("associationDelete failed for ${domainType().simpleName}#${id} context ${contextId}: ${e.message}", e)
            result = [success: false, status: 500, code: "INTERNAL_ERROR",
                      message: InterlisMessageSupport.text(grailsApplication,
                          "ili2grails.association.error.INTERNAL_ERROR", "Die Zuordnung kann nicht entfernt werden.")]
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

    protected Map<String, Object> formModel(T instance, Map sourceParams = params) {
        Map<String, Object> model = InterlisFormSupport.formViewModel(uiDescriptor())
        model.putAll(geometryModel(instance, sourceParams))
        model.putAll(relationshipModel(instance, sourceParams))
        model.put("fieldMeta", fieldMeta())
        return model
    }

    protected Map<String, Object> formModelWithContext(T instance,
                                                      Map<String, Object> contextState,
                                                      Map sourceParams = params) {
        Map<String, Object> model = formModel(instance, sourceParams)
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
        return associationContextState(instance, false)
    }

    protected Map<String, Object> associationContextState(T instance, boolean edit) {
        try {
            if (edit && hasAssociationContext(params)) {
                return InterlisAssociationContextSupport.prepareEditContext(
                    grailsApplication, domainType(), instance, params)
            }
            return InterlisAssociationContextSupport.prepareCreateContext(
                grailsApplication, domainType(), params)
        } catch (Exception e) {
            if (hasAssociationContext(params)) {
                log.warn("Association context rejected for ${domainType().simpleName}: ${e.message}")
                return null
            }
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

    protected Map<String, Object> loadContextStateFromParams(T instance = null, boolean edit = false) {
        String contextId = params.associationContext?.toString()
        String ownerIdStr = params.associationOwnerId?.toString()
        if (contextId == null || contextId.isBlank() || ownerIdStr == null || ownerIdStr.isBlank()) {
            return [:]
        }
        try {
            if (edit && instance != null) {
                return InterlisAssociationContextSupport.prepareEditContext(
                    grailsApplication, domainType(), instance, params)
            }
            return InterlisAssociationContextSupport.prepareCreateContext(
                grailsApplication, domainType(), params)
        } catch (Exception e) {
            log.warn("Failed to re-load association context for ${domainType().simpleName}: ${e.message}")
            if (hasAssociationContext(params)) {
                return null
            }
            return [:]
        }
    }

    protected Map domainBindParams() {
        Set<String> allowedFields = new LinkedHashSet<String>()
        (uiDescriptor()?.form?.sections ?: []).each { Map<String, Object> section ->
            (section.fields ?: []).each { Object field -> allowedFields.add(field.toString()) }
        }
        allowedFields.addAll(relationshipFields())
        allowedFields.add("version")

        Map filtered = new java.util.LinkedHashMap()
        allowedFields.each { String field ->
            if (params.containsKey(field)) {
                filtered[field] = params.get(field)
            } else if (booleanDomainField(field)) {
                // An unchecked HTML checkbox submits no value. Bind its native
                // false value explicitly while keeping the whitelist intact.
                filtered[field] = false
            }
            String nestedId = field + ".id"
            if (params.containsKey(nestedId)) {
                filtered[nestedId] = params.get(nestedId)
            }
            ["day", "month", "year", "hour", "minute", "second"].each { String component ->
                String structuredField = field + "_" + component
                if (params.containsKey(structuredField)) {
                    filtered[structuredField] = params.get(structuredField)
                }
            }
        }
        return filtered
    }

    protected boolean booleanDomainField(String field) {
        try {
            Class fieldType = domainType().getDeclaredField(field).type
            return fieldType == Boolean || fieldType == Boolean.TYPE
        } catch (NoSuchFieldException ignored) {
            return false
        }
    }

    protected boolean hasAssociationContext(Map sourceParams) {
        String contextId = sourceParams?.associationContext?.toString()
        String ownerId = sourceParams?.associationOwnerId?.toString()
        return contextId != null && !contextId.isBlank() && ownerId != null && !ownerId.isBlank()
    }

    protected void renderValidationForm(String viewName,
                                         T instance,
                                         Map<String, Object> contextState) {
        Map<String, Object> model = formModelWithContext(instance, contextState)
        model.put(modelKey(), instance)
        request.withFormat {
            form multipartForm {
                render view: viewName, model: model
            }
            "*" {
                respond instance.errors, [status: BAD_REQUEST]
            }
        }
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
        if (requested != null && (uiDescriptor().list.sortableColumns ?: ["id"]).contains(requested)) {
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
        Map<String, Object> requestParameters = new LinkedHashMap<>(params as Map)
        requestParameters.q = query
        requestParameters.max = pagination.max
        requestParameters.offset = pagination.offset
        requestParameters.sort = pagination.sort
        requestParameters.order = pagination.order
        return InterlisListQuerySupport.page(
            crudService(), domainType(), uiDescriptor(),
            InterlisListQuerySupport.parse(requestParameters, uiDescriptor())
        )
    }

    protected Map<String, Object> searchedRecords(String query,
                                                  Map<String, Object> filters,
                                                  Map<String, Object> pagination) {
        Map<String, Object> requestParameters = [
            q: query,
            filter: filters,
            max: pagination.max,
            offset: pagination.offset,
            sort: pagination.sort,
            order: pagination.order
        ]
        return InterlisListQuerySupport.page(
            crudService(), domainType(), uiDescriptor(),
            InterlisListQuerySupport.parse(requestParameters, uiDescriptor())
        )
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
        List<String> columns = detailColumns()
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
        return (uiDescriptor().list.columns ?: ["id"]).collect { it.toString() }
    }

    protected List<String> detailColumns() {
        return InterlisTableModel.tableColumns(grailsApplication, domainType(), geometryFields())
    }

    protected Map<String, Object> uiDescriptor() {
        return InterlisUiDescriptorSupport.descriptor(grailsApplication, domainType())
    }

    protected List<Map<String, Object>> filterFields() {
        Map<String, Object> definitions = uiDescriptor().list.filters instanceof Map
            ? uiDescriptor().list.filters as Map<String, Object>
            : [:]
        return definitions.values().collect { Map<String, Object> definition ->
            new LinkedHashMap<String, Object>(definition)
        } as List<Map<String, Object>>
    }

    protected Map<String, Object> activeFilters() {
        return InterlisListQuerySupport.parse(params, uiDescriptor()).activeFilters as Map<String, Object>
    }

    protected boolean listQueryProminent(Map<String, Object> descriptor, String field) {
        return (descriptor?.list?.prominentFilters ?: []).collect { it.toString() }.contains(field)
    }

    protected Map<String, Object> relationshipFilterOptions(List<Map<String, Object>> definitions,
                                                             Map<String, Object> query) {
        Map<String, Object> options = [:]
        definitions.findAll { it.type?.toString() == "relationship" }.each { Map<String, Object> definition ->
            String selected = query?.filterValues?.get(definition.name)?.value?.toString()
            options[definition.name] = InterlisListQuerySupport.relationshipOptions(
                grailsApplication, domainType(), definition, selected, 25
            )
        }
        return options
    }

    protected Object coerceFilterValue(Object value, Map<String, Object> definition) {
        return InterlisListQuerySupport.coerceFilterValue(value, definition)
    }

    protected String renderFieldValue(Object value) {
        return InterlisWorkspaceSupport.renderValue(value)
    }

    protected Map<String, Object> relationshipModel(T instance, Map sourceParams = params) {
        List<String> fields = relationshipFields()
        Map<String, List<Map<String, String>>> options = [:]
        Map<String, String> values = [:]
        Map<String, Boolean> required = [:]

        fields.each { String field ->
            options[field] = relationshipOptionPage(field, null, 25, 0).results as List<Map<String, String>>
            boolean submitted = submittedRelationshipValue(sourceParams, field)
            String submittedId = relationshipSubmittedId(sourceParams, field)
            Map<String, String> selected = submitted
                ? InterlisRelationshipOptions.optionForId(
                    grailsApplication, domainType(), field, submittedId, geometryFields())
                : selectedRelationshipOption(instance, field)
            if (selected != null && options[field].every { Map<String, String> option -> option.id != selected.id }) {
                options[field] = [selected] + options[field]
            }
            values[field] = submitted ? submittedId : selectedRelationshipId(instance, field)
            required[field] = relationshipFieldRequired(field)
        }

        return [
            relationshipFields: fields,
            relationshipOptions: options,
            relationshipValues: values,
            relationshipRequired: required
        ]
    }

    protected boolean submittedRelationshipValue(Map sourceParams, String field) {
        if (sourceParams == null || field == null) {
            return false
        }
        if (sourceParams.containsKey(field + ".id")) {
            return true
        }
        Object nested = sourceParams.get(field)
        return nested instanceof Map && (nested as Map).containsKey("id")
    }

    protected String relationshipSubmittedId(Map sourceParams, String field) {
        if (sourceParams == null || field == null) {
            return null
        }
        Object flattened = sourceParams.get(field + ".id")
        if (flattened != null) {
            return flattened.toString()
        }
        Object nested = sourceParams.get(field)
        if (nested instanceof Map) {
            Object id = (nested as Map).get("id")
            return id?.toString()
        }
        return null
    }

    protected List<String> relationshipFields() {
        return InterlisRelationshipOptions.relationshipFields(grailsApplication, domainType(), geometryFields())
    }

    protected Map<String, Object> relationshipOptionPage(String field, String query, Integer max, Integer offset) {
        if (!InterlisListQuerySupport.whitelistedRelationshipField(uiDescriptor(), field)) {
            return [results: [], pagination: [more: false, total: 0, nextOffset: safeOffset(offset)]]
        }
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

    protected Map<String, Object> geometryModel(T instance, Map sourceParams = params) {
        List<String> fields = geometryFields()
        Map<String, String> values = [:]
        Map<String, String> kinds = [:]
        Map<String, Integer> srids = [:]

        fields.each { String field ->
            Object currentValue = instance?."${field}"
            String submittedWkt = sourceParams?.get(field + "Wkt")?.toString()
            values[field] = submittedWkt != null
                ? submittedWkt
                : (currentValue != null ? currentValue.toText() : "")
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

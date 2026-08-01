package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry
import ch.interlis.generator.grails.runtime.api.security.InterlisAuthorizationPolicy
import ch.interlis.generator.grails.runtime.controller.InterlisControllerContext
import grails.converters.JSON
import grails.validation.ValidationException
import groovy.util.logging.Slf4j
import org.locationtech.jts.geom.Geometry
import org.springframework.dao.DataIntegrityViolationException

import java.beans.Introspector
import java.time.LocalDate
import java.time.temporal.TemporalAccessor

import static org.springframework.http.HttpStatus.*

/**
 * Base class of generated CRUD controllers.
 *
 * <p>Since the controller-flow split this class is a thin delegation layer:
 * the public actions delegate to typed flows ({@link InterlisListControllerFlow},
 * {@link InterlisFormControllerFlow}, {@link InterlisAssociationControllerFlow},
 * {@link InterlisInverseRelationshipControllerFlow},
 * {@link InterlisRelationshipOptionsControllerFlow}). The remaining protected
 * helpers build the GSP view models at the web boundary.</p>
 */
abstract @Slf4j
class InterlisCrudControllerSupport<T> {

    def grailsApplication

    InterlisAuthorizationPolicy authorizationPolicy
    InterlisRuntimeRegistry runtimeRegistry

    private static final InterlisListControllerFlow LIST_FLOW = new InterlisListControllerFlow()
    private static final InterlisFormControllerFlow FORM_FLOW = new InterlisFormControllerFlow()
    private static final InterlisAssociationControllerFlow ASSOCIATION_FLOW =
        new InterlisAssociationControllerFlow()
    private static final InterlisInverseRelationshipControllerFlow INVERSE_FLOW =
        new InterlisInverseRelationshipControllerFlow()
    private static final InterlisRelationshipOptionsControllerFlow OPTIONS_FLOW =
        new InterlisRelationshipOptionsControllerFlow()

    protected abstract Class<T> domainType()

    protected abstract Object crudService()

    protected abstract Object associationQueryService()

    protected abstract Object associationCommandService()

    protected abstract Object inverseRelationshipQueryService()

    protected abstract Object inverseRelationshipCommandService()

    private InterlisControllerContext<T> controllerContext() {
        return new InterlisControllerContext<T>(
            domainType(),
            crudService(),
            associationQueryService(),
            associationCommandService(),
            inverseRelationshipQueryService(),
            inverseRelationshipCommandService(),
            grailsApplication,
            runtimeRegistry,
            authorizationPolicy
        )
    }

    def index(Integer max, Integer offset) {
        return LIST_FLOW.index(this, controllerContext(), max, offset)
    }

    def show(Long id) {
        return LIST_FLOW.show(this, controllerContext(), id)
    }

    def create() {
        return FORM_FLOW.create(this, controllerContext())
    }

    def save() {
        return FORM_FLOW.save(this, controllerContext())
    }

    def edit(Long id) {
        return FORM_FLOW.edit(this, controllerContext(), id)
    }

    def update(Long id) {
        return FORM_FLOW.update(this, controllerContext(), id)
    }

    def delete(Long id) {
        return FORM_FLOW.delete(this, controllerContext(), id)
    }

    def relationshipOptions() {
        return OPTIONS_FLOW.relationshipOptions(this, controllerContext())
    }

    def relationshipCollectionPage(Long id) {
        return INVERSE_FLOW.relationshipCollectionPage(this, controllerContext(), id)
    }

    def relationshipCollectionOptions(Long id) {
        return INVERSE_FLOW.relationshipCollectionOptions(this, controllerContext(), id)
    }

    def relationshipAssign(Long id) {
        return INVERSE_FLOW.relationshipAssign(this, controllerContext(), id)
    }

    def associationPage(Long id) {
        return ASSOCIATION_FLOW.associationPage(this, controllerContext(), id)
    }

    def associationOptions(Long id) {
        return ASSOCIATION_FLOW.associationOptions(this, controllerContext(), id)
    }

    def associationCreate(Long id) {
        return ASSOCIATION_FLOW.associationCreate(this, controllerContext(), id)
    }

    def associationDelete(Long id) {
        return ASSOCIATION_FLOW.associationDelete(this, controllerContext(), id)
    }

    static boolean isDeleteIntegrityConflict(Throwable failure) {
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

    protected Map<String, Object> inverseRelationshipModel(T instance) {
        if (instance == null) {
            return [inverseRelationshipSections: []]
        }
        try {
            return [
                inverseRelationshipSections: inverseRelationshipQueryService().sections(
                    domainType(),
                    instance.id as java.io.Serializable,
                    associationPageSize(),
                    params
                )
            ]
        } catch (IllegalArgumentException e) {
            log.warn(
                "Invalid inverse-relationship configuration for ${domainType().simpleName}: ${e.message}"
            )
            return [
                inverseRelationshipSections: [],
                inverseRelationshipDiagnostic: "Ungültige Beziehungs-Konfiguration: ${e.message}"
            ]
        } catch (Exception e) {
            log.warn(
                "inverseRelationshipModel failed for ${domainType().simpleName}#${instance.id}: ${e.message}",
                e
            )
            return [
                inverseRelationshipSections: [],
                inverseRelationshipDiagnostic: "Beziehungsdaten konnten nicht geladen werden."
            ]
        }
    }

    protected Integer associationPageSize() {
        return 10
    }

    protected void flashNotification(String type,
                                     String text,
                                     String title = null,
                                     Map<String, Object> extras = [:]) {
        ch.interlis.generator.grails.runtime.controller.InterlisControllerResponseSupport
            .flashNotification(this, type, text, title, extras)
    }

    protected void applySecurityHeaders() {
        ch.interlis.generator.grails.runtime.controller.InterlisSecurityHeaderSupport
            .apply(this, response)
    }

    protected Map<String, Object> formModel(T instance, Map sourceParams = params) {
        Map<String, Object> descriptor = uiDescriptor()
        Map<String, Object> model = InterlisFormSupport.formViewModel(descriptor)
        model.putAll(geometryModel(instance, sourceParams))
        model.putAll(relationshipModel(instance, sourceParams))
        model.put("fieldMeta", fieldMeta())
        model.put(
            "workspaceDisplayLabel",
            InterlisWorkspaceSupport.displayLabel(
                grailsApplication,
                instance,
                descriptor?.list?.displayFields instanceof Collection
                    ? descriptor.list.displayFields as Collection<String>
                    : []
            )
        )
        return model
    }

    protected Map<String, Object> formModelWithContext(T instance,
                                                      Map<String, Object> contextState,
                                                      Map sourceParams = params) {
        Map<String, Object> model = formModel(instance, sourceParams)
        model.put("hiddenRelationshipFields", [])
        model.put("fixedRelationshipLabels", [:])
        model.put("associationContextState", null)
        model.put("relationshipContextState", null)
        if (contextState != null && !contextState.isEmpty()) {
            if (contextState.contextKind == "DIRECT_RELATIONSHIP") {
                model.put("hiddenRelationshipFields", [contextState.fixedProperty])
                model.put("fixedRelationshipLabels", [(contextState.fixedProperty): contextState.ownerLabel])
                model.put("relationshipContextState", contextState)
            } else {
                model.put("hiddenRelationshipFields",
                    InterlisAssociationContextSupport.hiddenRelationshipFields(contextState))
                model.put("fixedRelationshipLabels",
                    InterlisAssociationContextSupport.fixedRelationshipLabels(contextState))
                model.put("associationContextState", contextState)
            }
        }
        return model
    }

    protected Map<String, Object> associationContextState(T instance) {
        return associationContextState(instance, false)
    }

    protected Map<String, Object> associationContextState(T instance, boolean edit) {
        try {
            if (edit && hasInverseRelationshipParameters(params)) {
                throw new IllegalArgumentException("Direct relationship context is only valid for create")
            }
            if (!edit && hasInverseRelationshipParameters(params)) {
                return InterlisInverseRelationshipContextSupport.prepareCreateContext(
                    grailsApplication, domainType(), params)
            }
            if (edit && hasAssociationContext(params)) {
                return InterlisAssociationContextSupport.prepareEditContext(
                    grailsApplication, domainType(), instance, params)
            }
            return InterlisAssociationContextSupport.prepareCreateContext(
                grailsApplication, domainType(), params)
        } catch (Exception e) {
            if (hasAnyContext(params)) {
                log.warn("Context rejected for ${domainType().simpleName}: ${e.message}")
                return null
            }
            log.info("Context not applied for ${domainType().simpleName}: ${e.message}")
            return [:]
        }
    }

    protected void applyAssociationContext(T instance, Map<String, Object> state) {
        if (state?.contextKind == "DIRECT_RELATIONSHIP") {
            InterlisInverseRelationshipContextSupport.applyFixedRelationship(instance, state)
        } else {
            InterlisAssociationContextSupport.applyFixedRole(instance, state)
        }
    }

    protected Map<String, Object> contextualRedirectTarget(T instance, Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return null
        }
        return state.contextKind == "DIRECT_RELATIONSHIP"
            ? InterlisInverseRelationshipContextSupport.redirectTarget(grailsApplication, state)
            : InterlisAssociationContextSupport.redirectTarget(state)
    }

    /**
     * A direct inverse create is an action launched from the owner page. It
     * therefore always returns to that owner, including when the generic form
     * submitter happens to be "save and continue". Association forms retain
     * their existing continue behavior.
     */
    protected Map<String, Object> successfulSaveRedirect(T instance,
                                                          Map<String, Object> state,
                                                          String submitMode) {
        if (state?.contextKind == "DIRECT_RELATIONSHIP") {
            return contextualRedirectTarget(instance, state)
        }
        return InterlisFormSupport.saveAndContinue(submitMode)
            ? InterlisFormSupport.continueRedirect(instance, state)
            : contextualRedirectTarget(instance, state)
    }

    protected Map<String, Object> loadContextStateFromParams(T instance = null, boolean edit = false) {
        if (hasInverseRelationshipParameters(params)) {
            if (edit) {
                return null
            }
            try {
                return InterlisInverseRelationshipContextSupport.prepareCreateContext(
                    grailsApplication, domainType(), params)
            } catch (Exception e) {
                log.warn("Failed to load direct relationship context for ${domainType().simpleName}: ${e.message}")
                return null
            }
        }
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
            if (hasAnyContext(params)) {
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

    protected boolean hasInverseRelationshipParameters(Map sourceParams) {
        String relationshipField = sourceParams?.relationshipField?.toString()
        String ownerId = sourceParams?.relationshipOwnerId?.toString()
        return (relationshipField != null && !relationshipField.isBlank())
            || (ownerId != null && !ownerId.isBlank())
    }

    protected boolean hasAnyContext(Map sourceParams) {
        return hasAssociationContext(sourceParams) || hasInverseRelationshipParameters(sourceParams)
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
        return InterlisWorkspaceSupport.renderValue(grailsApplication, value)
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
            label: InterlisRelationshipOptions.optionLabel(grailsApplication, selected)
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

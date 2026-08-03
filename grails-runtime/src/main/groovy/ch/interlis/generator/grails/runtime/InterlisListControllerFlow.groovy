package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.controller.InterlisControllerContext
import ch.interlis.generator.grails.runtime.controller.InterlisControllerResponseSupport
import ch.interlis.generator.grails.runtime.controller.InterlisSecurityHeaderSupport
import ch.interlis.generator.grails.runtime.api.security.DomainOperation
import ch.interlis.generator.grails.runtime.api.security.DomainOperationContext
import groovy.util.logging.Slf4j

/**
 * List flow of the generated CRUD controller: index rendering with the typed
 * list query model.
 */
@Slf4j
final class InterlisListControllerFlow<T> {

    def index(InterlisCrudControllerSupport<T> controller,
              InterlisControllerContext<T> context,
              Integer max,
              Integer offset) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        if (!context.authorizationPolicy.canView(domainOperation(context, DomainOperation.VIEW))) {
            InterlisControllerResponseSupport.respondForbidden(controller,
                "Keine Berechtigung für diese Aktion.")
            return
        }
        Map<String, Object> descriptor = controller.uiDescriptor()
        Map<String, Object> listQuery = InterlisListQuerySupport.parse(controller.params, descriptor)
        if (max != null) {
            listQuery.max = controller.boundedMax(max)
        }
        if (offset != null) {
            listQuery.offset = controller.safeOffset(offset)
        }
        listQuery.params = InterlisListQuerySupport.urlParams(listQuery)
        List<String> columns = controller.tableColumns()
        Map<String, Object> page = InterlisListQuerySupport.page(
            context.crudService, context.domainType, descriptor, listQuery
        )
        List<T> records = page.records as List<T>
        List<Map<String, Object>> filters = controller.filterFields()
        Map<String, Object> filterOptions = controller.relationshipFilterOptions(filters, listQuery)
        listQuery.chips = InterlisListQuerySupport.activeFilterChips(listQuery, filterOptions)
        controller.respond records, model: [
            (controller.modelKey() + "List"): records,
            (controller.modelKey() + "Count"): page.total,
            uiDescriptor: descriptor,
            listQuery: listQuery,
            listQueryWarnings: listQuery.warnings,
            tableColumns: columns,
            tableRows: controller.tableRows(records, columns),
            displayColumn: descriptor.list.displayField,
            typedFilters: filters,
            prominentFilterFields: filters.findAll { controller.listQueryProminent(descriptor, it.name) },
            advancedFilterFields: filters.findAll { !controller.listQueryProminent(descriptor, it.name) },
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
            listUrlParams: listQuery.params,
            runtimeWriteAllowed: controller.runtimeWriteAllowed()
        ]
    }

    def show(InterlisCrudControllerSupport<T> controller,
             InterlisControllerContext<T> context,
             Long id) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        if (!context.authorizationPolicy.canView(domainOperation(context, DomainOperation.VIEW))) {
            InterlisControllerResponseSupport.respondForbidden(controller,
                "Keine Berechtigung für diese Aktion.")
            return
        }
        T instance = context.crudService.get(id) as T
        if (instance == null) {
            InterlisControllerResponseSupport.notFound(
                controller, context.grailsApplication, controller.modelKey())
            return
        }
        Map<String, Object> descriptor = controller.uiDescriptor()
        Map<String, Object> model = [:]
        model.putAll(controller.geometryModel(instance))
        model.putAll(controller.relationshipModel(instance))
        model.putAll(controller.detailModel(instance))
        model.putAll(controller.inverseRelationshipModel(instance))
        model.putAll(controller.associationModel(instance))
        model.putAll(InterlisWorkspaceSupport.showModel(
            context.grailsApplication, context.runtimeRegistry, context.domainType, instance, descriptor))
        model.put("uiDescriptor", descriptor)
        model.put("runtimeWriteAllowed", controller.runtimeWriteAllowed())
        controller.respond instance, model: model
    }

    private static DomainOperationContext domainOperation(InterlisControllerContext<?> context,
                                                          DomainOperation operation) {
        String iliName = null
        try {
            iliName = context.runtimeRegistry?.requireDomain(context.domainType)?.iliName()
        } catch (Exception ignored) {
            // registry not resolvable; the policy still receives the domain class name
        }
        return new DomainOperationContext(operation, context.domainType.name, iliName)
    }
}

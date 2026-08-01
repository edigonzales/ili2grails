package ch.interlis.generator.grails.runtime

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class InterlisInverseRelationshipQueryService {

    static transactional = false

    def grailsApplication
    def grailsLinkGenerator

    List<Map<String, Object>> sections(Class ownerType,
                                        Serializable ownerId,
                                        Integer maxPerSection,
                                        Map sourceParams = [:]) {
        if (ownerType == null || ownerId == null) {
            return []
        }
        Object owner = ownerType.get(ownerId)
        if (owner == null) {
            return []
        }
        int defaultLimit = boundedMax(maxPerSection ?: 10)
        return InterlisInverseRelationshipSupport.descriptors(grailsApplication, ownerType).collect {
            Map<String, Object> descriptor ->
                Class relatedType = InterlisInverseRelationshipSupport.resolveRelatedClass(
                    grailsApplication,
                    descriptor
                )
                Map<String, Object> state = sectionState(
                    descriptor.name?.toString(),
                    sourceParams,
                    relatedType,
                    defaultLimit
                )
                buildSection(owner, descriptor, state)
        }.findAll { it != null } as List<Map<String, Object>>
    }

    Map<String, Object> page(Class ownerType,
                             Serializable ownerId,
                             String relationshipName,
                             Integer max,
                             Integer offset) {
        return page(ownerType, ownerId, relationshipName, null, max, offset, null, null)
    }

    Map<String, Object> page(Class ownerType,
                             Serializable ownerId,
                             String relationshipName,
                             String query,
                             Integer max,
                             Integer offset) {
        return page(ownerType, ownerId, relationshipName, query, max, offset, null, null)
    }

    Map<String, Object> page(Class ownerType,
                             Serializable ownerId,
                             String relationshipName,
                             String query,
                             Integer max,
                             Integer offset,
                             String sort,
                             String order) {
        Map<String, Object> descriptor = InterlisInverseRelationshipSupport.requireDescriptor(
            grailsApplication,
            ownerType,
            relationshipName
        )
        if (descriptor.visible != true) {
            throw new InterlisInverseRelationshipSupport.InverseRelationshipNotFoundException(
                "Inverse relationship '${relationshipName}' is disabled"
            )
        }
        Class relatedType = InterlisInverseRelationshipSupport.resolveRelatedClass(
            grailsApplication,
            descriptor
        )
        Map<String, Object> targetDescriptor = descriptorFor(relatedType)
        String safeSort = safeSort(targetDescriptor, sort)
        String safeOrder = safeOrder(order)
        int pageMax = boundedMax(max ?: 10)
        int pageOffset = safeOffset(offset ?: 0)
        Map<String, Object> page = relationshipPage(
            ownerId,
            descriptor,
            query?.trim(),
            pageMax,
            pageOffset,
            safeSort,
            safeOrder
        )
        page.pagination = InterlisListQuerySupport.paginationModel(
            [q: query?.trim(), max: pageMax, offset: pageOffset, sort: safeSort, order: safeOrder],
            page.total as Number
        )
        return page
    }

    Map<String, Object> optionPage(Class ownerType,
                                   Serializable ownerId,
                                   String relationshipName,
                                   String query,
                                   Integer max,
                                   Integer offset) {
        Map<String, Object> descriptor = InterlisInverseRelationshipSupport.requireDescriptor(
            grailsApplication,
            ownerType,
            relationshipName
        )
        if (descriptor.visible != true || descriptor.writable != true) {
            return [results: [], pagination: [more: false, total: 0, nextOffset: safeOffset(offset ?: 0)]]
        }
        Class relatedType = InterlisInverseRelationshipSupport.resolveRelatedClass(
            grailsApplication,
            descriptor
        )
        return InterlisRelationshipOptions.optionPageForInverseRelationship(
            grailsApplication,
            relatedType,
            descriptor.relatedProperty?.toString(),
            ownerId,
            query,
            boundedMax(max ?: 25),
            safeOffset(offset ?: 0)
        )
    }

    private Map<String, Object> buildSection(Object owner,
                                             Map<String, Object> descriptor,
                                             Map<String, Object> state) {
        Class relatedType = InterlisInverseRelationshipSupport.resolveRelatedClass(
            grailsApplication,
            descriptor
        )
        Map<String, Object> page = relationshipPage(
            owner.id as Serializable,
            descriptor,
            state.q?.toString(),
            state.max as int,
            state.offset as int,
            state.sort?.toString(),
            state.order?.toString()
        )
        String relatedController = InterlisInverseRelationshipSupport.controllerForClass(relatedType)
        String relatedProperty = descriptor.relatedProperty?.toString()
        String relatedDomainLabel = descriptorFor(relatedType)?.label?.toString()
            ?: relatedType?.simpleName
        Map<String, Object> pagination = InterlisListQuerySupport.scopedPaginationModel(
            "inverse." + descriptor.name,
            state,
            page.total as Number,
            state.scopedParams as Map<String, Object>
        )
        return [
            ownerId          : owner.id?.toString(),
            name             : descriptor.name,
            label            : descriptor.label,
            relatedLabel     : descriptor.relatedLabel,
            relatedDomainLabel: relatedDomainLabel,
            relatedController: relatedController,
            relatedProperty  : relatedProperty,
            writable         : descriptor.writable == true,
            mandatory        : descriptor.mandatory == true,
            domId            : domId(descriptor.name?.toString()),
            total            : page.total,
            max              : page.max,
            offset           : page.offset,
            more             : page.more,
            query            : state.q,
            sort             : state.sort,
            order            : state.order,
            columns          : page.columns,
            displayColumn    : page.displayColumn,
            sortParams       : sortParams(state, "inverse." + descriptor.name, page.columns),
            rows             : page.rows,
            pagination       : pagination,
            queryFormParams  : queryFormParams(state, "inverse." + descriptor.name),
            contextualCreate: contextualCreate(owner, descriptor, relatedController, relatedProperty)
        ]
    }

    private Map<String, Object> relationshipPage(Serializable ownerId,
                                                 Map<String, Object> descriptor,
                                                 String query,
                                                 int max,
                                                 int offset,
                                                 String sort,
                                                 String order) {
        Class relatedType = InterlisInverseRelationshipSupport.resolveRelatedClass(
            grailsApplication,
            descriptor
        )
        String relatedProperty = descriptor.relatedProperty?.toString()
        if (relatedType == null || relatedProperty == null || relatedProperty.isBlank()) {
            return [
                total: 0,
                rows: [],
                columns: [],
                displayColumn: null,
                max: max,
                offset: offset,
                more: false
            ]
        }
        Map<String, Object> relatedDescriptor = descriptorFor(relatedType)
        List<String> searchColumns = query
            ? InterlisRelationshipOptions.inverseRelationshipSearchFields(grailsApplication, relatedType)
            : []
        String safeSortField = safeSort(relatedDescriptor, sort)
        String safeOrderValue = safeOrder(order)
        def results = relatedType.createCriteria().list(
            max: max,
            offset: offset,
            sort: safeSortField,
            order: safeOrderValue
        ) {
            and {
                eq(relatedProperty + ".id", ownerId)
                if (query) {
                    if (searchColumns.isEmpty()) {
                        eq("id", null)
                    } else {
                        or {
                            searchColumns.each { String column ->
                                ilike(column, "%" + query + "%")
                            }
                        }
                    }
                }
            }
        }
        Number total = relatedType.createCriteria().get {
            and {
                eq(relatedProperty + ".id", ownerId)
                if (query) {
                    if (searchColumns.isEmpty()) {
                        eq("id", null)
                    } else {
                        or {
                            searchColumns.each { String column ->
                                ilike(column, "%" + query + "%")
                            }
                        }
                    }
                }
            }
            projections {
                count("id")
            }
        } as Number ?: 0

        String controller = InterlisInverseRelationshipSupport.controllerForClass(relatedType)
        Map<String, Object> columnModel = columnModel(relatedDescriptor)
        List<Map<String, Object>> rows = (results as List<Object>).collect { Object related ->
            tableRow(related, controller, columnModel.columns as List<Map<String, Object>>,
                columnModel.displayColumn?.toString())
        }
        return [
            total        : total.longValue(),
            rows         : rows,
            columns      : columnModel.columns,
            displayColumn: columnModel.displayColumn,
            max          : max,
            offset       : offset,
            more         : offset + rows.size() < total.longValue()
        ]
    }

    private Map<String, Object> sectionState(String name,
                                             Map sourceParams,
                                             Class relatedType,
                                             int defaultLimit) {
        String prefix = "inverse." + name
        Map<String, Object> targetDescriptor = descriptorFor(relatedType)
        String rawMax = sourceParams?.get(prefix + ".max")?.toString()
        String rawOffset = sourceParams?.get(prefix + ".offset")?.toString()
        int max = boundedMax(parseInteger(rawMax, defaultLimit))
        int offset = safeOffset(parseInteger(rawOffset, 0))
        String query = sourceParams?.get(prefix + ".q")?.toString()?.trim()
        String sort = safeSort(targetDescriptor, sourceParams?.get(prefix + ".sort")?.toString())
        String order = safeOrder(sourceParams?.get(prefix + ".order")?.toString())
        Map<String, Object> scopedParams = (sourceParams ?: [:]).findAll { Object key, Object value ->
            key?.toString()?.startsWith("inverse.") && value != null
        }.collectEntries { Object key, Object value ->
            [(key.toString()): value]
        } as Map<String, Object>
        return [q: query, max: max, offset: offset, sort: sort, order: order, scopedParams: scopedParams]
    }

    private Map<String, Object> queryFormParams(Map<String, Object> state, String prefix) {
        return InterlisListQuerySupport.scopedUrlParams(
            prefix, state, [q: null, offset: 0], state.scopedParams as Map<String, Object>
        )
    }

    private Map<String, Map<String, Object>> sortParams(Map<String, Object> state,
                                                         String prefix,
                                                         List<Map<String, Object>> columns) {
        String nextOrder = state.order?.toString() == "desc" ? "asc" : "desc"
        return (columns ?: []).collectEntries { Map<String, Object> column ->
            String key = column.key?.toString()
            [(key): InterlisListQuerySupport.scopedUrlParams(
                prefix,
                state,
                [sort: key, order: nextOrder, offset: 0],
                state.scopedParams as Map<String, Object>
            )]
        }
    }

    private Map<String, Object> contextualCreate(Object owner,
                                                  Map<String, Object> descriptor,
                                                  String controller,
                                                  String relatedProperty) {
        if (owner == null || descriptor?.writable != true
            || controller == null || controller.isBlank()
            || relatedProperty == null || relatedProperty.isBlank()) {
            return null
        }
        return [
            controller: controller,
            action    : "create",
            params    : [
                relationshipField   : relatedProperty,
                relationshipOwnerId : owner.id?.toString()
            ]
        ]
    }

    private Map<String, Object> columnModel(Map<String, Object> descriptor) {
        Map<String, Object> list = descriptor?.list instanceof Map ? descriptor.list as Map<String, Object> : [:]
        List<String> configured = list.columns instanceof Collection
            ? list.columns.collect { it.toString() }.findAll { !it.isBlank() }.unique()
            : []
        if (configured.isEmpty()) {
            configured = [(list.displayField ?: "id").toString()]
        }
        Map<String, Object> fieldMeta = descriptor?.fieldMeta instanceof Map
            ? descriptor.fieldMeta as Map<String, Object>
            : [:]
        Set<String> sortable = list.sortableColumns instanceof Collection
            ? list.sortableColumns.collect { it.toString() } as Set<String>
            : ["id"] as Set<String>
        List<Map<String, Object>> columns = configured.collect { String field ->
            Map<String, Object> meta = fieldMeta[field] instanceof Map ? fieldMeta[field] as Map<String, Object> : [:]
            [
                key     : field,
                label   : meta.label?.toString() ?: humanize(field),
                sortable: sortable.contains(field)
            ]
        }
        String displayColumn = list.displayField?.toString()
        if (displayColumn == null || !configured.contains(displayColumn)) {
            displayColumn = (list.displayFields instanceof Collection
                ? list.displayFields.collect { it.toString() }.find { configured.contains(it) }
                : null) ?: configured.first()
        }
        return [columns: columns, displayColumn: displayColumn]
    }

    private Map<String, Object> tableRow(Object related,
                                         String controller,
                                         List<Map<String, Object>> columns,
                                         String displayColumn) {
        String id = readProperty(related, "id")?.toString()
        Map<String, Object> values = [:]
        Map<String, Object> links = [:]
        columns.each { Map<String, Object> column ->
            String key = column.key?.toString()
            Object value = readProperty(related, key)
            values[key] = InterlisWorkspaceSupport.renderValue(grailsApplication, value)
            if (key == displayColumn && controller != null && id != null) {
                links[key] = [controller: controller, action: "show", id: id]
            }
        }
        return [
            id        : id,
            label     : InterlisRelationshipOptions.optionLabel(grailsApplication, related),
            controller: controller,
            url       : relatedUrl(controller, related.id),
            values    : values,
            links     : links
        ]
    }

    private Map<String, Object> descriptorFor(Class domainType) {
        if (domainType == null) {
            return [:]
        }
        try {
            return InterlisUiDescriptorSupport.descriptor(grailsApplication, domainType)
        } catch (Exception ignored) {
            return [:]
        }
    }

    private String safeSort(Map<String, Object> descriptor, String requested) {
        List<String> sortable = descriptor?.list?.sortableColumns instanceof Collection
            ? descriptor.list.sortableColumns.collect { it.toString() }
            : ["id"]
        return requested != null && sortable.contains(requested) ? requested : "id"
    }

    private String safeOrder(String requested) {
        return requested?.toLowerCase(Locale.ROOT) == "desc" ? "desc" : "asc"
    }

    private Integer parseInteger(String raw, Integer fallback) {
        try {
            return raw == null || raw.isBlank() ? fallback : Integer.valueOf(raw)
        } catch (NumberFormatException ignored) {
            return fallback
        }
    }

    private int boundedMax(Integer max) {
        return Math.max(1, Math.min(max ?: 10, 100))
    }

    private int safeOffset(Integer offset) {
        return Math.max(offset ?: 0, 0)
    }

    private String domId(String value) {
        return "inverse-" + (value ?: "relationship").replaceAll("[^A-Za-z0-9_-]", "-")
    }

    private Object readProperty(Object value, String propertyName) {
        if (value == null || propertyName == null) {
            return null
        }
        try {
            return value."${propertyName}"
        } catch (Exception ignored) {
            return null
        }
    }

    private String humanize(String value) {
        return value.replaceAll("([a-z])([A-Z])", '$1 $2')
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .findAll { !it.isBlank() }
            .collect { String part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1) }
            .join(' ')
    }

    private String relatedUrl(String controller, Object id) {
        if (controller == null || id == null || grailsLinkGenerator == null) {
            return null
        }
        try {
            return grailsLinkGenerator.link(
                controller: controller,
                action: "show",
                id: id,
                absolute: false
            )
        } catch (IllegalStateException missingServerUrl) {
            return "/${controller}/show/${URLEncoder.encode(id.toString(), StandardCharsets.UTF_8)}"
        }
    }
}

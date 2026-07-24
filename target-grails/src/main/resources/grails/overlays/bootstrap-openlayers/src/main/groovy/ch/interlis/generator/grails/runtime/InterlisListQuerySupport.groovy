package ch.interlis.generator.grails.runtime

import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.Temporal

/**
 * Parses and executes the server-side list query contract. All Criteria paths
 * in this class originate in the UI descriptor. Request keys are used only to
 * select a descriptor entry and never become Criteria property names.
 */
final class InterlisListQuerySupport {

    static final int DEFAULT_MAX = 25
    static final int MAX_MAX = 100
    private static final int MAX_VISIBLE_PAGE_NUMBERS = 5
    private static final int LEFT_JOIN = 4

    private InterlisListQuerySupport() {
    }

    static Map<String, Object> parse(def rawParameters, Map<String, Object> descriptor) {
        Map<String, Object> source = rawParameters instanceof Map
            ? rawParameters as Map<String, Object>
            : [:]
        Map<String, Object> list = descriptor?.list instanceof Map ? descriptor.list : [:]
        Map<String, Map<String, Object>> definitions = list.filters instanceof Map
            ? list.filters as Map<String, Map<String, Object>>
            : [:]
        List<String> warnings = []

        String query = normalized(source.q)
        Integer max = boundedInteger(source.max, DEFAULT_MAX, 1, MAX_MAX, warnings, "max")
        Integer offset = boundedInteger(source.offset, 0, 0, Integer.MAX_VALUE, warnings, "offset")

        List<String> sortable = (list.sortableColumns instanceof Collection
            ? list.sortableColumns as Collection
            : ["id"]).collect { it.toString() }
        if (!sortable.contains("id")) {
            sortable = ["id"] + sortable
        }
        String requestedSort = normalized(source.sort)
        String sort = requestedSort && sortable.contains(requestedSort) ? requestedSort : "id"
        if (requestedSort != null && sort != requestedSort) {
            warnings << "Ungültige Sortierung '${requestedSort}'; es wird nach id sortiert."
        }
        String order = normalized(source.order)?.toLowerCase(Locale.ROOT)
        if (order != "desc") {
            if (order != null && order != "asc") {
                warnings << "Ungültige Sortierreihenfolge; es wird aufsteigend sortiert."
            }
            order = "asc"
        }

        Map<String, Object> rawFilters = collectFilterParameters(source, warnings)
        Map<String, Map<String, Object>> filters = [:]
        Map<String, Object> activeFilters = [:]
        Map<String, Map<String, Object>> filterValues = [:]
        rawFilters.keySet().findAll { !definitions.containsKey(it.toString()) }.each { String field ->
            warnings << "Unbekannter Filter '${field}'; der Filter wurde verworfen."
        }
        definitions.keySet().each { String field ->
            Map<String, Object> definition = definitions[field]
            Object raw = rawFilters[field]
            Map<String, Object> parts = raw instanceof Map
                ? raw as Map<String, Object>
                : [value: raw]
            Map<String, Object> parsed = parseFilter(field, definition, parts, warnings)
            if (parsed == null) {
                return
            }
            filters[field] = parsed
            filterValues[field] = [
                value: parsed.raw,
                min  : parsed.minRaw,
                max  : parsed.maxRaw,
                from : parsed.fromRaw,
                to   : parsed.toRaw
            ].findAll { String key, Object value -> value != null && value.toString() != "" }
            activeFilters[field] = filterValues[field].size() == 1 && filterValues[field].containsKey("value")
                ? filterValues[field].value
                : filterValues[field]
        }

        Map<String, Object> result = [
            q            : query,
            filters      : filters,
            activeFilters: activeFilters,
            filterValues : filterValues,
            warnings     : warnings.unique(),
            max          : max,
            offset       : offset,
            sort         : sort,
            order        : order
        ]
        result.params = urlParams(result)
        result.chips = activeFilterChips(result)
        return result
    }

    static Object coerceFilterValue(Object raw, Map<String, Object> definition) {
        String value = normalized(raw)
        if (value == null || definition == null) {
            return null
        }
        switch (definition.type?.toString()) {
            case "text":
                return value
            case "enum":
                List<Map<String, Object>> options = definition.options instanceof Collection
                    ? definition.options as List<Map<String, Object>>
                    : []
                if (!options.any { it.value?.toString() == value }) {
                    return null
                }
                Class enumType = definition.propertyType instanceof Class ? definition.propertyType as Class : null
                return enumType?.isEnum() ? Enum.valueOf(enumType, value) : value
            case "boolean":
                return value == "true" ? Boolean.TRUE : (value == "false" ? Boolean.FALSE : null)
            case "relationship":
                return value ==~ /[0-9]+/ ? Long.valueOf(value) : null
            case "number":
                return numberValue(value, definition)
            case "date":
                return dateValue(value, definition)
            default:
                return null
        }
    }

    /**
     * Executes the already parsed query. The fast path deliberately uses the
     * service API; the Criteria path contains only descriptor-derived names.
     */
    static Map<String, Object> page(def crudService,
                                    Class domainType,
                                    Map<String, Object> descriptor,
                                    Map<String, Object> query) {
        Map<String, Map<String, Object>> definitions = descriptor?.list?.filters instanceof Map
            ? descriptor.list.filters as Map<String, Map<String, Object>>
            : [:]
        Map<String, Object> filters = query?.filters instanceof Map ? query.filters : [:]
        String search = normalized(query?.q)
        List<Map<String, Object>> searchDefinitions = descriptor?.list?.searchDefinitions instanceof Collection
            ? descriptor.list.searchDefinitions as List<Map<String, Object>>
            : []
        Map<String, Object> pagination = [
            max   : query.max as Integer,
            offset: query.offset as Integer,
            sort  : query.sort?.toString() ?: "id",
            order : query.order?.toString() == "desc" ? "desc" : "asc"
        ]

        if (search == null && filters.isEmpty()) {
            List records = crudService.list(pagination) as List
            return [records: records, total: crudService.count(), domainHasRecords: crudService.count() > 0]
        }
        if (search != null && searchDefinitions.isEmpty() && filters.isEmpty()) {
            return [records: [], total: 0, domainHasRecords: crudService.count() > 0]
        }

        def results = domainType.createCriteria().list(
            max: pagination.max,
            offset: pagination.offset,
            sort: pagination.sort,
            order: pagination.order
        ) {
            if (search != null && !searchDefinitions.isEmpty()) {
                searchDefinitions.findAll { it.relationship }.collect { it.alias }.unique().each { String alias ->
                    Map<String, Object> definition = searchDefinitions.find { it.alias == alias }
                    createAlias(definition.relationshipField as String, alias, LEFT_JOIN)
                }
                String pattern = "%" + search + "%"
                or {
                    searchDefinitions.each { Map<String, Object> definition ->
                        ilike(definition.criteriaPath as String, pattern)
                    }
                }
            }
            filters.each { String requestedField, Map<String, Object> parsed ->
                Map<String, Object> definition = definitions[requestedField]
                if (definition == null) {
                    return
                }
                String field = definition.name.toString()
                String type = definition.type?.toString()
                if (type == "relationship") {
                    eq(field + ".id", parsed.value)
                    return
                }
                if (parsed.value != null) {
                    if (type == "text") {
                        ilike(field, "%" + parsed.value.toString() + "%")
                    } else {
                        eq(field, parsed.value)
                    }
                }
                if (parsed.min != null) {
                    ge(field, parsed.min)
                }
                if (parsed.max != null) {
                    le(field, parsed.max)
                }
                if (parsed.from != null) {
                    ge(field, parsed.from)
                }
                if (parsed.to != null) {
                    le(field, parsed.to)
                }
            }
        }
        Number total = totalCount(results)
        return [
            records       : results as List,
            total         : total,
            domainHasRecords: total > 0 || crudService.count() > 0
        ]
    }

    static Map<String, Object> urlParams(Map<String, Object> query, Map<String, Object> overrides = [:]) {
        Map<String, Object> result = new LinkedHashMap<>()
        if (normalized(query?.q) != null) {
            result.q = query.q
        }
        Map<String, Map<String, Object>> filters = query?.filterValues instanceof Map
            ? query.filterValues as Map<String, Map<String, Object>>
            : [:]
        filters.each { String field, Map<String, Object> values ->
            if (values.value != null) result["filter." + field] = values.value
            if (values.min != null) result["filter." + field + ".min"] = values.min
            if (values.max != null) result["filter." + field + ".max"] = values.max
            if (values.from != null) result["filter." + field + ".from"] = values.from
            if (values.to != null) result["filter." + field + ".to"] = values.to
        }
        result.sort = query?.sort ?: "id"
        result.order = query?.order ?: "asc"
        result.max = query?.max ?: DEFAULT_MAX
        result.offset = query?.offset ?: 0
        (overrides ?: [:]).each { String key, Object value ->
            if (value == null || value.toString() == "") {
                result.remove(key)
            } else {
                result[key] = value
            }
        }
        return result
    }

    static Map<String, Object> removeFilterParams(Map<String, Object> query, String field) {
        Map<String, Object> params = urlParams(query)
        ["filter." + field, "filter." + field + ".min", "filter." + field + ".max",
         "filter." + field + ".from", "filter." + field + ".to"].each { params.remove(it) }
        params.offset = 0
        return params
    }

    static Map<String, Object> sortParams(Map<String, Object> query, String field) {
        String current = query?.sort?.toString()
        String nextOrder = current == field && query?.order?.toString() == "asc" ? "desc" : "asc"
        return urlParams(query, [sort: field, order: nextOrder, offset: 0])
    }

    static Map<String, Object> paginationModel(Map<String, Object> query, Number total) {
        int max = (query?.max ?: DEFAULT_MAX) as int
        int offset = (query?.offset ?: 0) as int
        int count = (total ?: 0) as int
        int lastOffset = count > 0 ? ((count - 1) / max as int) * max : 0
        int currentPage = (offset / max as int) + 1
        int lastPage = count > 0 ? ((count - 1) / max as int) + 1 : 1
        int resultStart = count > 0 ? Math.min(offset + 1, count) : 0
        int resultEnd = count > 0 ? Math.min(offset + max, count) : 0
        List<Map<String, Object>> pages = paginationPages(query, currentPage, lastPage, max)
        return [
            total: count,
            max: max,
            offset: offset,
            currentPage: currentPage,
            lastPage: lastPage,
            showResultRange: count > max,
            resultStart: resultStart,
            resultEnd: resultEnd,
            pageSizeParams: urlParams(query, [max: null, offset: 0]),
            hasPrevious: offset > 0,
            hasNext: offset < lastOffset,
            previousParams: urlParams(query, [offset: Math.max(0, offset - max)]),
            nextParams: urlParams(query, [offset: Math.min(lastOffset, offset + max)]),
            pages: pages
        ]
    }

    private static List<Map<String, Object>> paginationPages(Map<String, Object> query,
                                                              int currentPage,
                                                              int lastPage,
                                                              int max) {
        if (lastPage <= MAX_VISIBLE_PAGE_NUMBERS) {
            return (1..lastPage).collect { int pageNumber -> pageItem(query, pageNumber, currentPage, max) }
        }

        List<Integer> pageNumbers
        if (currentPage <= 3) {
            pageNumbers = [1, 2, 3, 4, lastPage]
        } else if (currentPage >= lastPage - 2) {
            pageNumbers = [1, lastPage - 3, lastPage - 2, lastPage - 1, lastPage]
        } else {
            pageNumbers = [1, currentPage - 1, currentPage, currentPage + 1, lastPage]
        }

        List<Map<String, Object>> pages = []
        Integer previousPage = null
        pageNumbers.unique().sort().each { int pageNumber ->
            if (previousPage != null && pageNumber - previousPage > 1) {
                pages << [ellipsis: true]
            }
            pages << pageItem(query, pageNumber, currentPage, max)
            previousPage = pageNumber
        }
        return pages
    }

    private static Map<String, Object> pageItem(Map<String, Object> query,
                                                 int pageNumber,
                                                 int currentPage,
                                                 int max) {
        int pageOffset = (pageNumber - 1) * max
        [ellipsis: false,
         number: pageNumber,
         current: pageNumber == currentPage,
         params: urlParams(query, [offset: pageOffset])]
    }

    static List<Map<String, Object>> activeFilterChips(Map<String, Object> query,
                                                       Map<String, Object> filterOptions = [:]) {
        List<Map<String, Object>> chips = []
        (query?.filters ?: [:]).each { String field, Map<String, Object> parsed ->
            Map<String, Object> definition = parsed.definition instanceof Map ? parsed.definition : [:]
            List<String> values = []
            String displayValue = parsed.raw?.toString()
            if (definition.type?.toString() == "relationship" && displayValue != null) {
                Map<String, Object> optionPage = filterOptions?.get(field) instanceof Map
                    ? filterOptions[field] as Map<String, Object>
                    : [:]
                List<Map<String, Object>> options = optionPage.results instanceof Collection
                    ? optionPage.results as List<Map<String, Object>>
                    : []
                Map<String, Object> selectedOption = options.find {
                    it.id?.toString() == displayValue
                }
                String optionLabel = selectedOption?.label?.toString()
                if (optionLabel?.trim()) {
                    displayValue = optionLabel
                }
            }
            if (displayValue != null) values << displayValue
            if (parsed.minRaw != null) values << "ab " + parsed.minRaw
            if (parsed.maxRaw != null) values << "bis " + parsed.maxRaw
            if (parsed.fromRaw != null) values << "ab " + parsed.fromRaw
            if (parsed.toRaw != null) values << "bis " + parsed.toRaw
            chips << [
                field     : field,
                label     : definition.label ?: field,
                value     : values.join(" "),
                removeParams: removeFilterParams(query, field)
            ]
        }
        return chips
    }

    static boolean whitelistedRelationshipField(Map<String, Object> descriptor, String field) {
        Map<String, Object> definition = descriptor?.list?.filters instanceof Map
            ? descriptor.list.filters[field] as Map<String, Object>
            : null
        return definition?.type?.toString() == "relationship"
    }

    static Map<String, Object> relationshipOptions(def grailsApplication,
                                                    Class domainType,
                                                    Map<String, Object> definition,
                                                    String selectedId,
                                                    Integer max = DEFAULT_MAX) {
        String field = definition?.name?.toString()
        Map<String, Object> page = InterlisRelationshipOptions.optionPage(
            grailsApplication, domainType, field, null, Math.min(max ?: DEFAULT_MAX, MAX_MAX), 0, []
        )
        List<Map<String, String>> results = (page.results ?: []) as List<Map<String, String>>
        if (selectedId != null && results.every { it.id != selectedId }) {
            Class targetType = definition.targetType as Class
            Object selected = targetType?.get(selectedId as Long)
            if (selected != null) {
                results = [[id: selected.id.toString(), label: InterlisRelationshipOptions.optionLabel(grailsApplication, selected)]] + results
            }
        }
        return [results: results, pagination: page.pagination]
    }

    private static Map<String, Object> parseFilter(String field,
                                                   Map<String, Object> definition,
                                                   Map<String, Object> parts,
                                                   List<String> warnings) {
        if (definition == null) {
            return null
        }
        String type = definition.type?.toString()
        Object raw = parts.value
        String rawValue = normalized(raw)
        Map<String, Object> parsed = [field: field, definition: definition, type: type, raw: rawValue]
        if (type in ["number", "date"]) {
            if (parts.keySet().findAll { it !in ["value", "min", "max", "from", "to"] }) {
                warnings << "Unbekannter Bereichsparameter für Filter '${field}'."
            }
            if (rawValue != null) {
                parsed.value = coerceFilterValue(rawValue, definition)
                if (parsed.value == null) {
                    warnings << "Ungültiger Wert für Filter '${field}'; der Filter wurde verworfen."
                    return null
                }
            }
            String lowerKey = type == "number" ? "min" : "from"
            String upperKey = type == "number" ? "max" : "to"
            String lowerRaw = normalized(parts[lowerKey])
            String upperRaw = normalized(parts[upperKey])
            if (rawValue == null && lowerRaw == null && upperRaw == null) {
                return null
            }
            if (lowerRaw != null) parsed[lowerKey] = coerceFilterValue(lowerRaw, definition)
            if (upperRaw != null) parsed[upperKey] = coerceFilterValue(upperRaw, definition)
            if (lowerRaw != null && parsed[lowerKey] == null || upperRaw != null && parsed[upperKey] == null) {
                warnings << "Ungültige Bereichsgrenze für Filter '${field}'; der Filter wurde verworfen."
                return null
            }
            if (parsed[lowerKey] != null && parsed[upperKey] != null && parsed[lowerKey] > parsed[upperKey]) {
                warnings << "Ungültiger Bereich für Filter '${field}'; die Untergrenze ist grösser als die Obergrenze."
                return null
            }
            parsed.minRaw = type == "number" ? lowerRaw : null
            parsed.maxRaw = type == "number" ? upperRaw : null
            parsed.fromRaw = type == "date" ? lowerRaw : null
            parsed.toRaw = type == "date" ? upperRaw : null
            return parsed.findAll { String key, Object value -> value != null }
        }
        if (parts.keySet().findAll { it != "value" }) {
            warnings << "Unbekannter Filterparameter für '${field}'; der Filter wurde verworfen."
            return null
        }
        if (rawValue == null) {
            return null
        }
        Object value = coerceFilterValue(rawValue, definition)
        if (value == null) {
            warnings << "Ungültiger Wert für Filter '${field}'; der Filter wurde verworfen."
            return null
        }
        parsed.value = value
        return parsed
    }

    private static Map<String, Object> collectFilterParameters(Map<String, Object> source, List<String> warnings) {
        Map<String, Object> result = [:]
        if (source.filter instanceof Map) {
            (source.filter as Map).each { Object key, Object value ->
                addFilterParameter(result, key.toString(), value)
            }
        }
        source.each { Object key, Object value ->
            String name = key?.toString()
            if (name?.startsWith("filter.")) {
                addFilterParameter(result, name.substring("filter.".length()), value, warnings, name)
            }
        }
        return result
    }

    private static void addFilterParameter(Map<String, Object> result,
                                           String name,
                                           Object value,
                                           List<String> warnings = null,
                                           String originalName = null) {
        List<String> parts = name.split('\\.') as List<String>
        if (parts.size() < 1 || parts.size() > 2 || parts.any { it == null || it.isBlank() }) {
            if (warnings != null) {
                warnings << "Unbekannter Filterparameter '${originalName ?: name}'."
            }
            return
        }
        String field = parts[0]
        if (parts.size() == 1) {
            result[field] = value
            return
        }
        Map<String, Object> values = result[field] instanceof Map
            ? result[field] as Map<String, Object>
            : [:]
        values[parts[1]] = value
        result[field] = values
    }

    private static String normalized(Object value) {
        if (value instanceof Collection) {
            value = value ? value.first() : null
        }
        String result = value?.toString()?.trim()
        return result ?: null
    }

    private static Integer boundedInteger(Object raw,
                                          Integer fallback,
                                          Integer minimum,
                                          Integer maximum,
                                          List<String> warnings,
                                          String parameter) {
        String value = normalized(raw)
        if (value == null) return fallback
        try {
            Integer parsed = Integer.valueOf(value)
            if (parsed < minimum || parsed > maximum) {
                warnings << "Ungültiger Wert für ${parameter}; der Wert wurde begrenzt."
            }
            return Math.min(Math.max(parsed, minimum), maximum)
        } catch (NumberFormatException ignored) {
            warnings << "Ungültiger Wert für ${parameter}; der Standardwert wird verwendet."
            return fallback
        }
    }

    private static Object numberValue(String raw, Map<String, Object> definition) {
        try {
            BigDecimal decimal = new BigDecimal(raw)
            Class type = definition.propertyType instanceof Class ? definition.propertyType as Class : null
            if (type == BigInteger) return decimal.toBigIntegerExact()
            if (type == Byte || type == Byte.TYPE) return decimal.byteValueExact()
            if (type == Short || type == Short.TYPE) return decimal.shortValueExact()
            if (type == Integer || type == Integer.TYPE) return decimal.intValueExact()
            if (type == Long || type == Long.TYPE) return decimal.longValueExact()
            if (type == Float || type == Float.TYPE) return decimal.floatValue()
            if (type == Double || type == Double.TYPE) return decimal.doubleValue()
            return decimal
        } catch (Exception ignored) {
            return null
        }
    }

    private static Object dateValue(String raw, Map<String, Object> definition) {
        try {
            LocalDate date = LocalDate.parse(raw)
            Class type = definition.propertyType instanceof Class ? definition.propertyType as Class : null
            if (type == LocalDateTime) return date.atStartOfDay()
            if (type == LocalDate) return date
            if (type == java.sql.Date || type == java.util.Date) return java.sql.Date.valueOf(date)
            if (type == LocalTime) return date.atStartOfDay().toLocalTime()
            return date
        } catch (Exception ignored) {
            return null
        }
    }

    private static Number totalCount(def results) {
        try {
            return results.totalCount ?: results.size()
        } catch (Exception ignored) {
            return results.size()
        }
    }
}

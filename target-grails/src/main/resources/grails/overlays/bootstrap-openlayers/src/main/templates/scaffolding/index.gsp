<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <g:set var="entityName" value="\${message(code: '${propertyName}.label', default: '${className}')}" />
    <g:set var="rows" value="\${${propertyName}List ?: []}" />
    <title>\${entityName}</title>
</head>
<body>
<div id="content" role="main" class="ili-page ili-page-list">
    <g:render template="list-header" model="\${[
        entityName: entityName
    ]}"/>

    <g:render template="list-filters" model="\${[
        entityName: entityName,
        labelPrefix: '${propertyName}',
        typedFilters: typedFilters,
        prominentFilterFields: prominentFilterFields,
        advancedFilterFields: advancedFilterFields,
        filterOptions: filterOptions,
        listQuery: listQuery,
        activeFilters: activeFilters,
        activeFilterChips: activeFilterChips,
        listQueryWarnings: listQueryWarnings
    ]}"/>

    <g:if test="\${rows.isEmpty()}">
        <p class="ili-list-result-summary" data-list-result-summary>
            <g:message code="ili2grails.pagination.resultCount"
                       args="\${[pagination?.total ?: 0]}"
                       default="{0} Treffer"/>
        </p>
        <g:render template="list-empty" model="\${[
            entityName: entityName,
            domainHasRecords: domainHasRecords,
            hasActiveListQuery: hasActiveListQuery,
            listQuery: listQuery
        ]}"/>
    </g:if>
    <g:else>
        <g:render template="list-table" model="\${[
            rows: rows,
            tableColumns: tableColumns,
            tableRows: tableRows,
            displayColumn: displayColumn,
            sortUrls: sortUrls,
            listQuery: listQuery,
            entityName: entityName,
            controllerName: controllerName
        ]}"/>
        <g:render template="list-pagination" model="\${[
            pagination: pagination,
            entityName: entityName
        ]}"/>
    </g:else>
</div>
</body>
</html>

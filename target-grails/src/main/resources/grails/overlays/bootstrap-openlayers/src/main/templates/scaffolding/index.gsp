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

    <g:if test="\${flash.message}">
        <div class="alert alert-info" role="status">\${flash.message}</div>
    </g:if>
    <g:if test="\${listQueryWarnings}">
        <div class="alert alert-warning" role="alert" data-list-query-warning>
            <h2 class="h6 mb-2"><g:message code="ili2grails.list.queryWarningTitle" default="Einige Suchparameter wurden nicht übernommen"/></h2>
            <ul class="mb-0">
                <g:each in="\${listQueryWarnings}" var="warning"><li>\${warning}</li></g:each>
            </ul>
        </div>
    </g:if>

    <g:render template="list-filters" model="\${[
        entityName: entityName,
        labelPrefix: '${propertyName}',
        typedFilters: typedFilters,
        prominentFilterFields: prominentFilterFields,
        advancedFilterFields: advancedFilterFields,
        filterOptions: filterOptions,
        listQuery: listQuery,
        activeFilters: activeFilters,
        activeFilterChips: activeFilterChips
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

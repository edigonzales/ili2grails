<section class="ili-list-tools" aria-label="\${message(code: 'ili2grails.list.search', default: 'Suche')}">
    <g:form action="index" method="GET" class="ili-list-query-form">
        <input type="hidden" name="sort" value="\${listQuery?.sort ?: 'id'}" />
        <input type="hidden" name="order" value="\${listQuery?.order ?: 'asc'}" />
        <div class="ili-list-search-row">
            <div class="flex-grow-1">
                <div class="input-group ili-search-input-group">
                    <span class="input-group-text ili-search-icon" aria-hidden="true"><ili:icon name="search"/></span>
                    <input id="list-search" type="search" name="q" value="\${listQuery?.q ?: ''}"
                           class="form-control ili-search-input" aria-label="\${message(code: 'ili2grails.list.search', default: 'Suche')}" autocomplete="off"
                           placeholder="\${message(code: 'ili2grails.list.searchPlaceholder', args: [entityName], default: 'Nach ' + entityName + ' suchen')}" />
                </div>
            </div>
            <div class="ili-list-search-action">
                <button type="submit" class="btn btn-primary"><g:message code="ili2grails.list.searchSubmit" default="Suchen"/></button>
            </div>
        </div>

        <g:if test="\${prominentFilterFields}">
            <div class="ili-quick-filter-row" aria-label="\${message(code: 'ili2grails.list.quickFilters', default: 'Schnellfilter')}">
                <g:each in="\${prominentFilterFields}" var="filterField">
                    <g:render template="list-filter-field" model="\${[
                        filterField: filterField,
                        labelPrefix: labelPrefix,
                        listQuery: listQuery,
                        filterOptions: filterOptions,
                        compact: true
                    ]}"/>
                </g:each>
            </div>
        </g:if>

        <g:if test="\${advancedFilterFields}">
            <details class="ili-filter-panel" \${activeFilterChips ? 'open' : ''}>
                <summary>
                    <g:if test="\${prominentFilterFields}">
                        <g:message code="ili2grails.list.moreFilters" default="Weitere Filter"/>
                    </g:if>
                    <g:else>
                        <g:message code="ili2grails.list.filters" default="Filter"/>
                    </g:else>
                </summary>
                <div class="ili-filter-grid">
                    <g:each in="\${advancedFilterFields}" var="filterField">
                        <g:render template="list-filter-field" model="\${[
                            filterField: filterField,
                            labelPrefix: labelPrefix,
                            listQuery: listQuery,
                            filterOptions: filterOptions,
                            compact: false
                        ]}"/>
                    </g:each>
                </div>
            </details>
        </g:if>
    </g:form>

    <g:if test="\${activeFilterChips || listQuery?.q}">
        <div class="ili-active-filters" aria-label="\${message(code: 'ili2grails.list.activeFilters', default: 'Aktive Filter')}">
            <span class="ili-active-filters-label"><g:message code="ili2grails.list.active" default="Aktiv:"/></span>
            <g:if test="\${listQuery?.q}">
                <g:link action="index" params="\${ch.interlis.generator.grails.runtime.InterlisListQuerySupport.removeFilterParams(listQuery, '__none__') + [q: null, offset: 0]}"
                        class="badge rounded-pill ili-active-filter-badge text-decoration-none">
                    \${message(code: 'ili2grails.list.queryFilter', args: [listQuery.q], default: 'Suche: ' + listQuery.q)} <span aria-hidden="true">&times;</span>
                </g:link>
            </g:if>
            <g:each in="\${activeFilterChips ?: []}" var="chip">
                <g:link action="index" params="\${chip.removeParams}"
                        class="badge rounded-pill ili-active-filter-badge text-decoration-none">
                    \${chip.label}: \${chip.value} <span aria-hidden="true">&times;</span>
                </g:link>
            </g:each>
            <g:link action="index" class="ili-data-link"><g:message code="ili2grails.list.resetFilters" default="Alle zurücksetzen"/></g:link>
        </div>
    </g:if>
</section>

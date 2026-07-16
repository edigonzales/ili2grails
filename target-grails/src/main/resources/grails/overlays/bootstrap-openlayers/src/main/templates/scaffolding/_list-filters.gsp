<section class="ili-list-tools" aria-labelledby="list-search-heading">
    <g:form action="index" method="GET" class="ili-list-query-form">
        <input type="hidden" name="sort" value="\${listQuery?.sort ?: 'id'}" />
        <input type="hidden" name="order" value="\${listQuery?.order ?: 'asc'}" />
        <div class="ili-list-search-row">
            <div class="flex-grow-1">
                <label class="form-label" for="list-search">Suche</label>
                <div class="input-group input-group-lg">
                    <span class="input-group-text" aria-hidden="true"><ili:icon name="search"/></span>
                    <input id="list-search" type="search" name="q" value="\${listQuery?.q ?: ''}"
                           class="form-control" autocomplete="off" placeholder="\${'Nach ' + entityName + ' suchen'}" />
                </div>
            </div>
            <div class="ili-list-page-size">
                <label class="form-label" for="list-max">Pro Seite</label>
                <g:select id="list-max" name="max" from="\${[10, 25, 50, 100]}"
                          value="\${listQuery?.max ?: 25}" class="form-select form-select-lg" />
            </div>
            <div class="ili-list-search-action">
                <button type="submit" class="btn btn-primary btn-lg">Suchen</button>
            </div>
        </div>

        <g:if test="\${prominentFilterFields}">
            <div class="ili-quick-filter-row" aria-label="Schnellfilter">
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
            <details class="ili-filter-panel">
                <summary>Weitere Filter</summary>
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
        <div class="ili-active-filters" aria-label="Aktive Filter">
            <span class="ili-active-filters-label">Aktiv:</span>
            <g:if test="\${listQuery?.q}">
                <g:link action="index" params="\${ch.interlis.generator.grails.runtime.InterlisListQuerySupport.removeFilterParams(listQuery, '__none__') + [q: null, offset: 0]}"
                        class="badge rounded-pill text-bg-light border text-decoration-none">
                    Suche: \${listQuery.q} <span aria-hidden="true">&times;</span>
                </g:link>
            </g:if>
            <g:each in="\${activeFilterChips ?: []}" var="chip">
                <g:link action="index" params="\${chip.removeParams}"
                        class="badge rounded-pill text-bg-light border text-decoration-none">
                    \${chip.label}: \${chip.value} <span aria-hidden="true">&times;</span>
                </g:link>
            </g:each>
            <g:link action="index" class="btn btn-sm btn-link">Alle zurücksetzen</g:link>
        </div>
    </g:if>
</section>

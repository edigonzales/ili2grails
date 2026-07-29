<g:set var="inverseSectionList" value="\${inverseRelationshipSections ?: []}"/>
<g:each in="\${inverseSectionList}" var="section">
    <g:set var="sectionLabelCode" value="\${domainPropertyName ? domainPropertyName + '.inverse.' + section.name + '.label' : null}"/>
    <g:set var="sectionLabel" value="\${sectionLabelCode ? message(code: sectionLabelCode, default: section.label) : section.label}"/>
    <section class="card ili-inverse-relationship-section"
             data-inverse-relationship-section
             data-relationship-name="\${section.name}"
             aria-labelledby="inverse-head-\${section.domId}">
        <div class="card-body">
            <header class="ili-inverse-relationship-header">
                <h2 class="ili-section-title" id="inverse-head-\${section.domId}">
                    \${sectionLabel}
                    <span class="ili-association-section-count"
                          data-inverse-total
                          data-inverse-total-value="\${section.total}">
                        <g:if test="\${section.total == 1}">
                            <g:message code="ili2grails.inverse.count.one" default="1 Eintrag"/>
                        </g:if>
                        <g:else>
                            <g:message code="ili2grails.inverse.count.many"
                                       args="\${[section.total]}"
                                       default="{0} Einträge"/>
                        </g:else>
                    </span>
                </h2>
            </header>

            <section class="ili-list-tools" aria-label="\${message(code: 'ili2grails.inverse.search', default: 'Suche')}">
                <g:form url="\${[action: 'show', id: section.ownerId]}"
                        method="GET"
                        class="ili-list-query-form"
                        role="search">
                    <g:each in="\${section.queryFormParams ?: [:]}" var="queryParam">
                        <g:hiddenField name="\${queryParam.key}" value="\${queryParam.value}" />
                    </g:each>
                    <div class="ili-list-search-row">
                        <div class="flex-grow-1">
                            <label class="visually-hidden" for="inverse-\${section.domId}-query">
                                <g:message code="ili2grails.inverse.search" default="\${sectionLabel} durchsuchen"/>
                            </label>
                            <div class="input-group ili-search-input-group">
                                <input type="search"
                                       id="inverse-\${section.domId}-query"
                                       name="inverse.\${section.name}.q"
                                       value="\${section.query ?: ''}"
                                       class="form-control ili-search-input"
                                       placeholder="\${message(code: 'ili2grails.inverse.searchPlaceholder', default: 'Suchen …')}"
                                       autocomplete="off"/>
                            </div>
                        </div>
                        <div class="ili-list-search-action">
                            <button class="btn btn-primary" type="submit">
                                <g:message code="ili2grails.action.search" default="Suchen"/>
                            </button>
                        </div>
                    </div>
                </g:form>
                <g:if test="\${section.contextualCreate}">
                    <div class="ili-list-search-action mt-3">
                        <g:link class="btn btn-primary btn-sm"
                                controller="\${section.contextualCreate.controller}"
                                action="\${section.contextualCreate.action}"
                                params="\${section.contextualCreate.params}"
                                data-inverse-contextual-create="true">
                            <ili:icon name="plus-lg" cssClass="me-1"/>\${section.relatedDomainLabel ?: section.relatedLabel} erfassen
                        </g:link>
                    </div>
                </g:if>
            </section>

            <g:if test="\${section.rows}">
                <div class="ili-table-wrap">
                    <table class="ili-association-table ili-inverse-relationship-table"
                           aria-label="\${sectionLabel}">
                        <thead>
                            <tr>
                                <g:each in="\${section.columns ?: []}" var="column">
                                    <th scope="col">
                                        <g:set var="sortParam" value="\${section.sortParams?.get(column.key)}"/>
                                        <g:if test="\${sortParam && column.sortable}">
                                            <g:link class="ili-sort-link" action="show"
                                                    id="\${section.ownerId}"
                                                    params="\${sortParam}">
                                                \${column.label}
                                            </g:link>
                                        </g:if>
                                        <g:else>\${column.label}</g:else>
                                    </th>
                                </g:each>
                            </tr>
                        </thead>
                        <tbody data-inverse-relationship-rows>
                            <g:each in="\${section.rows}" var="row">
                                <tr data-inverse-related-id="\${row.id}">
                                    <g:each in="\${section.columns ?: []}" var="column">
                                        <g:set var="cellValue" value="\${row.values?.get(column.key)}"/>
                                        <g:set var="cellLink" value="\${row.links?.get(column.key)}"/>
                                        <td>
                                            <g:if test="\${cellLink}">
                                                <g:link class="ili-data-link"
                                                        controller="\${cellLink.controller}"
                                                        action="\${cellLink.action}"
                                                        id="\${cellLink.id}">
                                                    \${cellValue ?: '—'}
                                                </g:link>
                                            </g:if>
                                            <g:else>\${cellValue ?: '—'}</g:else>
                                        </td>
                                    </g:each>
                                </tr>
                            </g:each>
                        </tbody>
                    </table>
                </div>
            </g:if>
            <g:else>
                <p class="ili-association-empty" data-inverse-empty>
                    <g:message code="ili2grails.inverse.empty" default="Keine Einträge vorhanden."/>
                </p>
            </g:else>

            <g:if test="\${section.pagination}">
                <g:set var="page" value="\${section.pagination}"/>
                <div class="ili-pagination-bar">
                    <span class="ili-list-result-summary">
                        <g:if test="\${page.showResultRange}">
                            <g:message code="ili2grails.pagination.range"
                                       args="\${[page.resultStart, page.resultEnd, page.total]}"
                                       default="{0}–{1} von {2}"/>
                        </g:if>
                        <g:else>\${page.total}</g:else>
                    </span>
                    <nav class="ili-pagination-controls"
                         aria-label="\${message(code: 'ili2grails.pagination.navigation', default: 'Seitennavigation')}">
                        <g:if test="\${page.hasPrevious}">
                            <g:link class="btn btn-outline-secondary btn-sm"
                                    action="show"
                                    id="\${section.ownerId}"
                                    params="\${page.previousParams}">
                                <g:message code="ili2grails.pagination.previous" default="Zurück"/>
                            </g:link>
                        </g:if>
                        <g:else>
                            <span class="btn btn-outline-secondary btn-sm disabled" aria-disabled="true">
                                <g:message code="ili2grails.pagination.previous" default="Zurück"/>
                            </span>
                        </g:else>
                        <g:each in="\${page.pages}" var="pageItem">
                            <g:if test="\${pageItem.ellipsis}">
                                <span class="ili-pagination-ellipsis">…</span>
                            </g:if>
                            <g:elseif test="\${pageItem.current}">
                                <span class="btn btn-primary btn-sm" aria-current="page">\${pageItem.number}</span>
                            </g:elseif>
                            <g:else>
                                <g:link class="btn btn-outline-secondary btn-sm"
                                        action="show"
                                        id="\${section.ownerId}"
                                        params="\${pageItem.params}">\${pageItem.number}</g:link>
                            </g:else>
                        </g:each>
                        <g:if test="\${page.hasNext}">
                            <g:link class="btn btn-outline-secondary btn-sm"
                                    action="show"
                                    id="\${section.ownerId}"
                                    params="\${page.nextParams}">
                                <g:message code="ili2grails.pagination.next" default="Weiter"/>
                            </g:link>
                        </g:if>
                        <g:else>
                            <span class="btn btn-outline-secondary btn-sm disabled" aria-disabled="true">
                                <g:message code="ili2grails.pagination.next" default="Weiter"/>
                            </span>
                        </g:else>
                    </nav>
                </div>
            </g:if>

            <g:render template="inverse-relationship-picker" model="\${[section: section]}"/>
        </div>
    </section>
</g:each>

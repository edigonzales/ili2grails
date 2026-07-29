<g:set var="inverseSectionList" value="\${inverseRelationshipSections ?: []}"/>
<g:each in="\${inverseSectionList}" var="section">
    <g:set var="browserModalId" value="inverse-browser-\${section.domId}"/>
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
                          data-inverse-total-value="\${section.total}"
                          data-inverse-count-one="\${message(code: 'ili2grails.inverse.count.one', default: '1 linked record')}"
                          data-inverse-count-many="\${message(code: 'ili2grails.inverse.count.many', default: '{0} linked records')}">
                        <g:if test="\${section.total == 1}">
                            <g:message code="ili2grails.inverse.count.one" default="1 linked record"/>
                        </g:if>
                        <g:else>
                            <g:message code="ili2grails.inverse.count.many"
                                       args="\${[section.total]}"
                                       default="{0} linked records"/>
                        </g:else>
                    </span>
                </h2>
            </header>

            <g:if test="\${section.rows}">
                <div class="ili-table-wrap">
                    <table class="ili-association-table ili-inverse-relationship-table"
                           aria-label="\${sectionLabel}">
                        <tbody data-inverse-relationship-rows>
                            <g:each in="\${section.rows}" var="row">
                                <tr data-inverse-related-id="\${row.id}">
                                    <td>
                                        <g:if test="\${row.controller && row.id}">
                                            <g:link class="ili-data-link"
                                                    controller="\${row.controller}"
                                                    action="show"
                                                    id="\${row.id}">
                                                \${row.label}
                                            </g:link>
                                        </g:if>
                                        <g:else>\${row.label}</g:else>
                                    </td>
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
                <table class="ili-association-table ili-inverse-relationship-table ili-inverse-empty-table"
                       hidden aria-label="\${sectionLabel}">
                    <tbody data-inverse-relationship-rows></tbody>
                </table>
            </g:else>

            <g:if test="\${section.more}">
                <button type="button"
                        class="btn btn-link ili-inverse-show-all"
                        data-bs-toggle="modal"
                        data-bs-target="#\${browserModalId}"
                        data-inverse-open-browser>
                    <g:message code="ili2grails.inverse.showAll"
                               args="\${[section.total]}"
                               default="Alle anzeigen ({0})"/>
                </button>
            </g:if>

            <div class="modal fade ili-inverse-browser"
                 id="\${browserModalId}"
                 tabindex="-1"
                 aria-labelledby="\${browserModalId}-title"
                 aria-hidden="true"
                 data-inverse-browser
                 data-page-url="\${createLink(action: 'relationshipCollectionPage', id: section.ownerId, params: [relationship: section.name])}"
                 data-page-size="25"
                 data-loading-message="\${message(code: 'ili2grails.inverse.loading', default: 'Laden …')}"
                 data-error-message="\${message(code: 'ili2grails.inverse.browserError', default: 'Beziehungen konnten nicht geladen werden.')}">
                <div class="modal-dialog modal-xl modal-dialog-scrollable">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h2 class="modal-title fs-5" id="\${browserModalId}-title">
                                \${sectionLabel}
                                <span class="ili-association-section-count"
                                      data-inverse-browser-total
                                      data-inverse-count-one="\${message(code: 'ili2grails.inverse.count.one', default: '1 linked record')}"
                                      data-inverse-count-many="\${message(code: 'ili2grails.inverse.count.many', default: '{0} linked records')}">
                                    <g:if test="\${section.total == 1}">
                                        <g:message code="ili2grails.inverse.count.one" default="1 linked record"/>
                                    </g:if>
                                    <g:else>
                                        <g:message code="ili2grails.inverse.count.many"
                                                   args="\${[section.total]}"
                                                   default="{0} linked records"/>
                                    </g:else>
                                </span>
                            </h2>
                            <button type="button" class="btn-close" data-bs-dismiss="modal"
                                    aria-label="\${message(code: 'ili2grails.workspace.close', default: 'Schliessen')}"></button>
                        </div>
                        <div class="modal-body">
                            <form class="ili-inverse-browser-search" data-inverse-browser-form>
                                <label class="form-label" for="\${browserModalId}-search">
                                    <g:message code="ili2grails.inverse.browserSearch" default="Beziehungen durchsuchen"/>
                                </label>
                                <input type="search"
                                       id="\${browserModalId}-search"
                                       class="form-control"
                                       data-inverse-browser-search
                                       placeholder="\${message(code: 'ili2grails.inverse.browserSearchPlaceholder', default: 'Nach einem Datensatz suchen …')}"
                                       autocomplete="off"/>
                            </form>

                            <p class="ili-inverse-browser-status" data-inverse-browser-status
                               role="status" aria-live="polite" hidden></p>

                            <div class="ili-table-wrap">
                                <table class="ili-association-table ili-inverse-relationship-table"
                                       aria-label="\${sectionLabel}">
                                    <tbody data-inverse-browser-rows></tbody>
                                </table>
                            </div>
                            <p class="ili-association-empty" data-inverse-browser-empty hidden>
                                <g:message code="ili2grails.inverse.emptySearch" default="Keine passenden Einträge gefunden."/>
                            </p>

                            <nav class="ili-inverse-browser-pagination"
                                 data-inverse-browser-pagination
                                 aria-label="\${message(code: 'ili2grails.pagination.navigation', default: 'Seitennavigation')}"
                                 hidden>
                                <button type="button"
                                        class="btn btn-outline-secondary btn-sm"
                                        data-inverse-browser-previous
                                        disabled>
                                    <g:message code="ili2grails.pagination.previous" default="Zurück"/>
                                </button>
                                <span class="ili-inverse-browser-range" data-inverse-browser-range></span>
                                <button type="button"
                                        class="btn btn-outline-secondary btn-sm"
                                        data-inverse-browser-next
                                        disabled>
                                    <g:message code="ili2grails.pagination.next" default="Weiter"/>
                                </button>
                            </nav>
                        </div>
                    </div>
                </div>
            </div>

            <g:render template="inverse-relationship-picker" model="\${[section: section]}"/>
        </div>
    </section>
</g:each>

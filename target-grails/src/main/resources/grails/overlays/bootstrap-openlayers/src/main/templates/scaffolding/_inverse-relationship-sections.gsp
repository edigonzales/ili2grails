<g:set var="inverseSectionList" value="\${inverseRelationshipSections ?: []}"/>
<g:each in="\${inverseSectionList}" var="section">
    <section class="card ili-inverse-relationship-section"
             data-inverse-relationship-section
             data-relationship-name="\${section.name}"
             aria-labelledby="inverse-head-\${section.domId}">
        <div class="card-body">
            <header class="ili-inverse-relationship-header">
                <h2 class="ili-section-title" id="inverse-head-\${section.domId}">
                    \${section.label}
                </h2>
                <span class="ili-association-section-count" data-inverse-total>\${section.total}</span>
            </header>

            <g:if test="\${section.rows}">
                <div class="ili-table-wrap">
                    <table class="ili-association-table" aria-label="\${section.label}">
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
                <table class="ili-association-table ili-inverse-empty-table" hidden aria-label="\${section.label}">
                    <tbody data-inverse-relationship-rows></tbody>
                </table>
            </g:else>

            <g:if test="\${section.more}">
                <button type="button"
                        class="btn btn-link ili-association-more-link"
                        data-inverse-load-more
                        data-next-offset="\${section.offset + section.rows.size()}"
                        data-page-size="\${section.max}"
                        data-page-url="\${createLink(action: 'relationshipCollectionPage', id: section.ownerId, params: [relationship: section.name])}">
                    <g:message code="ili2grails.inverse.more" default="Mehr anzeigen"/>
                </button>
            </g:if>

            <g:render template="inverse-relationship-picker" model="\${[section: section]}"/>
        </div>
    </section>
</g:each>

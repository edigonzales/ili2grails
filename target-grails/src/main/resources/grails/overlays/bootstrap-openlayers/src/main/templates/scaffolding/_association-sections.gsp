<g:set var="associationContextList" value="\${raw(associationSections ?: [])}"/>
<g:each in="\${associationContextList}" var="section" status="sectionIdx">
    <g:set var="sectionDomId" value="\${raw(section.domId ?: 'assoc-section-' + sectionIdx)}"/>
    <section class="ili-association-section" aria-labelledby="assoc-head-\${raw(sectionDomId)}">
        <header class="ili-association-section-header">
            <h3 class="ili-association-section-title" id="assoc-head-\${raw(sectionDomId)}">
                \${raw(section.label ?: section.contextId)}
            </h3>
            <span class="ili-association-section-count">\${raw(section.total)}</span>
        </header>

        <g:if test="\${section.rows == null || section.rows.isEmpty()}">
            <p class="ili-association-empty">
                <g:if test="\${section.messageCode}">
                    <g:message code="\${raw(section.messageCode)}.empty" default="\${raw(section.emptyMessage ?: 'Keine Einträge vorhanden.')}"/>
                </g:if>
                <g:else>
                    \${raw(section.emptyMessage ?: 'Keine Einträge vorhanden.')}
                </g:else>
            </p>
            <g:render template="association-quick-add" model="\${[
                section: section,
                owner: owner
            ]}"/>
        </g:if>
        <g:else>
            <div class="ili-table-wrap">
                <table class="ili-association-table" aria-label="\${raw(section.label ?: section.contextId)}">
                    <thead>
                        <tr>
                            <g:each in="\${section.columns ?: []}" var="col">
                                <th scope="col">\${raw(col.label)}</th>
                            </g:each>
                            <th scope="col" class="ili-association-actions-header"></th>
                        </tr>
                    </thead>
                    <tbody>
                        <g:each in="\${section.rows}" var="row">
                            <tr>
                                <g:each in="\${row.counterparts ?: []}" var="counterpart">
                                    <td>
                                        <g:if test="\${counterpart.controller && counterpart.id}">
                                            <g:link controller="\${raw(counterpart.controller)}" action="show" id="\${raw(counterpart.id)}">
                                                \${raw(counterpart.label ?: counterpart.id)}
                                            </g:link>
                                        </g:if>
                                        <g:else>
                                            \${raw(counterpart.label ?: counterpart.id)}
                                        </g:else>
                                    </td>
                                </g:each>
                                <g:each in="\${row.attributes ?: []}" var="attr">
                                    <td>
                                        <g:if test="\${attr.value instanceof java.time.temporal.TemporalAccessor}">
                                            <g:formatDate date="\${raw(attr.value)}"/>
                                        </g:if>
                                        <g:elseif test="\${attr.value instanceof Enum}">
                                            \${raw(attr.value.name())}
                                        </g:elseif>
                                        <g:else>
                                            \${raw(attr.value != null ? attr.value.toString() : '')}
                                        </g:else>
                                    </td>
                                </g:each>
                                <td class="ili-association-row-actions">
                                    <g:if test="\${row.associationController && row.associationId}">
                                        <g:link controller="\${raw(row.associationController)}" action="show" id="\${raw(row.associationId)}" class="btn btn-sm btn-outline-secondary">
                                            Details
                                        </g:link>
                                    </g:if>
                                    <g:if test="\${row.deleteAllowed}">
                                        <button type="button" class="btn btn-sm btn-outline-danger ili-association-delete-btn"
                                                data-association-delete
                                                data-delete-form="assoc-delete-\${raw(sectionDomId)}-\${raw(row.associationId)}"
                                                data-association-label="\${raw(row.associationLabel ?: '')}">
                                            Entfernen
                                        </button>
                                    </g:if>
                                </td>
                            </tr>
                        </g:each>
                    </tbody>
                </table>
            </div>

            <g:if test="\${section.writable}">
                <g:render template="association-quick-add" model="\${[
                    section: section,
                    owner: owner
                ]}"/>
            </g:if>

            <g:if test="\${section.more}">
                <g:link action="associationPage" id="\${raw(owner?.id)}" params="\${[context: section.contextId]}" class="ili-association-more-link">
                    Mehr anzeigen
                </g:link>
            </g:if>
        </g:else>

        <g:each in="\${section.rows ?: []}" var="row">
            <g:if test="\${row.deleteAllowed && row.associationId}">
                <g:form action="associationDelete"
                        id="\${raw(owner?.id)}"
                        method="DELETE"
                        name="assoc-delete-\${raw(sectionDomId)}-\${raw(row.associationId)}"
                        class="ili-hidden-delete-form">
                    <g:hiddenField name="context" value="\${raw(section.contextId)}"/>
                    <g:hiddenField name="associationId" value="\${raw(row.associationId)}"/>
                    <button type="submit" class="ili-native-submit js-delete-submit">Delete</button>
                </g:form>
            </g:if>
        </g:each>
    </section>
</g:each>

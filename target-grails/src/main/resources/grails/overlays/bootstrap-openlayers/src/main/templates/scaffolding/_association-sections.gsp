<g:set var="associationContextList" value="\${associationSections ?: []}"/>
<g:each in="\${associationContextList}" var="section" status="sectionIdx">
    <g:set var="sectionDomId" value="\${section.domId ?: 'assoc-section-' + sectionIdx}"/>
    <g:set var="contextualForm" value="\${section.createMode == 'CONTEXTUAL_FORM' || section.createMode == 'NARY_CONTEXTUAL_FORM'}"/>
    <section class="ili-association-section" aria-labelledby="assoc-head-\${sectionDomId}">
        <header class="ili-association-section-header">
            <h3 class="ili-association-section-title" id="assoc-head-\${sectionDomId}">
                \${section.label ?: section.contextId}
            </h3>
            <span class="ili-association-section-count">\${section.total}</span>
        </header>

        <g:if test="\${section.rows == null || section.rows.isEmpty()}">
            <p class="ili-association-empty">
                <g:if test="\${section.messageCode}">
                    <g:message code="\${section.messageCode}.empty" default="\${section.emptyMessage ?: message(code: 'ili2grails.association.empty', default: 'Keine Einträge vorhanden.') }"/>
                </g:if>
                <g:else>
                    \${section.emptyMessage ?: message(code: 'ili2grails.association.empty', default: 'Keine Einträge vorhanden.')}
                </g:else>
            </p>
            <g:render template="association-quick-add" model="\${[
                section: section,
                owner: owner
            ]}"/>
            <g:if test="\${contextualForm && section.writable}">
                <g:if test="\${runtimeWriteAllowed}">
                    <g:link controller="\${section.associationController ?: ''}" action="create"
                            params="\${[associationContext: section.contextId, associationOwnerId: owner?.id]}"
                            class="btn btn-primary btn-sm mt-2">
                        \${message(code: 'ili2grails.association.add', args: [section.label], default: section.label + ' hinzufügen')}
                    </g:link>
                </g:if>
            </g:if>
        </g:if>
        <g:else>
            <div class="ili-table-wrap">
                <table class="ili-association-table" aria-label="\${section.label ?: section.contextId}">
                    <thead>
                        <tr>
                            <g:each in="\${section.columns ?: []}" var="col">
                                <th scope="col">\${col.label}</th>
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
                                            <g:link class="ili-data-link" controller="\${counterpart.controller}" action="show" id="\${counterpart.id}">
                                                \${counterpart.label ?: counterpart.id}
                                            </g:link>
                                        </g:if>
                                        <g:else>
                                            \${counterpart.label ?: counterpart.id}
                                        </g:else>
                                    </td>
                                </g:each>
                                <g:each in="\${row.attributes ?: []}" var="attr">
                                    <td>
                                        <g:if test="\${attr.value instanceof java.time.temporal.TemporalAccessor}">
                                            <g:formatDate date="\${attr.value}"/>
                                        </g:if>
                                        <g:elseif test="\${attr.value instanceof Enum}">
                                            \${attr.value.name()}
                                        </g:elseif>
                                        <g:else>
                                            \${attr.value != null ? attr.value.toString() : ''}
                                        </g:else>
                                    </td>
                                </g:each>
                                <td class="ili-association-row-actions">
                                    <g:if test="\${row.editAllowed && row.associationController && row.associationId}">
                                        <g:link controller="\${row.associationController}" action="edit"
                                                id="\${row.associationId}"
                                                params="\${[associationContext: section.contextId, associationOwnerId: owner?.id]}"
                                                class="btn btn-sm btn-outline-primary">
                                            <g:message code="ili2grails.action.edit" default="Bearbeiten"/>
                                        </g:link>
                                    </g:if>
                                    <g:if test="\${row.associationController && row.associationId}">
                                        <g:link controller="\${row.associationController}" action="show" id="\${row.associationId}" class="btn btn-sm btn-outline-secondary">
                                            <g:message code="ili2grails.action.details" default="Details"/>
                                        </g:link>
                                    </g:if>
                                    <g:if test="\${runtimeWriteAllowed && row.deleteAllowed}">
                                        <button type="button" class="btn btn-sm btn-outline-danger ili-association-delete-btn"
                                                data-association-delete
                                                data-delete-form="assoc-delete-\${sectionDomId}-\${row.associationId}"
                                                data-association-label="\${row.associationLabel ?: ''}">
                                            <g:message code="ili2grails.action.remove" default="Entfernen"/>
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

            <g:if test="\${contextualForm && section.writable}">
                <g:if test="\${runtimeWriteAllowed}">
                    <g:link controller="\${section.associationController ?: ''}" action="create"
                            params="\${[associationContext: section.contextId, associationOwnerId: owner?.id]}"
                            class="btn btn-primary btn-sm mt-2">
                        \${message(code: 'ili2grails.association.add', args: [section.label], default: section.label + ' hinzufügen')}
                    </g:link>
                </g:if>
            </g:if>

            <g:if test="\${section.more}">
                <g:link action="associationPage" id="\${owner?.id}" params="\${[context: section.contextId]}" class="ili-association-more-link">
                    <g:message code="ili2grails.association.more" default="Mehr anzeigen"/>
                </g:link>
            </g:if>
        </g:else>

        <g:each in="\${section.rows ?: []}" var="row">
            <g:if test="\${row.deleteAllowed && row.associationId}">
                <g:form action="associationDelete"
                        id="\${owner?.id}"
                        method="DELETE"
                        name="assoc-delete-\${sectionDomId}-\${row.associationId}"
                        class="ili-hidden-delete-form">
                    <g:hiddenField name="context" value="\${section.contextId}"/>
                    <g:hiddenField name="associationId" value="\${row.associationId}"/>
                    <button type="submit" class="ili-native-submit js-delete-submit"><g:message code="ili2grails.action.delete" default="Löschen"/></button>
                </g:form>
            </g:if>
        </g:each>
    </section>
</g:each>

<section class="card ili-workspace-relationships" data-workspace-relationships>
    <div class="card-body">
        <header class="ili-section-heading">
            <h2 class="ili-section-title h5 mb-0"><g:message code="ili2grails.ui.linkedRecords" default="Verknüpfte Datensätze"/></h2>
        </header>
        <g:if test="${relationshipLinks}">
            <dl class="ili-definition-list">
                <g:each in="${relationshipLinks}" var="relationshipLink">
                    <div class="ili-definition-row" data-workspace-relationship="${relationshipLink.name}">
                        <dt>
                            <g:message code="${domainPropertyName}.${relationshipLink.name}.label"
                                       default="${relationshipLink.label ?: relationshipLink.name}"/>
                        </dt>
                        <dd>
                            <g:if test="${relationshipLink.controller && relationshipLink.id}">
                                <g:link controller="${relationshipLink.controller}"
                                        action="show"
                                        id="${relationshipLink.id}"
                                        class="ili-data-link ili-workspace-relationship-link">
                                    ${relationshipLink.valueLabel ?: relationshipLink.id}
                                </g:link>
                            </g:if>
                            <g:else>
                                <span class="text-body-secondary"><g:message code="ili2grails.workspace.noAssociation" default="Keine Zuordnung"/></span>
                            </g:else>
                        </dd>
                    </div>
                </g:each>
            </dl>
        </g:if>
        <g:else>
            <p class="text-body-secondary mb-0"><g:message code="ili2grails.ui.noLinkedRecords" default="Keine verknüpften Datensätze vorhanden."/></p>
        </g:else>
    </div>
</section>

<section class="card ili-details-tile ili-workspace-details" data-workspace-details>
    <div class="card-body">
        <g:each in="${detailSections ?: []}" var="detailSection" status="sectionIndex">
            <section class="ili-workspace-detail-section" data-workspace-detail-section="${detailSection.title ?: sectionIndex}">
                <h2 class="ili-section-title h5">${detailSection.title ?: message(code: 'ili2grails.workspace.details', default: 'Details')}</h2>
                <dl class="ili-definition-list">
                    <g:each in="${detailSection.fields ?: []}" var="detailField">
                        <div class="ili-definition-row" data-workspace-detail-field="${detailField.name}">
                            <dt>
                                <g:message code="${domainPropertyName}.${detailField.name}.label"
                                           default="${detailField.label ?: detailField.name}"/>
                            </dt>
                            <dd>${detailField.value ?: '—'}</dd>
                        </div>
                    </g:each>
                </dl>
            </section>
        </g:each>
        <g:if test="${!(detailSections ?: [])}">
            <p class="text-body-secondary mb-0"><g:message code="ili2grails.workspace.noDetails" default="Keine skalaren Detailattribute vorhanden."/></p>
        </g:if>
    </div>
</section>

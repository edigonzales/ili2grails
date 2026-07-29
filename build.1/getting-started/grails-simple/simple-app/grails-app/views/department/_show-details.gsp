<section class="card ili-details-tile">
    <div class="card-body">
        <h2 class="ili-section-title h5"><g:message code="ili2grails.workspace.details" default="Details"/></h2>
        <dl class="ili-definition-list">
            <g:each in="${detailColumns ?: []}" var="detailColumn">
                <div class="ili-definition-row">
                    <dt>${message(code: 'department.' + detailColumn + '.label', default: detailColumn)}</dt>
                    <dd>${detailValues?.get(detailColumn) ?: '-'}</dd>
                </div>
            </g:each>
        </dl>
    </div>
</section>

<g:if test="\${row?.associationController && row?.associationId}">
    <g:link controller="\${raw(row.associationController)}" action="show" id="\${raw(row.associationId)}" class="btn btn-sm btn-outline-secondary">
        <g:message code="default.button.show.label" default="Show"/>
    </g:link>
</g:if>
<g:each in="\${row?.counterparts ?: []}" var="cp">
    <g:if test="\${cp?.controller && cp?.id}">
        <g:link controller="\${raw(cp.controller)}" action="show" id="\${raw(cp.id)}" class="btn btn-sm btn-outline-secondary">
            &Ouml;ffnen
        </g:link>
    </g:if>
</g:each>

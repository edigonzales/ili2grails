<g:if test="\${row?.associationController && row?.associationId}">
    <g:link controller="\${row.associationController}" action="show" id="\${row.associationId}" class="btn btn-sm btn-outline-secondary">
        <g:message code="ili2grails.action.show" default="Anzeigen"/>
    </g:link>
</g:if>
<g:each in="\${row?.counterparts ?: []}" var="cp">
    <g:if test="\${cp?.controller && cp?.id}">
        <g:link controller="\${cp.controller}" action="show" id="\${cp.id}" class="btn btn-sm btn-outline-secondary">
            <g:message code="ili2grails.action.open" default="Öffnen"/>
        </g:link>
    </g:if>
</g:each>

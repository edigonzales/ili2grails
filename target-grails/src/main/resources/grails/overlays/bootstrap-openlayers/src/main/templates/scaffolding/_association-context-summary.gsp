<g:if test="\${associationContextState}">
    <div class="ili-context-summary card mb-3">
        <div class="card-body">
            <h5 class="card-title"><g:message code="ili2grails.association.context" default="Kontext"/></h5>
            <p class="card-text">
                \${associationContextState.ownerLabel ?: message(code: 'ili2grails.association.noContext', default: 'Kein Kontext')}
            </p>
        </div>
    </div>
</g:if>

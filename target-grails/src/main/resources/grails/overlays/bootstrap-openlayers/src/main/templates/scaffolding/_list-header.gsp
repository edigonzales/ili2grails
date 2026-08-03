<section class="ili-page-header ili-list-header">
    <div>
        <h1 class="ili-page-title">\${entityName}</h1>
    </div>
    <div class="ili-page-actions">
        <g:if test="\${runtimeWriteAllowed}">
        <g:link action="create" class="btn btn-primary">
            <ili:icon name="plus-lg" cssClass="me-1"/><g:message code="ili2grails.action.new" default="Neu"/>
        </g:link>
        </g:if>
    </div>
</section>

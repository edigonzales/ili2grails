<section class="ili-workspace-header ili-page-header" data-domain-workspace-header>
    <div class="ili-workspace-heading">
        <p class="ili-eyebrow" data-workspace-domain-label="true">${domainLabel ?: message(code: 'ili2grails.workspace.domain', default: 'Domain')}</p>
        <h1 class="ili-page-title" data-workspace-display-label="true">${displayLabel ?: ('#' + instance?.id)}</h1>
        <p class="ili-page-subtitle">
            ${domainLabel ?: message(code: 'ili2grails.workspace.record', default: 'Datensatz')}
            <g:if test="${instance?.id != null}"> · #${instance.id}</g:if>
        </p>
    </div>
    <nav class="ili-page-actions" aria-label="${message(code: 'ili2grails.workspace.actions', default: 'Objektaktionen')}">
        <g:link class="btn btn-outline-secondary" controller="${controllerName}" action="index">
            <ili:icon name="list" cssClass="me-1"/><g:message code="ili2grails.workspace.list" default="Liste"/>
        </g:link>
        <g:link class="btn btn-outline-primary" controller="${controllerName}" action="create">
            <ili:icon name="plus-lg" cssClass="me-1"/><g:message code="ili2grails.workspace.new" default="Neu"/>
        </g:link>
        <g:link class="btn btn-primary" controller="${controllerName}" action="edit" id="${instance?.id}">
            <ili:icon name="pencil" cssClass="me-1"/><g:message code="ili2grails.workspace.edit" default="Bearbeiten"/>
        </g:link>
    </nav>
</section>

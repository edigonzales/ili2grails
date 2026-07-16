<section class="ili-workspace-header ili-page-header" data-domain-workspace-header>
    <div class="ili-workspace-heading">
        <p class="ili-eyebrow" data-workspace-domain-label="true">${domainLabel ?: 'Domain'}</p>
        <h1 class="ili-page-title" data-workspace-display-label="true">${displayLabel ?: ('#' + instance?.id)}</h1>
        <p class="ili-page-subtitle">
            ${domainLabel ?: 'Datensatz'}
            <g:if test="${instance?.id != null}"> · #${instance.id}</g:if>
        </p>
    </div>
    <nav class="ili-page-actions" aria-label="Objektaktionen">
        <g:link class="btn btn-outline-secondary" controller="${controllerName}" action="index">
            <ili:icon name="list" cssClass="me-1"/>Liste
        </g:link>
        <g:link class="btn btn-outline-primary" controller="${controllerName}" action="create">
            <ili:icon name="plus-lg" cssClass="me-1"/>Neu
        </g:link>
        <g:link class="btn btn-primary" controller="${controllerName}" action="edit" id="${instance?.id}">
            <ili:icon name="pencil" cssClass="me-1"/>Bearbeiten
        </g:link>
    </nav>
</section>

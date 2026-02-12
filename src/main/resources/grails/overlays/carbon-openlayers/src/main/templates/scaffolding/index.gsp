<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <g:set var="entityName" value="\${message(code: '${propertyName}.label', default: '${className}')}" />
    <title><g:message code="default.list.label" args="[entityName]" /></title>
</head>
<body>
<div id="content" role="main" class="ili-page ili-page-list">
    <section class="ili-page-header">
        <div>
            <h1 class="ili-page-title"><g:message code="default.list.label" args="[entityName]" /></h1>
            <p class="ili-page-subtitle">Server-side rendered CRUD mit modernem Scaffold-Theme.</p>
        </div>
        <div class="ili-page-actions">
            <g:link class="bx--btn bx--btn--primary" aria-label="Create" action="create">
                <g:message code="default.new.label" args="[entityName]" />
            </g:link>
        </div>
    </section>

    <g:if test="\${flash.message}">
        <div class="bx--inline-notification bx--inline-notification--info" role="status">
            <div class="bx--inline-notification__text-wrapper">\${flash.message}</div>
        </div>
    </g:if>

    <section class="bx--tile ili-tile">
        <f:table class="scaffold bx--data-table bx--data-table--compact bx--data-table--zebra" controller="\${controllerName}" collection="\${${propertyName}List}"/>

        <g:if test="\${${propertyName}Count > params.int('max')}">
            <div class="ili-pagination-wrap">
                <g:paginate total="\${${propertyName}Count ?: 0}" />
            </div>
        </g:if>
    </section>
</div>
</body>
</html>

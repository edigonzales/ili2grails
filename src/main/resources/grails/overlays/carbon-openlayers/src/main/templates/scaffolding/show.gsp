<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <g:set var="entityName" value="\${message(code: '${propertyName}.label', default: '${className}')}" />
    <title><g:message code="default.show.label" args="[entityName]" /></title>
</head>
<body>
<div id="content" role="main" class="ili-page ili-page-show">
    <section class="ili-page-header">
        <div>
            <h1 class="ili-page-title"><g:message code="default.show.label" args="[entityName]" /></h1>
            <p class="ili-page-subtitle">Details der Entität inklusive Geometrie.</p>
        </div>
        <div class="ili-page-actions">
            <g:link class="bx--btn bx--btn--tertiary" aria-label="List" action="index">
                <g:message code="default.list.label" args="[entityName]" />
            </g:link>
            <g:link class="bx--btn bx--btn--secondary" aria-label="Create" action="create">
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
        <f:display bean="${propertyName}" listClass="container" listItemClass="row mb-3" labelClass="form-label col-sm-3 text-sm-end" valueClass="col-sm-9" except="\${geometryFields ?: []}" />
    </section>

    <g:if test="\${geometryFields}">
        <section class="bx--tile ili-tile ili-geometry-section">
            <h2 class="ili-section-title">Geometrie</h2>
            <g:each in="\${geometryFields}" var="geomField">
                <article class="ili-geometry-tile">
                    <header class="ili-geometry-header">
                        <strong>\${geomField}</strong>
                        <span class="bx--tag bx--tag--cool-gray">\${geometryKinds?.get(geomField) ?: 'GEOMETRY'}</span>
                    </header>
                    <div class="ili-geometry-editor"
                         data-geometry-field="\${geomField}"
                         data-geometry-kind="\${geometryKinds?.get(geomField) ?: 'GEOMETRY'}"
                         data-geometry-srid="\${geometrySrids?.get(geomField) ?: ''}"
                         data-geometry-mode="view">
                        <input type="hidden" class="js-geometry-wkt" value="\${geometryValues?.get(geomField) ?: ''}"/>
                        <div class="ili-geometry-map"></div>
                    </div>
                </article>
            </g:each>
        </section>
    </g:if>

    <g:form resource="\${this.${propertyName}}" controller="\${controllerName}" method="DELETE" class="ili-form-actions">
        <g:link class="bx--btn bx--btn--secondary" action="edit" resource="\${this.${propertyName}}" controller="\${controllerName}">
            <g:message code="default.button.edit.label" default="Edit" />
        </g:link>
        <button class="bx--btn bx--btn--danger" type="submit" onclick="return confirm('\${message(code: 'default.button.delete.confirm.message', default: 'Are you sure?')}');">
            \${message(code: 'default.button.delete.label', default: 'Delete')}
        </button>
    </g:form>
</div>
</body>
</html>

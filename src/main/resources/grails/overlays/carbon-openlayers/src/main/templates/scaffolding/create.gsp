<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <g:set var="entityName" value="\${message(code: '${propertyName}.label', default: '${className}')}" />
    <title><g:message code="default.create.label" args="[entityName]" /></title>
</head>
<body>
<div id="content" role="main" class="ili-page ili-page-form">
    <section class="ili-page-header">
        <div>
            <h1 class="ili-page-title"><g:message code="default.create.label" args="[entityName]" /></h1>
            <p class="ili-page-subtitle">Neue Entität erfassen.</p>
        </div>
        <div class="ili-page-actions">
            <g:link class="bx--btn bx--btn--tertiary" aria-label="List" action="index">
                <g:message code="default.list.label" args="[entityName]" />
            </g:link>
        </div>
    </section>

    <g:if test="\${flash.message}">
        <div class="bx--inline-notification bx--inline-notification--info" role="status">
            <div class="bx--inline-notification__text-wrapper">\${flash.message}</div>
        </div>
    </g:if>

    <g:hasErrors bean="\${this.${propertyName}}">
        <ul class="bx--list--unordered ili-error-list" role="alert">
            <g:eachError bean="\${this.${propertyName}}" var="error">
                <li><g:message error="\${error}"/></li>
            </g:eachError>
        </ul>
    </g:hasErrors>

    <g:form resource="\${this.${propertyName}}" controller="\${controllerName}" method="POST" class="ili-form">
        <section class="bx--tile ili-tile">
            <fieldset class="form">
                <f:all bean="${propertyName}"
                       except="\${geometryFields ?: []}"
                       class="row"
                       requiredClass="mb-3 required"
                       labelClass="col-sm-2 col-form-label text-sm-end"
                       divClass="col-sm-10"
                       widget-class="bx--text-input"
                       widget-invalidClass="bx--text-input--invalid"
                       widget-selectDateClass="w-auto bx--select-input d-inline"
                       widget-checkBoxClass="bx--checkbox" />
            </fieldset>
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
                             data-geometry-mode="edit">
                            <input type="hidden" name="\${geomField}Wkt" value="\${geometryValues?.get(geomField) ?: ''}" class="js-geometry-wkt"/>
                            <div class="ili-geometry-toolbar">
                                <div class="ili-geometry-type-picker js-geometry-type-picker">
                                    <label for="geom-type-\${geomField}" class="ili-geometry-type-label">Typ</label>
                                    <select id="geom-type-\${geomField}" class="bx--select-input js-geometry-draw-type">
                                        <option value="Point">Punkt</option>
                                        <option value="LineString">Linie</option>
                                        <option value="Polygon">Polygon</option>
                                    </select>
                                </div>
                                <button type="button" class="bx--btn bx--btn--secondary" data-geometry-action="draw">Zeichnen</button>
                                <button type="button" class="bx--btn bx--btn--tertiary" data-geometry-action="modify">Ändern</button>
                                <button type="button" class="bx--btn bx--btn--danger--tertiary" data-geometry-action="clear">Löschen</button>
                            </div>
                            <div class="ili-geometry-map"></div>
                        </div>
                    </article>
                </g:each>
            </section>
        </g:if>

        <footer class="ili-form-actions">
            <button class="bx--btn bx--btn--primary" type="submit">
                \${message(code: 'default.button.create.label', default: 'Create')}
            </button>
        </footer>
    </g:form>
</div>
</body>
</html>

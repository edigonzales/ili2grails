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
            <h1 class="ili-page-title"><g:message code="default.show.label" args="[entityName]" /> #\${this.${propertyName}?.id}</h1>
            <p class="ili-page-subtitle">Detailansicht mit Geometrie und sicheren Destruktiv-Aktionen.</p>
        </div>
        <div class="ili-page-actions">
            <g:link class="btn btn-outline-secondary" action="index">
                <g:message code="default.list.label" args="[entityName]" />
            </g:link>
            <g:link class="btn btn-outline-primary" action="create">
                <g:message code="default.new.label" args="[entityName]" />
            </g:link>
            <g:link class="btn btn-primary" action="edit" resource="\${this.${propertyName}}" controller="\${controllerName}">
                <g:message code="default.button.edit.label" default="Edit" />
            </g:link>
        </div>
    </section>

    <g:if test="\${flash.message}">
        <div class="alert alert-info" role="status">\${flash.message}</div>
    </g:if>

    <div class="ili-split-layout \${geometryFields ? 'ili-split-with-map' : 'ili-split-single'}">
        <section class="ili-form-column">
            <g:render template="show-details" model="\${[
                detailColumns: detailColumns,
                detailValues: detailValues
            ]}"/>
        </section>

        <g:if test="\${geometryFields}">
            <aside class="ili-map-column">
                <g:render template="geometry-panel" model="\${[
                    geometryFields: geometryFields,
                    geometryValues: geometryValues,
                    geometryKinds: geometryKinds,
                    geometrySrids: geometrySrids,
                    geometryMode: 'view'
                ]}"/>
            </aside>
        </g:if>
    </div>

    <section class="card ili-danger-zone">
        <div class="card-body">
            <header class="ili-danger-zone-head">
                <h2 class="ili-section-title">Danger Zone</h2>
                <span class="badge text-bg-danger">Destruktiv</span>
            </header>
            <p class="mb-3">Das Löschen ist endgültig und kann nicht rückgängig gemacht werden.</p>
            <button type="button" class="btn btn-danger" data-delete-open="delete-modal-${propertyName}" data-bs-toggle="modal" data-bs-target="#delete-modal-${propertyName}">
                \${message(code: 'default.button.delete.label', default: 'Delete')}
            </button>
        </div>
    </section>

    <g:form resource="\${this.${propertyName}}"
            controller="\${controllerName}"
            method="DELETE"
            id="delete-form-${propertyName}"
            class="ili-hidden-delete-form">
        <button type="submit" class="ili-native-submit js-delete-submit">Delete</button>
    </g:form>

    <div class="modal fade" id="delete-modal-${propertyName}" tabindex="-1" aria-labelledby="delete-modal-title-${propertyName}" aria-hidden="true" data-delete-modal>
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h2 class="modal-title fs-5" id="delete-modal-title-${propertyName}">Objekt wirklich löschen?</h2>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Schliessen"></button>
                </div>
                <div class="modal-body">
                    <p class="mb-0">Diese Aktion löscht den Datensatz dauerhaft.</p>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Abbrechen</button>
                    <button type="button" class="btn btn-danger"
                            data-delete-confirm="true"
                            data-delete-form="delete-form-${propertyName}">
                        Löschen
                    </button>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
</html>

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
            <g:link class="ili-link-btn" action="index">
                <bx-btn kind="tertiary"><g:message code="default.list.label" args="[entityName]" /></bx-btn>
            </g:link>
            <g:link class="ili-link-btn" action="create">
                <bx-btn kind="secondary"><g:message code="default.new.label" args="[entityName]" /></bx-btn>
            </g:link>
            <g:link class="ili-link-btn" action="edit" resource="\${this.${propertyName}}" controller="\${controllerName}">
                <bx-btn kind="primary"><g:message code="default.button.edit.label" default="Edit" /></bx-btn>
            </g:link>
        </div>
    </section>

    <g:if test="\${flash.message}">
        <bx-inline-notification kind="info" title="Hinweis" subtitle="\${flash.message}"></bx-inline-notification>
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

    <section class="bx--tile ili-danger-zone">
        <header class="ili-danger-zone-head">
            <h2 class="ili-section-title">Danger Zone</h2>
            <bx-tag type="red">Destruktiv</bx-tag>
        </header>
        <p>Das Löschen ist endgültig und kann nicht rückgängig gemacht werden.</p>
        <bx-btn kind="danger" data-delete-open="delete-modal-${propertyName}">
            \${message(code: 'default.button.delete.label', default: 'Delete')}
        </bx-btn>
    </section>

    <g:form resource="\${this.${propertyName}}"
            controller="\${controllerName}"
            method="DELETE"
            id="delete-form-${propertyName}"
            class="ili-hidden-delete-form">
        <button type="submit" class="ili-native-submit js-delete-submit">Delete</button>
    </g:form>

    <bx-modal id="delete-modal-${propertyName}" data-delete-modal>
        <bx-modal-header>
            <bx-modal-label>Danger Zone</bx-modal-label>
            <bx-modal-heading>Objekt wirklich löschen?</bx-modal-heading>
        </bx-modal-header>
        <bx-modal-body>
            <p>Diese Aktion löscht den Datensatz dauerhaft.</p>
        </bx-modal-body>
        <bx-modal-footer>
            <bx-modal-footer-button kind="secondary" data-modal-close>Abbrechen</bx-modal-footer-button>
            <bx-modal-footer-button kind="danger"
                                    data-delete-confirm="true"
                                    data-delete-form="delete-form-${propertyName}">
                Löschen
            </bx-modal-footer-button>
        </bx-modal-footer>
    </bx-modal>
</div>
</body>
</html>

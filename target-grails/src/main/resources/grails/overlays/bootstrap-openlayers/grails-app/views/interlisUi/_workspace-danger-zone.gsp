<section class="card ili-danger-zone ili-workspace-danger-zone" data-workspace-danger-zone>
    <div class="card-body">
        <header class="ili-danger-zone-head">
            <h2 class="ili-section-title"><g:message code="ili2grails.workspace.danger" default="Danger Zone"/></h2>
            <span class="badge text-bg-danger"><g:message code="ili2grails.workspace.destructive" default="Destruktiv"/></span>
        </header>
        <p class="mb-3">
            <g:message code="ili2grails.workspace.deleteDescription" default="Das Löschen wird serverseitig geprüft. Referenzielle Beziehungen oder andere Datenbank-Integritätsbedingungen können das Löschen verhindern."/>
        </p>
        <button type="button"
                class="btn btn-danger align-self-start"
                data-delete-open="${deleteModalId}"
                data-bs-toggle="modal"
                data-bs-target="#${deleteModalId}">
            <ili:icon name="trash" cssClass="me-1"/><g:message code="ili2grails.action.delete" default="Löschen"/>
        </button>
    </div>
</section>

<g:form controller="${controllerName}"
        action="delete"
        method="DELETE"
        name="${deleteFormId}"
        class="ili-hidden-delete-form">
    <input type="hidden" name="id" value="${instance?.id}"/>
    <button type="submit" class="ili-native-submit js-delete-submit"><g:message code="ili2grails.action.delete" default="Löschen"/></button>
</g:form>

<div class="modal fade" id="${deleteModalId}" tabindex="-1"
     role="dialog" aria-modal="true"
     aria-labelledby="${deleteModalId}-title" aria-describedby="${deleteModalId}-description"
     aria-hidden="true" data-delete-modal>
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h2 class="modal-title fs-5" id="${deleteModalId}-title"><g:message code="ili2grails.workspace.deleteConfirm" default="Objekt wirklich löschen?"/></h2>
                <button type="button" class="ili-modal-close ms-auto"
                        data-bs-dismiss="modal" aria-label="${message(code: 'ili2grails.workspace.close', default: 'Schliessen')}" title="${message(code: 'ili2grails.workspace.close', default: 'Schliessen')}">
                    <ili:icon name="x-circle"/>
                </button>
            </div>
            <div class="modal-body" id="${deleteModalId}-description">
                <p class="mb-0">
                    <g:message code="ili2grails.workspace.deleteConfirmDescription" default="Das Löschen wird serverseitig geprüft und kann wegen referenzieller Beziehungen oder anderer Datenbank-Integritätsbedingungen fehlschlagen."/>
                </p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal"><g:message code="ili2grails.action.cancel" default="Abbrechen"/></button>
                <button type="button" class="btn btn-danger"
                        data-delete-confirm="true"
                        data-delete-form="${deleteFormId}">
                    <g:message code="ili2grails.action.delete" default="Löschen"/>
                </button>
            </div>
        </div>
    </div>
</div>

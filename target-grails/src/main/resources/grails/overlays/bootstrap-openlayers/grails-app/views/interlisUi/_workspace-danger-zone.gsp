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
                <h2 class="modal-title fs-5" id="${deleteModalId}-title">
                    <g:message code="ili2grails.workspace.deleteConfirmNamed"
                               args="${[domainLabel ?: message(code: 'ili2grails.workspace.record', default: 'Objekt')]}"
                               default="{0} löschen?"/>
                </h2>
                <button type="button" class="ili-modal-close ms-auto"
                        data-bs-dismiss="modal" aria-label="${message(code: 'ili2grails.workspace.close', default: 'Schliessen')}" title="${message(code: 'ili2grails.workspace.close', default: 'Schliessen')}">
                    <ili:icon name="x-circle"/>
                </button>
            </div>
            <div class="modal-body" id="${deleteModalId}-description">
                <p class="mb-2">
                    <strong>${displayLabel ?: ('#' + instance?.id)}</strong>
                    <g:message code="ili2grails.workspace.deleteTargetSuffix" default="wird dauerhaft gelöscht."/>
                </p>
                <p class="mb-0">
                    <g:message code="ili2grails.workspace.deleteConfirmDescription" default="Das Löschen wird serverseitig geprüft und kann wegen referenzieller Beziehungen oder anderer Datenbank-Integritätsbedingungen fehlschlagen."/>
                </p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-outline-secondary"
                        data-delete-cancel="true"
                        data-bs-dismiss="modal"><g:message code="ili2grails.action.cancel" default="Abbrechen"/></button>
                <button type="button" class="btn btn-danger"
                        data-delete-confirm="true"
                        data-delete-form="${deleteFormId}">
                    <ili:icon name="trash" cssClass="me-1"/><g:message code="ili2grails.action.deletePermanently" default="Endgültig löschen"/>
                </button>
            </div>
        </div>
    </div>
</div>

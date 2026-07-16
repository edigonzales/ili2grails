<section class="card ili-danger-zone ili-workspace-danger-zone" data-workspace-danger-zone>
    <div class="card-body">
        <header class="ili-danger-zone-head">
            <h2 class="ili-section-title">Danger Zone</h2>
            <span class="badge text-bg-danger">Destruktiv</span>
        </header>
        <p class="mb-3">
            Das Löschen wird serverseitig geprüft. Referenzielle Beziehungen oder andere
            Datenbank-Integritätsbedingungen können das Löschen verhindern.
        </p>
        <button type="button"
                class="btn btn-danger align-self-start"
                data-delete-open="${deleteModalId}"
                data-bs-toggle="modal"
                data-bs-target="#${deleteModalId}">
            <ili:icon name="trash" cssClass="me-1"/>Löschen
        </button>
    </div>
</section>

<g:form controller="${controllerName}"
        action="delete"
        method="DELETE"
        name="${deleteFormId}"
        class="ili-hidden-delete-form">
    <input type="hidden" name="id" value="${instance?.id}"/>
    <button type="submit" class="ili-native-submit js-delete-submit">Löschen</button>
</g:form>

<div class="modal fade" id="${deleteModalId}" tabindex="-1"
     role="dialog" aria-modal="true"
     aria-labelledby="${deleteModalId}-title" aria-describedby="${deleteModalId}-description"
     aria-hidden="true" data-delete-modal>
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h2 class="modal-title fs-5" id="${deleteModalId}-title">Objekt wirklich löschen?</h2>
                <button type="button" class="btn btn-outline-secondary btn-sm ili-modal-close"
                        data-bs-dismiss="modal" aria-label="Schliessen" title="Schliessen">
                    <ili:icon name="x-lg"/>
                </button>
            </div>
            <div class="modal-body" id="${deleteModalId}-description">
                <p class="mb-0">
                    Das Löschen wird serverseitig geprüft und kann wegen referenzieller
                    Beziehungen oder anderer Datenbank-Integritätsbedingungen fehlschlagen.
                </p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Abbrechen</button>
                <button type="button" class="btn btn-danger"
                        data-delete-confirm="true"
                        data-delete-form="${deleteFormId}">
                    Löschen
                </button>
            </div>
        </div>
    </div>
</div>

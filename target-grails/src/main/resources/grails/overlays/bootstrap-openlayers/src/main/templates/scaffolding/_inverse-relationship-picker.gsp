<g:if test="\${section?.writable}">
    <g:form action="relationshipAssign"
            id="\${section.ownerId}"
            method="POST"
            class="ili-inverse-relationship-form"
            data-inverse-relationship-form="true">
        <g:hiddenField name="relationship" value="\${section.name}"/>
        <g:hiddenField name="confirmReassignment" value="false"/>

        <div class="ili-relationship-picker js-relationship-picker">
            <label class="form-label" for="inverse-search-\${section.domId}">
                <g:message code="ili2grails.inverse.select"
                           args="\${[section.relatedLabel]}"
                           default="\${section.relatedLabel + ' auswählen'}"/>
            </label>
            <input type="search"
                   id="inverse-search-\${section.domId}"
                   class="form-control form-control-sm js-relationship-search"
                   data-relationship-collection="\${section.name}"
                   data-relationship-url="\${createLink(action: 'relationshipCollectionOptions', id: section.ownerId)}"
                   data-relationship-select="inverse-target-\${section.domId}"
                   autocomplete="off"
                   role="combobox"
                   aria-autocomplete="list"
                   aria-haspopup="listbox"
                   aria-expanded="false"
                   aria-controls="inverse-target-\${section.domId}-results"/>
            <div id="inverse-target-\${section.domId}-results"
                 class="ili-relationship-results list-group mb-2"
                 data-relationship-list
                 role="listbox"
                 hidden></div>
            <select name="targetId"
                    id="inverse-target-\${section.domId}"
                    data-relationship-optional="true"
                    class="form-select">
                <option value=""><g:message code="ili2grails.js.noSelection" default="Keine Auswahl"/></option>
            </select>
        </div>

        <div class="ili-inverse-relationship-actions">
            <button type="submit"
                    class="btn btn-primary btn-sm"
                    data-inverse-assign-submit
                    disabled>
                <g:message code="ili2grails.inverse.assign"
                           args="\${[section.relatedLabel]}"
                           default="\${section.relatedLabel + ' zuweisen'}"/>
            </button>
            <div class="alert alert-danger ili-inverse-relationship-error"
                 data-inverse-relationship-error
                 role="alert"
                 hidden></div>
        </div>

        <div class="modal fade"
             id="inverse-reassign-\${section.domId}"
             tabindex="-1"
             aria-labelledby="inverse-reassign-title-\${section.domId}"
             aria-hidden="true"
             data-inverse-reassignment-modal
             data-title-template="\${message(code: 'ili2grails.inverse.reassignTitle', default: '{0} umteilen')}"
             data-confirm-template="\${message(code: 'ili2grails.inverse.reassignConfirm', default: '{0} ist aktuell {1} zugeordnet. Soll {0} {2} zugeordnet werden?')}">
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content">
                    <div class="modal-header">
                        <h2 class="modal-title fs-5" id="inverse-reassign-title-\${section.domId}"
                            data-inverse-reassignment-title>
                            <g:message code="ili2grails.inverse.reassign" default="Umteilen"/>
                        </h2>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"
                                aria-label="\${message(code: 'ili2grails.workspace.close', default: 'Schliessen')}"></button>
                    </div>
                    <div class="modal-body">
                        <p data-inverse-reassignment-text></p>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">
                            <g:message code="ili2grails.action.cancel" default="Abbrechen"/>
                        </button>
                        <button type="button" class="btn btn-primary" data-inverse-reassignment-confirm>
                            <g:message code="ili2grails.inverse.reassign" default="Umteilen"/>
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </g:form>
</g:if>

<g:if test="\${section?.createMode == 'QUICK' && section?.writable && section?.quickTargetRole}">
    <g:form action="associationCreate"
            id="\${raw(owner?.id)}"
            method="POST"
            class="ili-association-quick-form">
        <g:hiddenField name="context" value="\${raw(section.contextId)}"/>
        <g:hiddenField name="role" value="\${raw(section.quickTargetRole)}"/>

        <div class="ili-relationship-picker js-relationship-picker">
            <input type="search"
                   class="form-control form-control-sm js-relationship-search"
                   data-relationship-context="\${raw(section.contextId)}"
                   data-relationship-role="\${raw(section.quickTargetRole)}"
                   data-relationship-url="\${raw(createLink(action: 'associationOptions', id: owner?.id))}"
                   data-relationship-select="association-target-\${raw(section.domId)}"
                   autocomplete="off"
                   aria-controls="association-target-\${raw(section.domId)}-results"/>
            <div id="association-target-\${raw(section.domId)}-results"
                 class="ili-relationship-results list-group mb-2"
                 data-relationship-list
                 role="listbox"
                 hidden></div>
            <select name="targetId"
                    id="association-target-\${raw(section.domId)}"
                    data-relationship-optional="true"
                    class="form-select">
                <option value="">Ziel auswählen</option>
            </select>
        </div>

        <button type="submit" class="btn btn-primary btn-sm" disabled
                data-quick-add-submit>
            Zuordnen
        </button>
    </g:form>
</g:if>

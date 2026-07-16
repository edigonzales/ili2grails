<g:if test="\${section?.createMode == 'QUICK' && section?.writable && section?.quickTargetRole}">
    <g:form action="associationCreate"
            id="\${owner?.id}"
            method="POST"
            class="ili-association-quick-form">
        <g:hiddenField name="context" value="\${section.contextId}"/>
        <g:hiddenField name="role" value="\${section.quickTargetRole}"/>

        <div class="ili-relationship-picker js-relationship-picker">
            <input type="search"
                   class="form-control form-control-sm js-relationship-search"
                   data-relationship-context="\${section.contextId}"
                   data-relationship-role="\${section.quickTargetRole}"
                   data-relationship-url="\${createLink(action: 'associationOptions', id: owner?.id)}"
                   data-relationship-select="association-target-\${section.domId}"
                   autocomplete="off"
                   role="combobox"
                   aria-autocomplete="list"
                   aria-haspopup="listbox"
                   aria-expanded="false"
                   aria-label="Ziel für \${section.label ?: section.contextId}"
                   aria-controls="association-target-\${section.domId}-results"/>
            <div id="association-target-\${section.domId}-results"
                 class="ili-relationship-results list-group mb-2"
                 data-relationship-list
                 role="listbox"
                 hidden></div>
            <select name="targetId"
                    id="association-target-\${section.domId}"
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

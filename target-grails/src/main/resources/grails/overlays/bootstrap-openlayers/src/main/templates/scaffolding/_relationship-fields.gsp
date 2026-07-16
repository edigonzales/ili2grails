<g:set var="relationshipFieldsToRender" value="\${fields ?: relationshipFields ?: []}" />
<g:if test="\${relationshipFieldsToRender}">
    <div class="ili-relationship-fields">
        <g:each in="\${relationshipFieldsToRender}" var="relationshipField">
            <g:if test="\${hiddenRelationshipFields?.contains(relationshipField)}">
                <div id="field-\${relationshipField}" class="ili-field-row mb-3" data-form-field="\${relationshipField}">
                    <label class="form-label">
                        \${message(code: '${propertyName}.' + relationshipField + '.label', default: fieldMeta?.get(relationshipField)?.label ?: relationshipField)}
                    </label>
                    <g:hiddenField name="\${relationshipField}.id"
                                   value="\${relationshipValues?.get(relationshipField)}" />
                    <div class="ili-fixed-relationship-value form-control-plaintext">
                        \${fixedRelationshipLabels?.get(relationshipField) ?: ''}
                    </div>
                </div>
            </g:if>
            <g:else>
                <div id="field-\${relationshipField}" class="ili-field-row mb-3 \${relationshipRequired?.get(relationshipField) ? 'required' : ''}" data-form-field="\${relationshipField}">
                    <label class="form-label" for="relationship-\${relationshipField}">
                        \${message(code: '${propertyName}.' + relationshipField + '.label', default: fieldMeta?.get(relationshipField)?.label ?: relationshipField)}
                    </label>
                    <div class="ili-relationship-picker js-relationship-picker">
                        <input type="search"
                               class="form-control form-control-sm mb-2 js-relationship-search"
                               placeholder="Suchen"
                               autocomplete="off"
                               role="combobox"
                               aria-autocomplete="list"
                               aria-haspopup="listbox"
                               aria-expanded="false"
                               aria-label="\${message(code: '${propertyName}.' + relationshipField + '.label', default: fieldMeta?.get(relationshipField)?.label ?: relationshipField)}"
                               aria-controls="relationship-\${relationshipField}-results"
                               data-relationship-field="\${relationshipField}"
                               data-relationship-select="relationship-\${relationshipField}"
                               data-relationship-url="\${createLink(action: 'relationshipOptions')}" />
                        <div id="relationship-\${relationshipField}-results"
                             class="ili-relationship-results list-group mb-2"
                             data-relationship-list
                             role="listbox"
                             aria-label="Beziehungsauswahl"
                             aria-live="polite"
                             hidden></div>
                        <g:select name="\${relationshipField}.id"
                                  id="relationship-\${relationshipField}"
                                  from="\${relationshipOptions?.get(relationshipField) ?: []}"
                                  optionKey="id"
                                  optionValue="label"
                                  value="\${relationshipValues?.get(relationshipField)}"
                                  noSelection="\${relationshipRequired?.get(relationshipField) ? null : ['': 'Keine Auswahl']}"
                                  data-relationship-optional="\${relationshipRequired?.get(relationshipField) ? 'false' : 'true'}"
                                  aria-describedby="relationship-\${relationshipField}-help relationship-\${relationshipField}-error"
                                  aria-invalid="\${hasErrors(bean: this.${propertyName}, field: relationshipField) ? 'true' : 'false'}"
                                  class="form-select \${hasErrors(bean: this.${propertyName}, field: relationshipField, 'is-invalid')}" />
                    </div>
                    <div id="relationship-\${relationshipField}-help" class="form-text">Auswahl über Suche oder Liste.</div>
                    <g:hasErrors bean="\${this.${propertyName}}" field="\${relationshipField}">
                        <div id="relationship-\${relationshipField}-error" class="invalid-feedback d-block">
                            <g:eachError bean="\${this.${propertyName}}" field="\${relationshipField}" var="error">
                                <div><g:message error="\${error}" /></div>
                            </g:eachError>
                        </div>
                    </g:hasErrors>
                </div>
            </g:else>
        </g:each>
    </div>
</g:if>

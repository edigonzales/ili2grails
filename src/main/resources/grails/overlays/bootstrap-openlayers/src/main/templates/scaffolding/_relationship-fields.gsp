<g:if test="\${relationshipFields}">
    <div class="ili-relationship-fields">
        <g:each in="\${relationshipFields}" var="relationshipField">
            <div class="ili-field-row mb-3 \${relationshipRequired?.get(relationshipField) ? 'required' : ''}">
                <label class="form-label" for="relationship-\${relationshipField}">
                    \${message(code: '${propertyName}.' + relationshipField + '.label', default: relationshipField)}
                </label>
                <g:select name="\${relationshipField}.id"
                          id="relationship-\${relationshipField}"
                          from="\${relationshipOptions?.get(relationshipField) ?: []}"
                          optionKey="id"
                          optionValue="label"
                          value="\${relationshipValues?.get(relationshipField)}"
                          noSelection="\${relationshipRequired?.get(relationshipField) ? [:] : ['': 'Keine Auswahl']}"
                          class="form-select \${hasErrors(bean: this.${propertyName}, field: relationshipField, 'is-invalid')}" />
                <g:hasErrors bean="\${this.${propertyName}}" field="\${relationshipField}">
                    <div class="invalid-feedback d-block">
                        <g:eachError bean="\${this.${propertyName}}" field="\${relationshipField}" var="error">
                            <div><g:message error="\${error}" /></div>
                        </g:eachError>
                    </div>
                </g:hasErrors>
            </div>
        </g:each>
    </div>
</g:if>

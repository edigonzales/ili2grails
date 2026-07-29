<section class="ili-form-section" aria-labelledby="form-section-${section.title?.toString()?.replaceAll('[^A-Za-z0-9_-]', '-')}" data-form-section="${section.title}">
    <header class="ili-form-section-header">
        <h2 id="form-section-${section.title?.toString()?.replaceAll('[^A-Za-z0-9_-]', '-')}" class="ili-section-title h5 mb-0">
            ${section.title}
        </h2>
    </header>

    <div class="ili-form-section-fields">
        <g:each in="${section.fields ?: []}" var="fieldName">
            <g:if test="${relationshipFields?.contains(fieldName)}">
                <g:render template="relationship-fields" model="${[
                    fields: [fieldName],
                    relationshipFields: relationshipFields,
                    relationshipOptions: relationshipOptions,
                    relationshipValues: relationshipValues,
                    relationshipRequired: relationshipRequired,
                    hiddenRelationshipFields: hiddenRelationshipFields,
                    fixedRelationshipLabels: fixedRelationshipLabels,
                    fieldMeta: fieldMeta
                ]}"/>
            </g:if>
            <g:else>
                <g:set var="fieldInfo" value="${fieldMeta?.get(fieldName) ?: [:]}" />
                <g:set var="fieldHasErrors" value="${hasErrors(bean: this.company, field: fieldName)}" />
                <div id="field-${fieldName}"
                     class="ili-form-field ${hasErrors(bean: this.company, field: fieldName, 'has-error')}"
                     data-form-field="${fieldName}">
                    <f:field bean="company"
                             property="${fieldName}"
                             label="${message(code: 'company.' + fieldName + '.label', default: fieldInfo.label ?: fieldName)}"
                             class="ili-native-grid"
                             requiredClass="ili-field-row required"
                             labelClass="form-label"
                             divClass="ili-native-control"
                             widget-class="form-control"
                             widget-invalidClass="form-control is-invalid"
                             widget-aria-invalid="${fieldHasErrors ? 'true' : 'false'}"
                             widget-aria-describedby="${[
                                 (fieldInfo.documentation || fieldInfo.unit) ? 'field-' + fieldName + '-help' : null,
                                 fieldHasErrors ? 'field-' + fieldName + '-error' : null
                             ].findAll { it != null }.join(' ')}"
                             widget-selectDateClass="form-control"
                             widget-checkBoxClass="form-check-input" />
                    <g:if test="${fieldInfo.documentation || fieldInfo.unit}">
                        <div id="field-${fieldName}-help" class="form-text ili-field-meta">
                            <g:if test="${fieldInfo.unit}">
                                <span class="ili-unit-badge" title="${message(code: 'ili2grails.form.unit', default: 'Einheit')}">${fieldInfo.unit}</span>
                            </g:if>
                            <g:if test="${fieldInfo.documentation}">
                                <span class="ili-field-documentation">${fieldInfo.documentation}</span>
                            </g:if>
                        </div>
                    </g:if>
                    <g:hasErrors bean="${this.company}" field="${fieldName}">
                        <div id="field-${fieldName}-error" class="invalid-feedback d-block" data-field-error="${fieldName}">
                            <g:eachError bean="${this.company}" field="${fieldName}" var="error">
                                <div><g:message error="${error}" /></div>
                            </g:eachError>
                        </div>
                    </g:hasErrors>
                </div>
            </g:else>
        </g:each>
    </div>
</section>

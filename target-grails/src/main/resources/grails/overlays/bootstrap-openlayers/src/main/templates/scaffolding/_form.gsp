<div id="content" role="main" class="ili-page ili-page-form">
    <section class="ili-page-header">
        <div>
            <h1 class="ili-page-title"><g:message code="\${pageTitleCode}" args="\${[entityName]}" /></h1>
            <p class="ili-page-subtitle">\${pageSubtitle}</p>
        </div>
        <div class="ili-page-actions">
            <span class="badge text-bg-warning ili-unsaved-badge" hidden data-unsaved-badge>Unsaved changes</span>
            <g:link class="btn btn-outline-secondary" action="index" data-unsaved-nav="true">
                <g:message code="default.list.label" args="\${[entityName]}" />
            </g:link>
            <g:if test="\${mode == 'edit'}">
                <g:link class="btn btn-outline-primary" action="create" data-unsaved-nav="true">
                    <g:message code="default.new.label" args="\${[entityName]}" />
                </g:link>
            </g:if>
        </div>
    </section>

    <g:if test="\${flash.message}">
        <div class="alert alert-info" role="status">\${flash.message}</div>
    </g:if>

    <g:hasErrors bean="\${this.${propertyName}}">
        <div class="alert alert-danger" role="alert">Validierung fehlgeschlagen. Bitte korrigiere die markierten Werte.</div>
        <ul class="ili-error-list" role="alert">
            <g:eachError bean="\${this.${propertyName}}" var="error">
                <li><g:message error="\${error}"/></li>
            </g:eachError>
        </ul>
    </g:hasErrors>

    <g:form resource="\${this.${propertyName}}"
            controller="\${controllerName}"
            method="\${mode == 'edit' ? 'PUT' : 'POST'}"
            class="ili-form js-dirty-form">
        <g:if test="\${mode == 'edit'}">
            <g:hiddenField name="version" value="\${this.${propertyName}?.version}" />
        </g:if>

        <g:if test="\${associationContextState}">
            <g:hiddenField name="associationContext" value="\${raw(associationContextState.contextId)}" />
            <g:hiddenField name="associationOwnerId" value="\${raw(associationContextState.ownerId)}" />
        </g:if>

        <div class="ili-split-layout \${geometryFields ? 'ili-split-with-map' : 'ili-split-single'}">
            <section class="ili-form-column">
                <section class="card ili-form-tile">
                    <div class="card-body ili-native-form-host">
                        <fieldset class="form">
                            <g:render template="relationship-fields" model="\${[
                                relationshipFields: relationshipFields,
                                relationshipOptions: relationshipOptions,
                                relationshipValues: relationshipValues,
                                relationshipRequired: relationshipRequired
                            ]}"/>
                            <f:all bean="${propertyName}"
                                   except="\${((geometryFields ?: []) + (relationshipFields ?: [])).unique()}"
                                   class="ili-native-grid"
                                   requiredClass="ili-field-row required mb-3"
                                   labelClass="form-label"
                                   divClass="ili-native-control"
                                   widget-class="form-control"
                                   widget-invalidClass="form-control is-invalid"
                                   widget-selectDateClass="form-control"
                                   widget-checkBoxClass="form-check-input" />
                        </fieldset>
                        <g:if test="\${fieldMeta}">
                            <section class="ili-field-help-panel" aria-label="Feldhinweise">
                                <h2 class="ili-section-title h6 mb-2">Feldhinweise</h2>
                                <dl class="ili-field-help-list">
                                    <g:each in="\${fieldMeta}" var="fieldEntry">
                                        <g:if test="\${fieldEntry.value?.documentation || fieldEntry.value?.unit}">
                                            <div class="ili-field-help-item">
                                                <dt>
                                                    \${message(code: '${propertyName}.' + fieldEntry.key + '.label', default: fieldEntry.value?.label ?: fieldEntry.key)}
                                                    <g:if test="\${fieldEntry.value?.unit}">
                                                        <span class="ili-unit-badge">\${fieldEntry.value.unit}</span>
                                                    </g:if>
                                                </dt>
                                                <g:if test="\${fieldEntry.value?.documentation}">
                                                    <dd>\${fieldEntry.value.documentation}</dd>
                                                </g:if>
                                            </div>
                                        </g:if>
                                    </g:each>
                                </dl>
                            </section>
                        </g:if>
                    </div>
                </section>
            </section>

            <g:if test="\${geometryFields}">
                <aside class="ili-map-column">
                    <g:render template="geometry-panel" model="\${[
                        geometryFields: geometryFields,
                        geometryValues: geometryValues,
                        geometryKinds: geometryKinds,
                        geometrySrids: geometrySrids,
                        geometryMode: 'edit'
                    ]}"/>
                </aside>
            </g:if>
        </div>

        <footer class="ili-form-actions">
            <button type="button" class="btn btn-primary" data-form-submit="true">
                \${message(code: submitCode, default: submitDefault)}
            </button>
            <g:link class="btn btn-outline-secondary" action="index" data-unsaved-nav="true">Abbrechen</g:link>
            <button type="submit" class="ili-native-submit js-native-submit">Submit</button>
        </footer>
    </g:form>
</div>

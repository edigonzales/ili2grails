<div id="content" role="main" class="ili-page ili-page-form">
    <section class="ili-page-header">
        <div>
            <h1 class="ili-page-title"><g:message code="\${pageTitleCode}" args="\${[entityName]}" /></h1>
            <p class="ili-page-subtitle">\${pageSubtitle}</p>
        </div>
        <div class="ili-page-actions">
            <span class="badge text-bg-warning ili-unsaved-badge" hidden data-unsaved-badge role="status" aria-live="polite"><g:message code="ili2grails.form.unsaved" default="Ungespeicherte Änderungen"/></span>
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
        <div class="alert alert-danger ili-validation-summary" role="alert" tabindex="-1" data-validation-summary>
            <strong><g:message code="ili2grails.form.validationFailed" default="Validierung fehlgeschlagen."/></strong>
            <g:message code="ili2grails.form.validationInstruction" default="Bitte korrigiere die markierten Werte."/>
        </div>
        <ul class="ili-error-list ili-validation-summary-list" role="list">
            <g:eachError bean="\${this.${propertyName}}" var="error">
                <li>
                    <a href="#field-\${error.field}"><g:message error="\${error}"/></a>
                </li>
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
            <g:hiddenField name="associationContext" value="\${associationContextState.contextId}" />
            <g:hiddenField name="associationOwnerId" value="\${associationContextState.ownerId}" />
        </g:if>

        <div class="ili-split-layout \${geometryFields ? 'ili-split-with-map' : 'ili-split-single'}">
            <section class="ili-form-column">
                <section class="card ili-form-tile">
                    <div class="card-body ili-native-form-host">
                        <fieldset class="form">
                            <g:each in="\${formSections ?: [[title: message(code: 'ili2grails.form.general', default: 'Allgemein'), fields: []]]}" var="formSection">
                                <g:render template="form-section" model="\${[
                                    section: formSection,
                                    propertyName: '${propertyName}',
                                    relationshipFields: relationshipFields,
                                    relationshipOptions: relationshipOptions,
                                    relationshipValues: relationshipValues,
                                    relationshipRequired: relationshipRequired,
                                    hiddenRelationshipFields: hiddenRelationshipFields,
                                    fixedRelationshipLabels: fixedRelationshipLabels,
                                    fieldMeta: fieldMeta
                                ]}"/>
                            </g:each>
                        </fieldset>
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

        <footer class="ili-form-actions" data-sticky-form-actions>
            <button type="submit" class="btn btn-primary" name="submitMode" value="save" data-form-submit="true">
                \${message(code: submitCode, default: submitDefault)}
            </button>
            <button type="submit" class="btn btn-outline-primary" name="submitMode" value="saveAndContinue" data-form-submit="true">
                <g:message code="ili2grails.action.saveAndContinue" default="Speichern und weiter"/>
            </button>
            <g:link class="btn btn-outline-secondary" action="index" data-unsaved-nav="true"><g:message code="ili2grails.action.cancel" default="Abbrechen"/></g:link>
        </footer>
    </g:form>
</div>

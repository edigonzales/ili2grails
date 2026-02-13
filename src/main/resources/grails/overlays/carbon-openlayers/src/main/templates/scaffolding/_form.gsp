<div id="content" role="main" class="ili-page ili-page-form">
    <section class="ili-page-header">
        <div>
            <h1 class="ili-page-title"><g:message code="\${pageTitleCode}" args="[\${entityName}]" /></h1>
            <p class="ili-page-subtitle">\${pageSubtitle}</p>
        </div>
        <div class="ili-page-actions">
            <bx-tag class="ili-unsaved-badge" type="purple" hidden data-unsaved-badge>Unsaved changes</bx-tag>
            <g:link class="ili-link-btn" action="index" data-unsaved-nav="true">
                <bx-btn kind="tertiary"><g:message code="default.list.label" args="[\${entityName}]" /></bx-btn>
            </g:link>
            <g:if test="\${mode == 'edit'}">
                <g:link class="ili-link-btn" action="create" data-unsaved-nav="true">
                    <bx-btn kind="secondary"><g:message code="default.new.label" args="[\${entityName}]" /></bx-btn>
                </g:link>
            </g:if>
        </div>
    </section>

    <g:if test="\${flash.message}">
        <bx-inline-notification kind="info" title="Hinweis" subtitle="\${flash.message}"></bx-inline-notification>
    </g:if>

    <g:hasErrors bean="\${this.${propertyName}}">
        <bx-inline-notification kind="error" title="Validierung fehlgeschlagen" subtitle="Bitte korrigiere die markierten Werte."></bx-inline-notification>
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

        <div class="ili-split-layout \${geometryFields ? 'ili-split-with-map' : 'ili-split-single'}">
            <section class="ili-form-column">
                <section class="bx--tile ili-form-tile">
                    <div class="ili-native-form-host js-carbon-bridge">
                        <fieldset class="form">
                            <f:all bean="${propertyName}"
                                   except="\${geometryFields ?: []}"
                                   class="ili-native-grid"
                                   requiredClass="ili-field-row required"
                                   labelClass="ili-native-label"
                                   divClass="ili-native-control"
                                   widget-class="ili-native-widget"
                                   widget-invalidClass="ili-native-widget--invalid"
                                   widget-selectDateClass="ili-native-widget"
                                   widget-checkBoxClass="ili-native-checkbox" />
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

        <footer class="ili-form-actions">
            <bx-btn kind="primary" data-form-submit="true">
                \${message(code: submitCode, default: submitDefault)}
            </bx-btn>
            <g:link class="ili-link-btn" action="index" data-unsaved-nav="true">
                <bx-btn kind="ghost">Abbrechen</bx-btn>
            </g:link>
            <button type="submit" class="ili-native-submit js-native-submit">Submit</button>
        </footer>
    </g:form>
</div>

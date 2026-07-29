<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <g:set var="entityName" value="${message(code: 'employee.label', default: 'Employee')}" />
    <title><g:message code="default.show.label" args="${[entityName]}" /></title>
</head>
<body>
<div id="content" role="main" class="ili-page ili-page-show ili-domain-workspace" data-domain-workspace>
    <g:render template="/interlisUi/workspace-header" model="${[
        instance: this.employee,
        displayLabel: workspaceDisplayLabel,
        domainLabel: workspaceDomainLabel ?: entityName,
        controllerName: controllerName
    ]}"/>

    <g:if test="${flash.message}">
        <div class="alert alert-info" role="status">${flash.message}</div>
    </g:if>

    <div class="ili-workspace-main ili-split-layout ${geometryFields ? 'ili-split-with-map' : 'ili-split-single'}">
        <div class="ili-workspace-content ili-form-column">
            <g:render template="/interlisUi/workspace-details" model="${[
                detailSections: workspaceDetailSections,
                domainPropertyName: 'employee'
            ]}"/>

            <g:render template="/interlisUi/workspace-relationships" model="${[
                relationshipLinks: workspaceRelationshipLinks,
                domainPropertyName: 'employee'
            ]}"/>

            <g:if test="${associationSections}">
                <section class="ili-association-sections" aria-labelledby="association-sections-heading">
                    <h2 class="ili-section-title" id="association-sections-heading"><g:message code="ili2grails.association.assignments" default="Zuordnungen"/></h2>
                    <g:render template="association-sections" model="${[
                        associationSections: associationSections,
                        owner: this.employee
                    ]}"/>
                </section>
            </g:if>

            <g:if test="${associationDiagnostic}">
                <div class="alert alert-warning" role="alert">${associationDiagnostic}</div>
            </g:if>

            <g:render template="/interlisUi/workspace-danger-zone" model="${[
                instance: this.employee,
                controllerName: controllerName,
                deleteFormId: 'delete-form-employee',
                deleteModalId: 'delete-modal-employee'
            ]}"/>
        </div>

        <g:if test="${geometryFields}">
            <aside class="ili-map-column ili-workspace-geometry" data-workspace-geometry>
                <g:render template="geometry-panel" model="${[
                    geometryFields: geometryFields,
                    geometryValues: geometryValues,
                    geometryKinds: geometryKinds,
                    geometrySrids: geometrySrids,
                    geometryMode: 'view'
                ]}"/>
            </aside>
        </g:if>
    </div>
</div>
</body>
</html>

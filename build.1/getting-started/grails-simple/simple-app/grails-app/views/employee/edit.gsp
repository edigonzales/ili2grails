<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <g:set var="entityName" value="${message(code: 'employee.label', default: 'Employee')}" />
    <title><g:message code="default.edit.label" args="${[entityName]}" /></title>
</head>
<body>
<g:render template="form" model="${[
    mode: 'edit',
    entityName: entityName,
    pageTitleCode: 'default.edit.label',
    pageSubtitle: message(code: 'ili2grails.form.editSubtitle', default: 'Bestehende Entität aktualisieren.'),
    submitCode: 'default.button.update.label',
    submitDefault: 'Update',
    geometryFields: geometryFields,
    geometryValues: geometryValues,
    geometryKinds: geometryKinds,
    geometrySrids: geometrySrids,
    relationshipFields: relationshipFields,
    relationshipOptions: relationshipOptions,
    relationshipValues: relationshipValues,
    relationshipRequired: relationshipRequired,
    hiddenRelationshipFields: hiddenRelationshipFields,
    fixedRelationshipLabels: fixedRelationshipLabels,
    associationContextState: associationContextState,
    formSections: formSections,
    fieldMeta: fieldMeta
]}"/>
</body>
</html>

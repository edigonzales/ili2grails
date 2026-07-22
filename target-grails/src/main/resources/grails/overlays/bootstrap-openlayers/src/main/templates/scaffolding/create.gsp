<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <g:set var="entityName" value="\${message(code: '${propertyName}.label', default: '${className}')}" />
    <title><g:message code="default.create.label" args="\${[entityName]}" /></title>
</head>
<body>
<g:render template="form" model="\${[
    mode: 'create',
    entityName: entityName,
    pageTitleCode: 'default.create.label',
    pageSubtitle: \${message(code: 'ili2grails.form.createSubtitle', default: 'Neue Entität erfassen.')},
    submitCode: 'default.button.create.label',
    submitDefault: 'Create',
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

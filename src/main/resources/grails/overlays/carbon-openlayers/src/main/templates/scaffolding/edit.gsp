<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <g:set var="entityName" value="\${message(code: '${propertyName}.label', default: '${className}')}" />
    <title><g:message code="default.edit.label" args="[entityName]" /></title>
</head>
<body>
<g:render template="form" model="\${[
    mode: 'edit',
    entityName: entityName,
    pageTitleCode: 'default.edit.label',
    pageSubtitle: 'Bestehende Entität aktualisieren.',
    submitCode: 'default.button.update.label',
    submitDefault: 'Update'
]}"/>
</body>
</html>

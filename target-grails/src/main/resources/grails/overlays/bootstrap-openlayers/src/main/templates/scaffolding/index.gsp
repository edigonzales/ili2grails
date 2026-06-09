<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <g:set var="entityName" value="\${message(code: '${propertyName}.label', default: '${className}')}" />
    <g:set var="rows" value="\${${propertyName}List ?: []}" />
    <title><g:message code="default.list.label" args="[entityName]" /></title>
</head>
<body>
<div id="content" role="main" class="ili-page ili-page-list">
    <section class="ili-page-header">
        <div>
            <h1 class="ili-page-title"><g:message code="default.list.label" args="[entityName]" /></h1>
            <p class="ili-page-subtitle">Serverseitige Suche und Paging für große Datenbestände.</p>
        </div>
        <div class="ili-page-actions">
            <g:link action="create" class="btn btn-primary">
                <g:message code="default.new.label" args="[entityName]" />
            </g:link>
            <span class="badge text-bg-secondary">Count: \${${propertyName}Count ?: 0}</span>
        </div>
    </section>

    <g:if test="\${flash.message}">
        <div class="alert alert-info" role="status">\${flash.message}</div>
    </g:if>

    <section class="ili-list-tools">
        <g:form action="index" method="GET" class="row g-2 align-items-end">
            <div class="col-12 col-md-7 col-lg-6">
                <label class="form-label" for="${propertyName}-search">Suche</label>
                <input id="${propertyName}-search"
                       type="search"
                       name="q"
                       value="\${q ?: ''}"
                       class="form-control"
                       autocomplete="off" />
            </div>
            <div class="col-6 col-md-2 col-lg-2">
                <label class="form-label" for="${propertyName}-max">Pro Seite</label>
                <g:select id="${propertyName}-max"
                          name="max"
                          from="\${[10, 25, 50, 100]}"
                          value="\${max ?: 25}"
                          class="form-select" />
            </div>
            <div class="col-6 col-md-auto">
                <button type="submit" class="btn btn-outline-primary">Suchen</button>
            </div>
            <g:if test="\${q}">
                <div class="col-12 col-md-auto">
                    <g:link action="index" class="btn btn-outline-secondary">Zurücksetzen</g:link>
                </div>
            </g:if>
        </g:form>
    </section>

    <g:if test="\${rows.isEmpty()}">
        <section class="card ili-empty-state">
            <div class="card-body">
                <h2 class="h5 mb-2">\${q ? 'Keine Treffer' : 'Noch keine Daten'}</h2>
                <p class="mb-3">\${q ? 'Passe die Suche an.' : 'Erstelle den ersten Datensatz für ' + entityName + '.'}</p>
                <g:link action="create" class="btn btn-primary">
                    <g:message code="default.new.label" args="[entityName]" />
                </g:link>
            </div>
        </section>
    </g:if>

    <g:else>
        <section class="card ili-table-tile">
            <div class="ili-table-wrap">
                <table class="table table-hover align-middle mb-0">
                    <thead class="table-light">
                        <tr>
                            <g:each in="\${tableColumns ?: []}" var="tableColumn">
                                <th scope="col">\${message(code: '${propertyName}.' + tableColumn + '.label', default: tableColumn)}</th>
                            </g:each>
                            <th scope="col" class="text-end">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <g:each in="\${rows}" var="row">
                            <tr>
                                <g:each in="\${tableColumns ?: []}" var="tableColumn">
                                    <td>\${tableRows?.get(row?.id)?.get(tableColumn) ?: '-'}</td>
                                </g:each>
                                <td class="text-end">
                                    <div class="ili-row-actions">
                                        <g:link class="ili-icon-action" action="show" id="\${row?.id}" title="Anzeigen" aria-label="Anzeigen">
                                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="16" height="16" fill="currentColor" aria-hidden="true">
                                                <path d="M15.5,7.8C14.3,4.7,11.3,2.6,8,2.5C4.7,2.6,1.7,4.7,0.5,7.8c0,0.1,0,0.2,0,0.3c1.2,3.1,4.1,5.2,7.5,5.3c3.3-0.1,6.3-2.2,7.5-5.3C15.5,8.1,15.5,7.9,15.5,7.8z M8,12.5c-2.7,0-5.4-2-6.5-4.5c1-2.5,3.8-4.5,6.5-4.5s5.4,2,6.5,4.5C13.4,10.5,10.6,12.5,8,12.5z"/>
                                                <path d="M8,5C6.3,5,5,6.3,5,8s1.3,3,3,3s3-1.3,3-3S9.7,5,8,5z M8,10c-1.1,0-2-0.9-2-2s0.9-2,2-2s2,0.9,2,2S9.1,10,8,10z"/>
                                            </svg>
                                        </g:link>
                                        <g:link class="ili-icon-action" action="edit" id="\${row?.id}" title="Bearbeiten" aria-label="Bearbeiten">
                                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="16" height="16" fill="currentColor" aria-hidden="true">
                                                <path d="M2 26H30V28H2zM25.4 9c.8-.8.8-2 0-2.8 0 0 0 0 0 0l-3.6-3.6c-.8-.8-2-.8-2.8 0 0 0 0 0 0 0l-15 15V24h6.4L25.4 9zM20.4 4L24 7.6l-3 3L17.4 7 20.4 4zM6 22v-3.6l10-10 3.6 3.6-10 10H6z"/>
                                            </svg>
                                        </g:link>
                                        <g:form resource="\${row}" controller="\${controllerName}" method="DELETE" id="row-delete-\${row?.id}" class="ili-inline-delete-form">
                                            <button type="button"
                                                    class="ili-icon-action ili-icon-action-danger"
                                                    data-row-delete="true"
                                                    data-delete-form="row-delete-\${row?.id}"
                                                    title="Löschen"
                                                    aria-label="Löschen">
                                                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="16" height="16" fill="currentColor" aria-hidden="true">
                                                    <path d="M12 12H14V24H12zM18 12H20V24H18z"/>
                                                    <path d="M4 6V8H6V28a2 2 0 002 2H24a2 2 0 002-2V8h2V6zM8 28V8H24V28zM12 2H20V4H12z"/>
                                                </svg>
                                            </button>
                                            <button type="submit" class="ili-native-submit js-delete-submit">Delete</button>
                                        </g:form>
                                    </div>
                                </td>
                            </tr>
                        </g:each>
                    </tbody>
                </table>
            </div>
            <div class="ili-pagination-bar">
                <g:paginate total="\${${propertyName}Count ?: 0}"
                            max="\${max ?: 25}"
                            offset="\${offset ?: 0}"
                            params="\${[q: q, max: max ?: 25]}" />
            </div>
        </section>
    </g:else>
</div>
</body>
</html>

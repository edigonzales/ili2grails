<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <g:set var="entityName" value="\${message(code: '${propertyName}.label', default: '${className}')}" />
    <g:set var="rows" value="\${${propertyName}List ?: []}" />
    <title><g:message code="default.list.label" args="\${[entityName]}" /></title>
</head>
<body>
<div id="content" role="main" class="ili-page ili-page-list">
    <section class="ili-page-header">
        <div>
            <h1 class="ili-page-title"><g:message code="default.list.label" args="\${[entityName]}" /></h1>
            <p class="ili-page-subtitle">Serverseitige Suche und Paging für große Datenbestände.</p>
        </div>
        <div class="ili-page-actions">
            <g:link action="create" class="btn btn-primary">
                <g:message code="default.new.label" args="\${[entityName]}" />
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
            <g:if test="\${typedFilters}">
                <div class="col-12">
                    <details class="ili-filter-panel">
                        <summary>Typisierte Filter</summary>
                        <div class="ili-filter-grid">
                            <g:each in="\${typedFilters}" var="filterField">
                                <div class="ili-field-row">
                                    <label class="form-label" for="filter-\${filterField.name}">
                                        \${message(code: '${propertyName}.' + filterField.name + '.label', default: filterField.name)}
                                    </label>
                                    <g:if test="\${filterField.type == 'boolean'}">
                                        <g:select id="filter-\${filterField.name}"
                                                  name="\${'filter.' + filterField.name}"
                                                  from="\${[[id: '', label: 'Alle'], [id: 'true', label: 'Ja'], [id: 'false', label: 'Nein']]}"
                                                  optionKey="id"
                                                  optionValue="label"
                                                  value="\${activeFilters?.get(filterField.name) ?: ''}"
                                                  class="form-select" />
                                    </g:if>
                                    <g:else>
                                        <input id="filter-\${filterField.name}"
                                               type="\${filterField.type == 'number' ? 'number' : (filterField.type == 'date' ? 'date' : 'search')}"
                                               name="\${'filter.' + filterField.name}"
                                               value="\${activeFilters?.get(filterField.name) ?: ''}"
                                               class="form-control"
                                               autocomplete="off" />
                                    </g:else>
                                </div>
                            </g:each>
                        </div>
                    </details>
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
                    <g:message code="default.new.label" args="\${[entityName]}" />
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
                                <th scope="col">
                                    <g:sortableColumn property="\${tableColumn}"
                                                      title="\${message(code: '${propertyName}.' + tableColumn + '.label', default: tableColumn)}"
                                                      params="\${params.findAll { key, value -> !(key in ['offset', 'sort', 'order']) }}" />
                                </th>
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
                                            <ili:icon name="eye"/>
                                        </g:link>
                                        <g:link class="ili-icon-action" action="edit" id="\${row?.id}" title="Bearbeiten" aria-label="Bearbeiten">
                                            <ili:icon name="pencil"/>
                                        </g:link>
                                        <g:form resource="\${row}" controller="\${controllerName}" method="DELETE" id="row-delete-\${row?.id}" class="ili-inline-delete-form">
                                            <button type="button"
                                                    class="ili-icon-action ili-icon-action-danger"
                                                    data-row-delete="true"
                                                    data-delete-form="row-delete-\${row?.id}"
                                                    title="Löschen"
                                                    aria-label="Löschen">
                                                <ili:icon name="trash"/>
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
                            params="\${params.findAll { key, value -> key != 'offset' }}" />
            </div>
        </section>
    </g:else>
</div>
</body>
</html>

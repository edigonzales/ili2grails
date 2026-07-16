<section class="card ili-table-tile" aria-label="\${entityName} Tabelle">
    <div class="ili-table-wrap">
        <table class="table table-hover align-middle mb-0">
            <thead class="table-light">
                <tr>
                    <g:each in="\${tableColumns ?: []}" var="tableColumn">
                        <th scope="col">
                            <g:link action="index" params="\${sortUrls?.get(tableColumn)}" class="ili-sort-link">
                                <g:message code="${propertyName}.\${tableColumn}.label" default="\${tableColumn}" />
                                <g:if test="\${listQuery?.sort == tableColumn}"><span aria-hidden="true">\${listQuery?.order == 'desc' ? '↓' : '↑'}</span></g:if>
                            </g:link>
                        </th>
                    </g:each>
                    <th scope="col" class="text-end">Aktionen</th>
                </tr>
            </thead>
            <tbody>
                <g:each in="\${rows}" var="row">
                    <tr>
                        <g:each in="\${tableColumns ?: []}" var="tableColumn">
                            <td>
                                <g:if test="\${tableColumn == displayColumn}">
                                    <g:link action="show" id="\${row?.id}">\${tableRows?.get(row?.id)?.get(tableColumn) ?: '-'}</g:link>
                                </g:if>
                                <g:else>\${tableRows?.get(row?.id)?.get(tableColumn) ?: '-'}</g:else>
                            </td>
                        </g:each>
                        <td class="text-end">
                            <div class="ili-row-actions">
                                <g:link class="ili-icon-action" action="show" id="\${row?.id}" title="Anzeigen" aria-label="Anzeigen"><ili:icon name="eye"/></g:link>
                                <g:link class="ili-icon-action" action="edit" id="\${row?.id}" title="Bearbeiten" aria-label="Bearbeiten"><ili:icon name="pencil"/></g:link>
                                <g:form resource="\${row}" controller="\${controllerName}" method="DELETE" id="row-delete-\${row?.id}" class="ili-inline-delete-form">
                                    <button type="button" class="ili-icon-action ili-icon-action-danger" data-row-delete="true" data-delete-form="row-delete-\${row?.id}" title="Löschen" aria-label="Löschen"><ili:icon name="trash"/></button>
                                    <button type="submit" class="ili-native-submit js-delete-submit">Delete</button>
                                </g:form>
                            </div>
                        </td>
                    </tr>
                </g:each>
            </tbody>
        </table>
    </div>
</section>

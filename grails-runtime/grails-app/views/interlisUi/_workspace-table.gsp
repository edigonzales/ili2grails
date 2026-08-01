<g:set var="workspaceSection" value="${section instanceof Map ? section : [:]}"/>
<section class="card ili-workspace-table-section" data-workspace-table="${workspaceSection.key ?: ''}">
    <div class="card-body">
        <div class="ili-section-heading">
            <div>
                <p class="ili-eyebrow"><g:message code="ili2grails.workspace.area" default="Arbeitsbereich"/></p>
                <h2 class="h5 mb-0">${workspaceSection.title ?: message(code: 'ili2grails.workspace.entries', default: 'Einträge')}</h2>
            </div>
            <g:if test="${workspaceSection.count != null}">
                <span class="badge text-bg-light">${workspaceSection.count}</span>
            </g:if>
        </div>
        <g:if test="${workspaceSection.rows}">
            <div class="table-responsive">
                <table class="table align-middle ili-workspace-table">
                    <thead>
                    <tr>
                        <g:each in="${workspaceSection.columns ?: []}" var="column">
                            <th scope="col">${column.label ?: column.key}</th>
                        </g:each>
                    </tr>
                    </thead>
                    <tbody>
                    <g:each in="${workspaceSection.rows}" var="row">
                        <tr>
                            <g:each in="${workspaceSection.columns ?: []}" var="column">
                                <g:set var="cellValue" value="${row.values?.get(column.key)}"/>
                                <g:set var="cellLink" value="${row.links?.get(column.key)}"/>
                                <td>
                                    <g:if test="${cellLink?.controller && cellLink?.id != null}">
                                        <g:link controller="${cellLink.controller}"
                                                action="${cellLink.action ?: 'show'}"
                                                id="${cellLink.id}">${cellValue}</g:link>
                                    </g:if>
                                    <g:else>${cellValue}</g:else>
                                </td>
                            </g:each>
                        </tr>
                    </g:each>
                    </tbody>
                </table>
            </div>
        </g:if>
        <g:else>
            <g:render template="/interlisUi/workspace-empty" model="${[section: workspaceSection]}"/>
        </g:else>
    </div>
</section>

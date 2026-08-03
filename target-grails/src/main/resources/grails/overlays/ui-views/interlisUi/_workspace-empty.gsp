<g:set var="workspaceSection" value="${section instanceof Map ? section : [:]}"/>
<div class="ili-workspace-empty" data-workspace-empty>
    <h3 class="h6 mb-1">${title ?: workspaceSection.emptyTitle ?: message(code: 'ili2grails.workspace.noEntries', default: 'Keine Einträge')}</h3>
    <p class="mb-0 text-body-secondary">${message ?: workspaceSection.emptyMessage ?: message(code: 'ili2grails.workspace.noEntriesDescription', default: 'Für diesen Bereich sind keine Einträge vorhanden.')}</p>
</div>

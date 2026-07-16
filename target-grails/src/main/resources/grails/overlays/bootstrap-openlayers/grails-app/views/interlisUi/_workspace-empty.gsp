<g:set var="workspaceSection" value="${section instanceof Map ? section : [:]}"/>
<div class="ili-workspace-empty" data-workspace-empty>
    <h3 class="h6 mb-1">${title ?: workspaceSection.emptyTitle ?: 'Keine Einträge'}</h3>
    <p class="mb-0 text-body-secondary">${message ?: workspaceSection.emptyMessage ?: 'Für diesen Bereich sind keine Einträge vorhanden.'}</p>
</div>

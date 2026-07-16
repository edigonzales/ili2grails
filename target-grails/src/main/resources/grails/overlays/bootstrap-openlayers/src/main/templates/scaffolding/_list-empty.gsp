<section class="card ili-empty-state" data-list-empty-state>
    <div class="card-body">
        <g:if test="\${domainHasRecords && hasActiveListQuery}">
            <h2 class="h5 mb-2">Keine Treffer</h2>
            <p class="mb-3">Die aktuellen Such- und Filterkriterien liefern keine Datensätze.</p>
            <g:link action="index" class="btn btn-outline-secondary">Filter zurücksetzen</g:link>
        </g:if>
        <g:else>
            <h2 class="h5 mb-2">Noch keine Daten</h2>
            <p class="mb-3">Erstelle den ersten Datensatz für \${entityName}.</p>
            <g:link action="create" class="btn btn-primary"><g:message code="default.new.label" args="\${[entityName]}" /></g:link>
        </g:else>
    </div>
</section>

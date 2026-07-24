<section class="card ili-empty-state" data-list-empty-state>
    <div class="card-body">
        <g:if test="\${domainHasRecords && hasActiveListQuery}">
            <h2 class="h5 mb-2"><g:message code="ili2grails.list.noResults" default="Keine Treffer"/></h2>
            <p class="mb-3"><g:message code="ili2grails.list.noResultsDescription" default="Die aktuellen Such- und Filterkriterien liefern keine Datensätze."/></p>
            <g:link action="index" class="btn btn-outline-secondary"><g:message code="ili2grails.list.reset" default="Filter zurücksetzen"/></g:link>
        </g:if>
        <g:else>
            <h2 class="h5 mb-2"><g:message code="ili2grails.list.noData" default="Noch keine Daten"/></h2>
            <p class="mb-3"><g:message code="ili2grails.list.noDataDescription" args="\${[entityName]}" default="Erstelle den ersten Datensatz für {0}."/></p>
            <g:link action="create" class="btn btn-primary"><ili:icon name="plus-lg" cssClass="me-1"/><g:message code="ili2grails.action.new" default="Neu"/></g:link>
        </g:else>
    </div>
</section>

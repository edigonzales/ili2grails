<g:if test="\${pagination?.total > 0}">
    <nav class="ili-pagination-bar" aria-label="Seitennavigation">
        <ul class="pagination mb-0">
            <li class="page-item \${pagination.hasPrevious ? '' : 'disabled'}">
                <g:if test="\${pagination.hasPrevious}"><g:link class="page-link" action="index" params="\${pagination.previousParams}">Zurück</g:link></g:if>
                <g:else><span class="page-link">Zurück</span></g:else>
            </li>
            <g:each in="\${pagination.pages}" var="page">
                <li class="page-item \${page.current ? 'active' : ''}">
                    <g:link class="page-link" action="index" params="\${page.params}">\${page.number}</g:link>
                </li>
            </g:each>
            <li class="page-item \${pagination.hasNext ? '' : 'disabled'}">
                <g:if test="\${pagination.hasNext}"><g:link class="page-link" action="index" params="\${pagination.nextParams}">Weiter</g:link></g:if>
                <g:else><span class="page-link">Weiter</span></g:else>
            </li>
        </ul>
    </nav>
</g:if>

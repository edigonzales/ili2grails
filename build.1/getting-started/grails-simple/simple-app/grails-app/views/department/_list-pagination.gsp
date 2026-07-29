<g:if test="${pagination?.total > 0}">
    <nav class="ili-pagination-bar" aria-label="${message(code: 'ili2grails.pagination.navigation', default: 'Seitennavigation')}">
        <div class="ili-pagination-controls">
            <ul class="pagination mb-0">
                <li class="page-item ${pagination.hasPrevious ? '' : 'disabled'}">
                    <g:if test="${pagination.hasPrevious}"><g:link class="page-link" action="index" params="${pagination.previousParams}"><g:message code="ili2grails.pagination.previous" default="Zurück"/></g:link></g:if>
                    <g:else><span class="page-link"><g:message code="ili2grails.pagination.previous" default="Zurück"/></span></g:else>
                </li>
                <g:each in="${pagination.pages}" var="page">
                    <g:if test="${page.ellipsis}">
                        <li class="page-item disabled ili-pagination-ellipsis">
                            <span class="page-link" aria-label="${message(code: 'ili2grails.pagination.morePages', default: 'Weitere Seiten')}">…</span>
                        </li>
                    </g:if>
                    <g:else>
                        <li class="page-item ili-pagination-page-number ${page.current ? 'active' : ''}">
                            <g:link class="page-link" action="index" params="${page.params}">${page.number}</g:link>
                        </li>
                    </g:else>
                </g:each>
                <li class="page-item ${pagination.hasNext ? '' : 'disabled'}">
                    <g:if test="${pagination.hasNext}"><g:link class="page-link" action="index" params="${pagination.nextParams}"><g:message code="ili2grails.pagination.next" default="Weiter"/></g:link></g:if>
                    <g:else><span class="page-link"><g:message code="ili2grails.pagination.next" default="Weiter"/></span></g:else>
                </li>
            </ul>
        </div>
        <g:form action="index" method="GET" class="ili-pagination-page-size-form">
            <g:each in="${pagination.pageSizeParams ?: [:]}" var="parameter">
                <input type="hidden" name="${parameter.key}" value="${parameter.value}" />
            </g:each>
            <div class="ili-list-page-size">
                <label class="form-label" for="list-max"><g:message code="ili2grails.pagination.pageSize" default="Zeilen pro Seite"/></label>
                <g:select id="list-max" name="max" from="${[10, 25, 50, 100]}"
                          value="${pagination.max}" class="form-select" data-ili-page-size-select="true" />
            </div>
        </g:form>
        <p class="ili-list-result-summary ili-pagination-summary" data-list-result-summary>
            <g:if test="${pagination?.showResultRange}">
                <g:message code="ili2grails.pagination.resultRange"
                           args="${[pagination.resultStart, pagination.resultEnd, pagination.total]}"
                           default="{0}–{1} von {2} Treffer"/>
            </g:if>
            <g:else>
                <g:message code="ili2grails.pagination.resultCount"
                           args="${[pagination?.total ?: 0]}"
                           default="{0} Treffer"/>
            </g:else>
        </p>
    </nav>
</g:if>

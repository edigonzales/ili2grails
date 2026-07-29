<g:if test="${query}">
    <section class="card ili-explorer-search-results" aria-labelledby="ili-search-results-title">
        <div class="card-body">
            <h2 id="ili-search-results-title" class="h5">Suchergebnisse für „${query}“</h2>
            <g:if test="${searchResults}">
                <ul class="ili-domain-list ili-explorer-result-list">
                    <g:each in="${searchResults}" var="domain">
                        <g:render template="/interlisUi/domain-link"
                                  model="${[
                                      domain: domain,
                                      showFavorite: true,
                                      itemClass: 'ili-domain-list-item'
                                  ]}"/>
                    </g:each>
                </ul>
            </g:if>
            <g:else>
                <p class="mb-0 text-body-secondary">Keine passende Domainklasse gefunden.</p>
            </g:else>
        </div>
    </section>
</g:if>

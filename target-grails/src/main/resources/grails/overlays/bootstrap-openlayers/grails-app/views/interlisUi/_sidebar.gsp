<aside id="iliSidebar" class="ili-sidebar offcanvas-lg offcanvas-start" tabindex="-1"
       aria-label="Domain-Navigation" data-ili-sidebar>
    <div class="offcanvas-header ili-sidebar-header">
        <h2 class="offcanvas-title h5 mb-0">Navigation</h2>
        <button type="button" class="btn btn-outline-secondary btn-sm ili-sidebar-close"
                data-bs-dismiss="offcanvas" data-bs-target="#iliSidebar"
                data-ili-sidebar-close
                aria-label="Navigation schliessen">
            <ili:icon name="x-lg"/>
        </button>
    </div>
    <div class="offcanvas-body ili-sidebar-body">
        <section class="ili-local-navigation-section" data-ili-local-section="favorites" hidden>
            <h2 class="ili-navigation-section-title">
                <ili:icon name="star" cssClass="me-1"/>Favoriten
            </h2>
            <ul class="ili-domain-list" data-ili-favorites-list></ul>
        </section>
        <section class="ili-local-navigation-section" data-ili-local-section="recents" hidden>
            <h2 class="ili-navigation-section-title">
                <ili:icon name="clock-history" cssClass="me-1"/>Zuletzt verwendet
            </h2>
            <ul class="ili-domain-list" data-ili-recents-list></ul>
        </section>

        <nav aria-label="Modelle und Topics" class="ili-navigation-groups">
            <g:render template="/interlisUi/navigation-groups"
                      model="${[
                          groups: navigationModel?.models,
                          singleModel: navigationModel?.singleModel,
                          showFavorite: true
                      ]}"/>
        </nav>

        <g:if test="${navigationModel?.fallback}">
            <section class="ili-navigation-fallback">
                <h2 class="ili-navigation-section-title">Weitere Seiten</h2>
                <ul class="ili-domain-list">
                    <g:each in="${navigationModel.fallback}" var="domain">
                        <g:render template="/interlisUi/domain-link"
                                  model="${[
                                      domain: domain,
                                      showFavorite: false,
                                      itemClass: 'ili-domain-list-item'
                                  ]}"/>
                    </g:each>
                </ul>
            </section>
        </g:if>
    </div>
</aside>

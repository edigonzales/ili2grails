<aside id="iliSidebar" class="ili-sidebar offcanvas-lg offcanvas-start" tabindex="-1"
       aria-label="${message(code: 'ili2grails.shell.domainNavigation', default: 'Domain-Navigation')}" data-ili-sidebar>
    <div class="offcanvas-header ili-sidebar-header">
        <button type="button" class="btn btn-outline-secondary btn-sm ili-sidebar-close"
                data-bs-dismiss="offcanvas" data-bs-target="#iliSidebar"
                data-ili-sidebar-close
                aria-label="${message(code: 'ili2grails.shell.navigation.close', default: 'Navigation schliessen')}">
            <ili:icon name="x-circle"/>
        </button>
    </div>
    <div class="offcanvas-body ili-sidebar-body">
        <section class="ili-local-navigation-section" data-ili-local-section="favorites" hidden>
            <h2 class="ili-navigation-section-title">
                <ili:icon name="star" cssClass="me-1"/><g:message code="ili2grails.explorer.favorites" default="Favoriten"/>
            </h2>
            <ul class="ili-domain-list" data-ili-favorites-list></ul>
        </section>
        <section class="ili-local-navigation-section" data-ili-local-section="recents" hidden>
            <h2 class="ili-navigation-section-title">
                <ili:icon name="clock-history" cssClass="me-1"/><g:message code="ili2grails.explorer.recent" default="Zuletzt verwendet"/>
            </h2>
            <ul class="ili-domain-list" data-ili-recents-list></ul>
        </section>

        <nav aria-label="${message(code: 'ili2grails.explorer.modelsTopics', default: 'Modelle und Topics')}" class="ili-navigation-groups">
            <g:render template="/interlisUi/navigation-groups"
                      model="${[
                          groups: navigationModel?.models,
                          singleModel: navigationModel?.singleModel,
                          showFavorite: true
                      ]}"/>
        </nav>

        <g:if test="${navigationModel?.workspaces}">
            <section class="ili-navigation-workspaces" data-ili-navigation-group="workspaces">
                <h2 class="ili-navigation-section-title"><g:message code="ili2grails.explorer.workspaces" default="Fachliche Arbeitsseiten"/></h2>
                <ul class="ili-domain-list">
                    <g:each in="${navigationModel.workspaces}" var="workspace">
                        <g:render template="/interlisUi/workspace-link"
                                  model="${[workspace: workspace, itemClass: 'ili-domain-list-item']}"/>
                    </g:each>
                </ul>
            </section>
        </g:if>

        <g:if test="${navigationModel?.fallback}">
            <section class="ili-navigation-fallback">
                <h2 class="ili-navigation-section-title"><g:message code="ili2grails.explorer.otherPages" default="Weitere Seiten"/></h2>
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

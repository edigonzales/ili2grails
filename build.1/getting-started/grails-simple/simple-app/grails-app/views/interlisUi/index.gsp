<!DOCTYPE html>
<html lang="de-CH">
<head>
    <meta name="layout" content="main"/>
    <title><g:message code="ili2grails.explorer.title" default="Domain Explorer"/></title>
</head>
<body>
<div id="content" role="main" class="ili-page ili-page-explorer" data-ili-explorer>
    <section class="ili-page-header">
        <div>
            <p class="ili-eyebrow">${appTitle ?: 'INTERLIS CRUD'}</p>
            <h1 class="ili-page-title">Domain Explorer</h1>
            <p class="ili-page-subtitle">Modelle, Topics und Domainklassen serverseitig durchsuchen.</p>
        </div>
    </section>

    <g:render template="/interlisUi/explorer-results"
              model="${[query: query, searchResults: searchResults]}"/>

    <section class="ili-explorer-groups" aria-labelledby="ili-explorer-groups-title">
        <div class="ili-section-heading">
            <div>
                <p class="ili-eyebrow">Verfügbare Fachdaten</p>
                <h2 id="ili-explorer-groups-title" class="h4 mb-0">Modelle und Topics</h2>
            </div>
        </div>
        <g:render template="/interlisUi/navigation-groups"
                  model="${[
                      groups: navigationModel?.models,
                      singleModel: navigationModel?.singleModel,
                      showFavorite: true
                  ]}"/>
        <g:if test="${navigationModel?.workspaces}">
            <section class="ili-navigation-workspaces" data-ili-navigation-group="workspaces">
                <h3 class="h6">Fachliche Arbeitsseiten</h3>
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
                <h3 class="h6">Weitere Seiten</h3>
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
    </section>

    <div class="row g-3 ili-explorer-local-sections">
        <div class="col-12 col-lg-6">
            <section class="card ili-local-navigation-section" data-ili-local-section="favorites" hidden>
                <div class="card-body">
                    <h2 class="h6"><ili:icon name="star" cssClass="me-1"/>Favoriten</h2>
                    <ul class="ili-domain-list" data-ili-favorites-list></ul>
                </div>
            </section>
        </div>
        <div class="col-12 col-lg-6">
            <section class="card ili-local-navigation-section" data-ili-local-section="recents" hidden>
                <div class="card-body">
                    <h2 class="h6"><ili:icon name="clock-history" cssClass="me-1"/>Zuletzt verwendet</h2>
                    <ul class="ili-domain-list" data-ili-recents-list></ul>
                </div>
            </section>
        </div>
    </div>
</div>
</body>
</html>

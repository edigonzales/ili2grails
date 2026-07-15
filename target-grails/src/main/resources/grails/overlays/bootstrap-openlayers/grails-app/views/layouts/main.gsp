<!doctype html>
<html lang="de-CH">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title><g:layoutTitle default="INTERLIS CRUD"/></title>
    <asset:link rel="icon" href="favicon.ico" type="image/x-ico"/>

    <asset:stylesheet src="application.css"/>
    <asset:stylesheet src="ili-modern.css"/>
    <g:layoutHead/>
</head>

<body>
<g:set var="navigationModel"
       value="${ch.interlis.generator.grails.runtime.InterlisNavigationSupport.navigationModel(grailsApplication)}"/>
<g:set var="shellAppTitle"
       value="${ch.interlis.generator.grails.runtime.InterlisUiDescriptorSupport.appTitle(grailsApplication)}"/>
<g:set var="explorerUrl" value="${createLink(controller: 'interlisUi', action: 'index')}"/>
<g:set var="currentNavigationEntry"
       value="${navigationModel?.domains?.find { it.controller == params.controller }}"/>

<header class="navbar bg-body border-bottom sticky-top ili-topbar" data-ili-topbar>
    <div class="container-fluid gap-2">
        <button class="btn btn-outline-secondary d-lg-none ili-sidebar-toggle"
                type="button"
                data-bs-toggle="offcanvas"
                data-bs-target="#iliSidebar"
                data-ili-sidebar-toggle
                aria-controls="iliSidebar"
                aria-label="Navigation einblenden"
                title="Navigation einblenden">
            <ili:icon name="list"/>
        </button>

        <a class="navbar-brand d-inline-flex align-items-center gap-2 fw-semibold"
           href="${explorerUrl}" data-ili-home-link>
            <ili:icon name="grid"/>
            <span>${shellAppTitle}</span>
        </a>

        <form class="ili-domain-finder flex-grow-1" method="GET"
              action="${createLink(controller: 'interlisUi', action: 'domains')}"
              role="search" data-ili-domain-finder-form>
            <label class="visually-hidden" for="ili-domain-finder-input">Domain suchen</label>
            <div class="input-group">
                <span class="input-group-text bg-body" aria-hidden="true">
                    <ili:icon name="search"/>
                </span>
                <input id="ili-domain-finder-input"
                       class="form-control"
                       type="search"
                       name="q"
                       value="${params.q ?: ''}"
                       placeholder="Domain suchen …"
                       autocomplete="off"
                       aria-controls="ili-domain-finder-results"
                       aria-expanded="false"
                       data-ili-domain-finder-input/>
                <button class="btn btn-outline-secondary" type="submit"
                        aria-label="Domain-Suche ausführen" title="Domain-Suche ausführen">
                    <ili:icon name="search"/>
                </button>
            </div>
            <div id="ili-domain-finder-results"
                 class="ili-domain-finder-results list-group"
                 role="listbox"
                 aria-label="Domain-Suchergebnisse"
                 data-ili-finder-results
                 hidden></div>
        </form>

        <div class="ili-user-slot" data-ili-extension-point="user-slot"
             aria-label="Benutzerbereich"></div>
    </div>
</header>

<div class="ili-shell-layout">
    <g:render template="/interlisUi/sidebar"
              model="${[navigationModel: navigationModel]}"/>

    <main id="main-content" class="ili-main-content flex-grow-1" role="main">
        <div class="container-fluid ili-main-grid">
            <nav class="ili-breadcrumbs" aria-label="Brotkrümel">
                <a href="${explorerUrl}">
                    <ili:icon name="house" cssClass="me-1"/>Explorer
                </a>
                <g:if test="${currentNavigationEntry}">
                    <ili:icon name="chevron-right" cssClass="ili-breadcrumb-separator"/>
                    <span aria-current="page">${currentNavigationEntry.label}</span>
                </g:if>
            </nav>
            <g:layoutBody/>
        </div>
    </main>
</div>

<asset:javascript src="application.js"/>
</body>
</html>

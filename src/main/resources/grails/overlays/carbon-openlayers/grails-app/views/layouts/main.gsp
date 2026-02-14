<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title><g:layoutTitle default="INTERLIS CRUD GENERATOR"/></title>
    <asset:link rel="icon" href="favicon.ico" type="image/x-ico"/>

    <link rel="preconnect" href="https://fonts.googleapis.com"/>
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin/>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600&display=swap"/>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/carbon-components@10/css/carbon-components.min.css"/>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/ol@v9.2.4/ol.css"/>

    <asset:stylesheet src="application.css"/>
    <asset:stylesheet src="ili-modern.css"/>
    <g:layoutHead/>
</head>

<body class="bx--body">
<a class="bx--skip-to-content" href="#main-content">Skip to content</a>
<g:set var="viewMenuEntries"
       value="${grailsApplication.controllerClasses
           .findAll { it.logicalPropertyName && it.logicalPropertyName != 'urlMappings' }
           .collect { [controller: it.logicalPropertyName, label: it.shortName?.replace('Controller', '')] }
           .sort { it.label?.toLowerCase() }}"/>

<bx-header aria-label="INTERLIS CRUD GENERATOR" class="ili-shell-header">
    <bx-header-menu-button
        button-label-active="Navigation schliessen"
        button-label-inactive="Navigation öffnen"
        collapse-mode="responsive"
        usage-mode="header-nav">
    </bx-header-menu-button>
    <bx-header-name href="${request.contextPath}/">INTERLIS CRUD GENERATOR</bx-header-name>
</bx-header>

<bx-side-nav aria-label="Seitennavigation" collapse-mode="responsive" usage-mode="header-nav">
    <bx-side-nav-items>
        <g:if test="${viewMenuEntries}">
            <g:each in="${viewMenuEntries}" var="entry">
                <bx-side-nav-link href="${createLink(controller: entry.controller, action: 'index')}">${entry.label}</bx-side-nav-link>
            </g:each>
        </g:if>
        <g:else>
            <bx-side-nav-link href="${request.contextPath}/">Home</bx-side-nav-link>
        </g:else>
    </bx-side-nav-items>
</bx-side-nav>

<main id="main-content" class="ili-main-content" role="main">
    <div class="ili-main-grid">
        <g:layoutBody/>
    </div>
</main>

<script src="https://cdn.jsdelivr.net/npm/proj4@2.11.0/dist/proj4.js"></script>
<script src="https://cdn.jsdelivr.net/npm/ol@v9.2.4/dist/ol.js"></script>
<asset:javascript src="ili-carbon-wc-bundle.js"/>
<asset:javascript src="application.js"/>
</body>
</html>

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
<g:set var="viewMenuEntries"
       value="${grailsApplication.controllerClasses
           .findAll { it.logicalPropertyName && it.logicalPropertyName != 'urlMappings' }
           .collect { [controller: it.logicalPropertyName, namespace: it.namespace, label: it.shortName?.replace('Controller', '')] }
           .sort { it.label?.toLowerCase() }}"/>

<nav class="navbar navbar-expand-lg bg-white border-bottom fixed-top" aria-label="Hauptnavigation">
    <div class="container-fluid">
        <a class="navbar-brand fw-semibold" href="${request.contextPath}/">INTERLIS CRUD</a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#iliMainNavbar" aria-controls="iliMainNavbar" aria-expanded="false" aria-label="Navigation ein-/ausblenden">
            <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="iliMainNavbar">
            <ul class="navbar-nav ms-auto mb-2 mb-lg-0">
                <g:if test="${viewMenuEntries}">
                    <g:each in="${viewMenuEntries}" var="entry">
                        <li class="nav-item">
                            <a class="nav-link" href="${createLink(controller: entry.controller, namespace: entry.namespace, action: 'index')}">${entry.label}</a>
                        </li>
                    </g:each>
                </g:if>
                <g:else>
                    <li class="nav-item"><a class="nav-link" href="${request.contextPath}/">Home</a></li>
                </g:else>
            </ul>
        </div>
    </div>
</nav>

<main id="main-content" class="ili-main-content" role="main">
    <div class="container-fluid ili-main-grid">
        <g:layoutBody/>
    </div>
</main>

<asset:javascript src="application.js"/>
</body>
</html>

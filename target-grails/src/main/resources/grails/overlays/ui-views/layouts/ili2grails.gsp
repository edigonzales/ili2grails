<!doctype html>
<html lang="${grailsApplication.config.ili2grails?.language ?: 'de-CH'}" data-ili-neutral-palette="balanced">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title><g:layoutTitle default="INTERLIS CRUD"/></title>
    <asset:link rel="icon" href="favicon.ico" type="image/x-ico"/>

    <asset:stylesheet src="application.css"/>
    <asset:stylesheet src="ili-modern.css"/>
    <g:layoutHead/>
</head>

<body data-ili-message-delete-record="${message(code: 'ili2grails.js.deleteRecord', default: 'Datensatz wirklich löschen?')}"
      data-ili-message-delete-association="${message(code: 'ili2grails.js.deleteAssociation', default: 'Zuordnung wirklich entfernen?')}"
      data-ili-message-unsaved="${message(code: 'ili2grails.js.unsaved', default: 'Es gibt ungespeicherte Änderungen. Seite wirklich verlassen?')}"
      data-ili-message-no-selection="${message(code: 'ili2grails.js.noSelection', default: 'Keine Auswahl')}"
      data-ili-message-favorite-mark="${message(code: 'ili2grails.workspace.favoriteMark', default: 'Als Favorit markieren')}"
      data-ili-message-favorite-remove="${message(code: 'ili2grails.workspace.favoriteRemove', default: 'Favorit entfernen')}"
      data-ili-message-no-domain="${message(code: 'ili2grails.js.noDomain', default: 'Keine Domain gefunden')}">
<g:set var="navigationModel"
       value="${ch.interlis.generator.grails.runtime.InterlisNavigationSupport.navigationModel(grailsApplication)}"/>
<g:set var="shellAppTitle"
       value="${ch.interlis.generator.grails.runtime.InterlisUiDescriptorSupport.appTitle(grailsApplication)}"/>
<g:set var="shellAppLogo"
       value="${ch.interlis.generator.grails.runtime.InterlisUiDescriptorSupport.appLogo(grailsApplication)}"/>
<g:set var="shellAppLogoIcon"
       value="${ch.interlis.generator.grails.runtime.InterlisUiDescriptorSupport.appLogoIcon(grailsApplication)}"/>
<g:set var="explorerUrl" value="${createLink(controller: 'interlisUi', action: 'index')}"/>
<g:set var="currentNavigationEntry"
       value="${navigationModel?.allEntries?.find { it.controller == params.controller }}"/>
<g:set var="breadcrumbAction" value="${params.action ?: 'index'}"/>
<g:set var="breadcrumbRecordLabel"
       value="${workspaceDisplayLabel ?: (params.id ? '#' + params.id : currentNavigationEntry?.label)}"/>

<header class="navbar bg-body border-bottom sticky-top ili-topbar" data-ili-topbar>
    <div class="container-fluid gap-2">
        <button class="btn btn-outline-secondary d-lg-none ili-sidebar-toggle"
                type="button"
                data-bs-toggle="offcanvas"
                data-bs-target="#iliSidebar"
                data-ili-sidebar-toggle
                aria-controls="iliSidebar"
                aria-label="${message(code: 'ili2grails.shell.navigation.show', default: 'Navigation einblenden')}"
                title="${message(code: 'ili2grails.shell.navigation.show', default: 'Navigation einblenden')}">
            <ili:icon name="list"/>
        </button>

        <a class="navbar-brand d-inline-flex align-items-center gap-2 fw-semibold"
           href="${explorerUrl}" data-ili-home-link>
            <g:if test="${shellAppLogo}">
                <img src="${asset.assetPath(src: shellAppLogo)}" alt="${shellAppTitle}" class="ili-app-logo"/>
            </g:if>
            <g:elseif test="${shellAppLogoIcon}">
                <ili:icon name="${shellAppLogoIcon}"/>
            </g:elseif>
            <span class="ili-app-title">${shellAppTitle}</span>
        </a>

        <form class="ili-domain-finder" method="GET"
              action="${createLink(controller: 'interlisUi', action: 'domains')}"
              role="search" data-ili-domain-finder-form>
            <label class="visually-hidden" for="ili-domain-finder-input"><g:message code="ili2grails.shell.domainSearch" default="Domain suchen …"/></label>
            <div class="ili-domain-search-row">
                <div class="input-group ili-search-input-group">
                    <span class="input-group-text ili-search-icon" aria-hidden="true">
                        <ili:icon name="search"/>
                    </span>
                    <input id="ili-domain-finder-input"
                           class="form-control ili-search-input"
                           type="search"
                           name="q"
                           value="${params.q ?: ''}"
                           placeholder="${message(code: 'ili2grails.shell.domainSearch', default: 'Domain suchen …')}"
                           autocomplete="off"
                           role="combobox"
                           aria-autocomplete="list"
                           aria-haspopup="listbox"
                           aria-controls="ili-domain-finder-results"
                           aria-expanded="false"
                           data-ili-domain-finder-input/>
                </div>
                <button class="btn btn-primary" type="submit"
                        aria-label="${message(code: 'ili2grails.shell.domainSearchSubmit', default: 'Domain-Suche ausführen')}"
                        title="${message(code: 'ili2grails.shell.domainSearchSubmit', default: 'Domain-Suche ausführen')}">
                    <g:message code="ili2grails.list.searchSubmit" default="Suchen"/>
                </button>
            </div>
            <div id="ili-domain-finder-results"
                 class="ili-domain-finder-results list-group"
                 role="listbox"
                 aria-label="${message(code: 'ili2grails.shell.domainResults', default: 'Domain-Suchergebnisse')}"
                 data-ili-finder-results
                 hidden></div>
        </form>

        <div class="ili-topbar-right" data-ili-extension-point="topbar-toolbar"
             aria-label="${message(code: 'ili2grails.shell.helpAndUser', default: 'Hilfe und Benutzer')}">
            <button type="button" class="ili-topbar-help-btn"
                    aria-label="${message(code: 'ili2grails.shell.help', default: 'Hilfe')}"
                    title="${message(code: 'ili2grails.shell.help', default: 'Hilfe')}">
                <ili:icon name="help"/>
            </button>
            <div class="vr d-none d-md-block opacity-25"></div>
            <div class="dropdown">
                <button class="ili-topbar-user-btn d-flex align-items-center gap-2 dropdown-toggle"
                        type="button" data-bs-toggle="dropdown" aria-expanded="false">
                    <ili:icon name="person-circle"/>
                    <span class="d-none d-md-inline">Max Muster</span>
                </button>
                <ul class="dropdown-menu dropdown-menu-end">
                    <li><span class="dropdown-item-text text-muted">Max Muster</span></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item" href="#"><g:message code="ili2grails.shell.signOut" default="Abmelden"/></a></li>
                </ul>
            </div>
        </div>
    </div>
</header>

<g:set var="notification" value="${flash.notification ?: (flash.message ? [type: 'info', message: flash.message] : null)}"/>
<g:if test="${notification}">
    <g:set var="notificationType" value="${['success', 'info', 'warning', 'danger'].contains(notification?.type?.toString()) ? notification.type.toString() : 'info'}"/>
    <g:set var="notificationTitle" value="${notification?.title ?: message(code: 'ili2grails.notification.' + notificationType, default: notificationType == 'danger' ? 'Aktion nicht möglich' : notificationType == 'warning' ? 'Achtung' : notificationType == 'success' ? 'Erfolgreich' : 'Hinweis')}"/>
    <g:set var="notificationIcon" value="${notification?.icon ?: (notificationType == 'danger' ? 'x-circle' : notificationType == 'warning' ? 'exclamation-triangle' : notificationType == 'success' ? 'check-circle' : 'info-circle')}"/>
    <div class="ili-notification-region"
         data-ili-notifications
         aria-label="${message(code: 'ili2grails.notification.region', default: 'Benachrichtigungen')}">
        <div class="alert alert-${notificationType} ili-notification"
             data-ili-notification
             data-notification-level="${notificationType}"
             role="${notificationType == 'danger' || notificationType == 'warning' ? 'alert' : 'status'}"
             aria-live="${notificationType == 'danger' || notificationType == 'warning' ? 'assertive' : 'polite'}"
             aria-atomic="true">
            <div class="ili-notification-icon" aria-hidden="true"><ili:icon name="${notificationIcon}"/></div>
            <div class="ili-notification-content">
                <strong class="ili-notification-title">${notificationTitle}</strong>
                <div class="ili-notification-message">${notification?.message ?: notification?.text ?: notification}</div>
                <g:if test="${notification?.detail}">
                    <details class="ili-notification-details">
                        <summary><g:message code="ili2grails.notification.showDetails" default="Details anzeigen"/></summary>
                        <p>${notification.detail}</p>
                    </details>
                </g:if>
                <g:if test="${notification?.actionUrl && notification?.actionLabel}">
                    <a class="ili-notification-action" href="${notification.actionUrl}">${notification.actionLabel}</a>
                </g:if>
            </div>
            <button type="button"
                    class="btn-close ili-notification-dismiss"
                    data-notification-dismiss
                    aria-label="${message(code: 'ili2grails.notification.close', default: 'Meldung schliessen')}"
                    title="${message(code: 'ili2grails.notification.close', default: 'Meldung schliessen')}"></button>
        </div>
    </div>
</g:if>

<div class="ili-shell-layout">
    <g:render template="/interlisUi/sidebar"
              model="${[navigationModel: navigationModel]}"/>

    <main id="main-content" class="ili-main-content flex-grow-1" role="main">
        <div class="container-fluid ili-main-grid">
            <nav class="ili-breadcrumbs" aria-label="${message(code: 'ili2grails.shell.breadcrumbs', default: 'Brotkrümel')}">
                <a href="${explorerUrl}">
                    <ili:icon name="house" cssClass="me-1"/><g:message code="ili2grails.shell.explorer" default="Explorer"/>
                </a>
                <g:if test="${currentNavigationEntry}">
                    <g:if test="${breadcrumbAction == 'create'}">
                        <ili:icon name="chevron-right" cssClass="ili-breadcrumb-separator"/>
                        <g:link controller="${currentNavigationEntry.controller}" action="index">${currentNavigationEntry.label}</g:link>
                        <ili:icon name="chevron-right" cssClass="ili-breadcrumb-separator"/>
                        <span aria-current="page"><g:message code="ili2grails.action.create" default="Erfassen"/></span>
                    </g:if>
                    <g:elseif test="${breadcrumbAction == 'show'}">
                        <ili:icon name="chevron-right" cssClass="ili-breadcrumb-separator"/>
                        <g:link controller="${currentNavigationEntry.controller}" action="index">${currentNavigationEntry.label}</g:link>
                        <ili:icon name="chevron-right" cssClass="ili-breadcrumb-separator"/>
                        <span aria-current="page">${breadcrumbRecordLabel}</span>
                    </g:elseif>
                    <g:elseif test="${breadcrumbAction == 'edit'}">
                        <ili:icon name="chevron-right" cssClass="ili-breadcrumb-separator"/>
                        <g:link controller="${currentNavigationEntry.controller}" action="index">${currentNavigationEntry.label}</g:link>
                        <ili:icon name="chevron-right" cssClass="ili-breadcrumb-separator"/>
                        <g:link controller="${currentNavigationEntry.controller}" action="show" id="${params.id}">${breadcrumbRecordLabel}</g:link>
                        <ili:icon name="chevron-right" cssClass="ili-breadcrumb-separator"/>
                        <span aria-current="page"><g:message code="ili2grails.action.edit" default="Bearbeiten"/></span>
                    </g:elseif>
                    <g:elseif test="${breadcrumbAction == 'index'}">
                        <ili:icon name="chevron-right" cssClass="ili-breadcrumb-separator"/>
                        <span aria-current="page">${currentNavigationEntry.label}</span>
                    </g:elseif>
                    <g:else>
                        <ili:icon name="chevron-right" cssClass="ili-breadcrumb-separator"/>
                        <span aria-current="page">${currentNavigationEntry.label}</span>
                    </g:else>
                </g:if>
            </nav>
            <g:layoutBody/>
        </div>
    </main>
</div>

<asset:javascript src="application.js"/>
</body>
</html>

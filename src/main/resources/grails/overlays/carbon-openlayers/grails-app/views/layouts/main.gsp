<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title><g:layoutTitle default="INTERLIS CRUD"/></title>
    <asset:link rel="icon" href="favicon.ico" type="image/x-ico"/>

    <link rel="stylesheet" href="https://unpkg.com/carbon-components@10/css/carbon-components.min.css"/>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/ol@v9.2.4/ol.css"/>

    <asset:stylesheet src="application.css"/>
    <asset:stylesheet src="ili-modern.css"/>
    <g:layoutHead/>
</head>

<body class="bx--body">
<header class="bx--header" role="banner" aria-label="INTERLIS CRUD">
    <a class="bx--skip-to-content" href="#main-content">Skip to content</a>
    <a class="bx--header__name" href="${request.contextPath}/" title="Home">
        <span class="bx--header__name--prefix">INTERLIS</span>&nbsp;CRUD
    </a>
</header>

<main id="main-content" class="bx--content ili-main-content" role="main">
    <div class="bx--grid">
        <div class="bx--row">
            <div class="bx--col-lg-16">
                <g:layoutBody/>
            </div>
        </div>
    </div>
</main>

<footer class="ili-footer" role="contentinfo">
    <div class="bx--grid">
        <div class="bx--row">
            <div class="bx--col-lg-16">
                <small>Generated with ili2grails</small>
            </div>
        </div>
    </div>
</footer>

<script src="https://cdn.jsdelivr.net/npm/proj4@2.11.0/dist/proj4.js"></script>
<script src="https://cdn.jsdelivr.net/npm/ol@v9.2.4/dist/ol.js"></script>
<asset:javascript src="application.js"/>
</body>
</html>

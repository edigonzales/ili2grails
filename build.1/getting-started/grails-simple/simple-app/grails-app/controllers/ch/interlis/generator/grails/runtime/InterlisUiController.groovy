package ch.interlis.generator.grails.runtime

/**
 * Server-rendered entry point for the Bootstrap application shell.
 *
 * This controller only exposes navigation metadata. It deliberately does not
 * query domain data or depend on authentication state.
 */
class InterlisUiController {

    static allowedMethods = [index: "GET", domains: "GET"]

    def index() {
        renderExplorer(params.q)
    }

    def domains() {
        renderExplorer(params.q)
    }

    private void renderExplorer(Object rawQuery) {
        applySecurityHeaders()
        Map<String, Object> navigationModel =
            InterlisNavigationSupport.navigationModel(grailsApplication)
        String query = normalizedQuery(rawQuery)
        render view: "index", model: [
            appTitle      : InterlisUiDescriptorSupport.appTitle(grailsApplication),
            navigationModel: navigationModel,
            searchResults : InterlisNavigationSupport.searchDomains(navigationModel, query),
            query         : query
        ]
    }

    private String normalizedQuery(Object value) {
        String query = value?.toString()?.trim()
        return query == null || query.isBlank() ? null : query
    }

    private void applySecurityHeaders() {
        response.setHeader("Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
                "connect-src 'self'; object-src 'none'; frame-ancestors 'none'; " +
                "base-uri 'self'; form-action 'self'")
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
    }
}

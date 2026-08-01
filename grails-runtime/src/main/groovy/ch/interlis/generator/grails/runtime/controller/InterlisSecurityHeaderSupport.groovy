package ch.interlis.generator.grails.runtime.controller

/**
 * Centralized security header application for generated controllers.
 */
final class InterlisSecurityHeaderSupport {

    static final String CONTENT_SECURITY_POLICY =
        "default-src 'self'; script-src 'self'; style-src 'self'; " +
        "img-src 'self' data:; connect-src 'self'; object-src 'none'; " +
        "frame-ancestors 'none'; base-uri 'self'; form-action 'self'"

    private InterlisSecurityHeaderSupport() {
    }

    static void apply(Object controller, Object response) {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY)
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
    }
}

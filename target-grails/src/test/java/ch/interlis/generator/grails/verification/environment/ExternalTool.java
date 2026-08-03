package ch.interlis.generator.grails.verification.environment;

/**
 * Externes Werkzeug, das ein erweiterter Verification-Test benötigen kann
 * (Spezifikation §10.1).
 */
public enum ExternalTool {
    JAVA,
    GRAILS,
    DOCKER,
    DOCKER_COMPOSE,
    ILI2PG,
    PLAYWRIGHT_CHROMIUM,
    POSTGRESQL
}

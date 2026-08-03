package ch.interlis.generator.grails.verification.environment;

import java.nio.file.Path;

/**
 * Optionen für die Umgebungsdetektion (Spezifikation §10.1).
 *
 * @param ili2pgHome konfigurierter ili2pg-Home-Pfad (aus -Pili2pgHome oder ILI2PG_HOME)
 * @param jdbcUrl    JDBC-URL, wird für Reports redigiert
 * @param inspectBrowser ob die Browser-Verfügbarkeit (Playwright) geprüft werden soll
 */
public record VerificationEnvironmentOptions(
    Path ili2pgHome,
    String jdbcUrl,
    boolean inspectBrowser
) {

    public static VerificationEnvironmentOptions defaults() {
        return new VerificationEnvironmentOptions(null, null, false);
    }
}

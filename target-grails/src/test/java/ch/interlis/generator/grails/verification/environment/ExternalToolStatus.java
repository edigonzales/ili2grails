package ch.interlis.generator.grails.verification.environment;

/**
 * Status eines externen Werkzeugs (Spezifikation §10.1).
 *
 * <p>{@code resolvedPath} darf in veröffentlichten Reports nur enthalten sein,
 * wenn keine Credentials oder privaten Verzeichnisinformationen offengelegt
 * werden. Die Detektor-Ausgabe für Reports redigiert den Pfad entsprechend.
 */
public record ExternalToolStatus(
    ExternalTool tool,
    ToolAvailability availability,
    String version,
    String resolvedPath,
    String diagnostic
) {

    public boolean available() {
        return availability == ToolAvailability.AVAILABLE;
    }
}

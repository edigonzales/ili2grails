package ch.interlis.generator.grails.verification.environment;

import java.nio.file.Path;
import java.util.Map;

/**
 * Redigierte Beschreibung der Verifikationsumgebung (Spezifikation §10.1).
 * Enthält keine Passwörter, keine kompletten JDBC-URLs mit Credentials und
 * keine Home-Verzeichnisse.
 */
public record VerificationEnvironment(
    String javaVersion,
    String osName,
    String osArchitecture,
    String gitCommit,
    String jdbcUrlRedacted,
    Map<ExternalTool, ExternalToolStatus> tools
) {

    public VerificationEnvironment {
        tools = Map.copyOf(tools);
    }

    public ExternalToolStatus tool(ExternalTool tool) {
        return tools.getOrDefault(tool, new ExternalToolStatus(
            tool, ToolAvailability.NOT_CHECKED, null, null, "not checked"));
    }
}

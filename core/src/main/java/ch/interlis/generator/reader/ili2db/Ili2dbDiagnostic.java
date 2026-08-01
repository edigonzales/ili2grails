package ch.interlis.generator.reader.ili2db;

import java.util.Map;

/**
 * Strukturierte Reader-Diagnostik. Logs sind Zusatzinformation; fachliche
 * Fehler werden nie nur geloggt und vergessen.
 */
public record Ili2dbDiagnostic(
    Ili2dbSeverity severity,
    Ili2dbDiagnosticCode code,
    String message,
    String iliElement,
    String physicalElement,
    Map<String, String> details
) {

    public Ili2dbDiagnostic {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public boolean isBlocking() {
        return severity == Ili2dbSeverity.FATAL || severity == Ili2dbSeverity.ERROR;
    }
}

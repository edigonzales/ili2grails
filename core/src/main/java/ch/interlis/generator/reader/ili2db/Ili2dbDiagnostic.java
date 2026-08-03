package ch.interlis.generator.reader.ili2db;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

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
        TreeMap<String, String> sorted = new TreeMap<>();
        if (details != null) {
            sorted.putAll(details);
        }
        details = Collections.unmodifiableMap(sorted);
    }

    public boolean isBlocking() {
        return severity == Ili2dbSeverity.FATAL || severity == Ili2dbSeverity.ERROR;
    }
}

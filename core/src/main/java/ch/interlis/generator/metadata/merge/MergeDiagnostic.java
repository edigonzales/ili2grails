package ch.interlis.generator.metadata.merge;

import java.util.Map;
import java.util.Objects;

/**
 * Strukturiertes Merge-Diagnostic.
 *
 * <p>{@code semanticElement} und {@code physicalElement} enthalten qualifizierte Namen.
 * {@code details} hat stabile maschinenlesbare Schlüssel. Tests dürfen nicht auf
 * Meldungstexte parsen.</p>
 */
public record MergeDiagnostic(
    MergeSeverity severity,
    MergeDiagnosticCode code,
    String message,
    String semanticElement,
    String physicalElement,
    Map<String, String> details
) {

    public MergeDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public boolean isBlocking() {
        return severity == MergeSeverity.ERROR
            || severity == MergeSeverity.FATAL;
    }
}

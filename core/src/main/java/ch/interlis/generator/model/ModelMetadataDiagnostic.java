package ch.interlis.generator.model;

/**
 * Diagnostik der Modell-Validierung vor dem Freeze.
 */
public record ModelMetadataDiagnostic(
    ModelMetadataDiagnosticCode code,
    String subject,
    String message,
    boolean blocking,
    java.util.Map<String, String> details
) {

    public ModelMetadataDiagnostic {
        details = details == null ? java.util.Map.of() : java.util.Map.copyOf(details);
    }
}

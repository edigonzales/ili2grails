package ch.interlis.generator.model;

/**
 * Wird beim Freeze geworfen, wenn blockierende Validierungs-Diagnostics
 * vorliegen.
 */
public final class ModelMetadataValidationException extends RuntimeException {

    private final java.util.List<ModelMetadataDiagnostic> diagnostics;

    public ModelMetadataValidationException(String message,
                                            java.util.List<ModelMetadataDiagnostic> diagnostics) {
        super(message);
        this.diagnostics = diagnostics == null
            ? java.util.List.of()
            : java.util.List.copyOf(diagnostics);
    }

    public java.util.List<ModelMetadataDiagnostic> getDiagnostics() {
        return diagnostics;
    }
}

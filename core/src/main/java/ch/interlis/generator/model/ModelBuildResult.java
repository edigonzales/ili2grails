package ch.interlis.generator.model;

import java.util.List;

/**
 * Ergebnis eines Build-Laufs mit Diagnostics.
 */
public record ModelBuildResult(
    ModelMetadata metadata,
    List<ModelMetadataDiagnostic> diagnostics
) {

    public ModelBuildResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean hasBlockingDiagnostics() {
        return diagnostics.stream().anyMatch(ModelMetadataDiagnostic::blocking);
    }
}

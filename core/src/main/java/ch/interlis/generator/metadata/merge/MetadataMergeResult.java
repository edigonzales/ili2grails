package ch.interlis.generator.metadata.merge;

import ch.interlis.generator.model.ModelMetadata;

import java.util.List;
import java.util.Objects;

/**
 * Resultat eines Metadaten-Merges: gemergtes Modell plus strukturierte Diagnostics.
 */
public record MetadataMergeResult(
    ModelMetadata metadata,
    List<MergeDiagnostic> diagnostics
) {

    public MetadataMergeResult {
        Objects.requireNonNull(metadata, "metadata");
        diagnostics = diagnostics == null
            ? List.of()
            : List.copyOf(diagnostics);
    }

    public boolean hasBlockingDiagnostics() {
        return diagnostics.stream()
            .anyMatch(MergeDiagnostic::isBlocking);
    }

    public List<MergeDiagnostic> blockingDiagnostics() {
        return diagnostics.stream()
            .filter(MergeDiagnostic::isBlocking)
            .toList();
    }

    public void throwIfBlocking() {
        if (hasBlockingDiagnostics()) {
            throw new MetadataMergeException(blockingDiagnostics());
        }
    }
}

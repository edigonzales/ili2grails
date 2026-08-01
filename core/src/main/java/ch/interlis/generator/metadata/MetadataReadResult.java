package ch.interlis.generator.metadata;

import ch.interlis.generator.metadata.merge.MergeDiagnostic;
import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.model.ModelMetadata;

import java.util.List;

/**
 * Vollständiges Lese-Resultat des {@link MetadataReader}: gemergtes Metamodell,
 * Merge-Diagnostics und die verwendete Modellauswahl.
 */
public record MetadataReadResult(
    ModelMetadata metadata,
    List<MergeDiagnostic> diagnostics,
    ModelSelection modelSelection
) {
}

package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.metadata.selection.ModelSelection;

import java.util.Objects;

/**
 * Typisierte Lese-Anfrage an den ili2db-Reader.
 */
public record Ili2dbReadRequest(
    ModelSelection modelSelection,
    String schemaName,
    Ili2dbFailurePolicy failurePolicy,
    boolean includeEnumValues,
    boolean includeGeometryMetadata
) {

    public Ili2dbReadRequest {
        Objects.requireNonNull(modelSelection, "modelSelection");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
    }

    public static Ili2dbReadRequest strict(ModelSelection selection, String schemaName) {
        return new Ili2dbReadRequest(selection, schemaName, Ili2dbFailurePolicy.STRICT, true, true);
    }
}

package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.metadata.selection.ModelSelection;

import java.util.Objects;

/**
 * Typisierte fachliche Lese-Anfrage an den ili2db-Reader.
 *
 * <p>Enthält nur die fachliche Anfrage: Modellauswahl, Fehlerpolitik und
 * die Flags für Enum- und Geometrie-Metadaten. Die technische Umgebung
 * (Verbindung, Schema, Dialekt) liegt im {@link Ili2dbReadContext}
 * (Spezifikation §13).</p>
 */
public record Ili2dbReadRequest(
    ModelSelection modelSelection,
    Ili2dbFailurePolicy failurePolicy,
    boolean includeEnumValues,
    boolean includeGeometryMetadata
) {

    public Ili2dbReadRequest {
        Objects.requireNonNull(modelSelection, "modelSelection");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
    }

    /**
     * Strikte Anfrage: FATAL und ERROR blockieren.
     */
    public static Ili2dbReadRequest strict(ModelSelection selection) {
        return new Ili2dbReadRequest(selection, Ili2dbFailurePolicy.STRICT, true, true);
    }

    /**
     * Diagnose-Anfrage: FATAL blockiert weiterhin, wenn kein sinnvolles
     * Modell gebaut werden kann; ERROR darf ein partielles, klar markiertes
     * Resultat liefern; WARNING und INFO blockieren nicht.
     */
    public static Ili2dbReadRequest diagnostic(ModelSelection selection) {
        return new Ili2dbReadRequest(selection, Ili2dbFailurePolicy.DIAGNOSTIC, true, true);
    }
}

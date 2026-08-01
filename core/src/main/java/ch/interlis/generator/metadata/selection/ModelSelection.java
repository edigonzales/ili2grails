package ch.interlis.generator.metadata.selection;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Präzise Auswahl der Modelle, die aus einer ili2db-Schema gelesen werden.
 *
 * <p>Enthalten ist immer das Root-Modell sowie – bei vorhandenem
 * ili2c-Abhängigkeitsgraphen – die transitiven echten Imports. Unabhängige
 * Modelle desselben Schemas sind ausgeschlossen.</p>
 */
public record ModelSelection(
    String rootModelName,
    Set<String> includedModelNames,
    ModelSelectionSource source
) {

    public ModelSelection {
        Objects.requireNonNull(rootModelName, "rootModelName");
        Objects.requireNonNull(includedModelNames, "includedModelNames");
        Objects.requireNonNull(source, "source");
        includedModelNames = Collections.unmodifiableSet(
            new LinkedHashSet<>(includedModelNames)
        );
        if (!includedModelNames.contains(rootModelName)) {
            throw new IllegalArgumentException(
                "includedModelNames must contain root model");
        }
    }

    /**
     * Fallback-Auswahl ohne Abhängigkeitsgraph: nur das Root-Modell.
     */
    public static ModelSelection rootOnly(String rootModelName) {
        Objects.requireNonNull(rootModelName, "rootModelName");
        return new ModelSelection(
            rootModelName,
            new LinkedHashSet<>(Set.of(rootModelName)),
            ModelSelectionSource.ROOT_ONLY_FALLBACK
        );
    }

    public boolean includes(String modelName) {
        return modelName != null && includedModelNames.contains(modelName);
    }
}

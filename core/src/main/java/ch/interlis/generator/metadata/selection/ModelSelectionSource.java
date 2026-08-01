package ch.interlis.generator.metadata.selection;

/**
 * Quelle einer {@link ModelSelection}.
 */
public enum ModelSelectionSource {
    /**
     * Auswahl basiert auf dem echten ili2c-Abhängigkeitsgraphen
     * (Root plus transitive Imports).
     */
    ILI2C_DEPENDENCY_GRAPH,

    /**
     * Fallback ohne ili2c-Abhängigkeitsgraph: nur das Root-Modell wird gelesen.
     */
    ROOT_ONLY_FALLBACK
}

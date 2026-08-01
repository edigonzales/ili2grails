package ch.interlis.generator.model;

/**
 * Build-Policy des Freeze-Gates.
 */
public enum ModelBuildPolicy {
    /** Blockierende Diagnostics verhindern das Freeze. */
    VALIDATE_BLOCKING,
    /** Diagnostics werden geliefert, das Freeze findet trotzdem statt. */
    ALLOW_WITH_DIAGNOSTICS
}

package ch.interlis.generator.metadata.merge;

/**
 * Schweregrad eines Merge-Diagnostics.
 */
public enum MergeSeverity {
    /**
     * Erwartete Abweichung ohne Einschränkung.
     */
    INFO,

    /**
     * Ergebnis bleibt nutzbar, aber nicht vollständig angereichert.
     */
    WARNING,

    /**
     * Standardgenerierung wird blockiert.
     */
    ERROR,

    /**
     * Merge kann nicht sinnvoll abgeschlossen werden.
     */
    FATAL
}

package ch.interlis.generator.metadata.merge;

/**
 * Merge-Policy.
 *
 * <ul>
 *   <li>{@link #STRICT}: Blockierende Diagnostics führen nach vollständiger
 *       Auswertung zu einer {@link MetadataMergeException}.</li>
 *   <li>{@link #DIAGNOSTIC}: Das Resultat bleibt inspizierbar; der Caller muss
 *       die Diagnostics selbst auswerten.</li>
 * </ul>
 *
 * <p>Es gibt bewusst keine LENIENT-First-Match-Policy.</p>
 */
public enum MetadataMergePolicy {
    STRICT,
    DIAGNOSTIC
}

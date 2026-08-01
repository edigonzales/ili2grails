package ch.interlis.generator.metadata.merge;

/**
 * Grund für eine Match-Entscheidung zwischen physischen und semantischen Elementen.
 *
 * <p>Die Reihenfolge der Enum-Konstanten entspricht der Stärke des Matches:
 * exakte Namen sind stärker als normalisierte Token.</p>
 */
public enum MatchReason {
    EXACT_QUALIFIED_NAME,
    EXACT_NAME,
    EXACT_SOURCE_ATTRIBUTE,
    EXACT_TARGET_ROLE,
    EXACT_PHYSICAL_NAME,
    EXACT_COLUMN_NAME,
    NORMALIZED_FULL_TOKEN,
    NORMALIZED_ID_SUFFIX,
    NO_MATCH
}

package ch.interlis.generator.report;

/**
 * Single association-role row in merge diagnostics.
 */
public record AssociationRoleMergeReportEntry(
    String association,
    String associationClass,
    String physicalTable,
    String role,
    String target,
    String oppositeRole,
    String physicalName,
    String semanticName,
    String sourceAttribute,
    String targetAttribute,
    String source,
    String mergeReason,
    String mergeConfidence,
    String mergeToken,
    String cardinality,
    boolean mandatory,
    boolean ordered,
    boolean external,
    boolean composition
) {
}

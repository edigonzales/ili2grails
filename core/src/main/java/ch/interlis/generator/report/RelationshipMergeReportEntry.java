package ch.interlis.generator.report;

/**
 * Single relationship row in a merge diagnostics report.
 */
public record RelationshipMergeReportEntry(
    String name,
    String sourceClass,
    String targetClass,
    String semanticKind,
    String type,
    String source,
    String physicalName,
    String semanticName,
    String sourceAttribute,
    String targetAttribute,
    String associationName,
    String sourceRoleName,
    String targetRoleName,
    String oppositeRoleName,
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

package ch.interlis.generator.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Summary and categorized rows for relationship merge diagnostics.
 */
public record RelationshipMergeReport(
    String modelName,
    long totalRelationships,
    Map<String, Long> byMergeReason,
    Map<String, Long> byMergeConfidence,
    long totalAssociationRoles,
    Map<String, Long> associationRolesByMergeReason,
    Map<String, Long> associationRolesByMergeConfidence,
    List<RelationshipMergeReportEntry> exactMatches,
    List<RelationshipMergeReportEntry> normalizedTokenMatches,
    List<RelationshipMergeReportEntry> ili2dbOnly,
    List<RelationshipMergeReportEntry> ili2cOnly,
    List<RelationshipMergeReportEntry> mediumConfidence,
    List<RelationshipMergeReportEntry> suspicious,
    List<AssociationRoleMergeReportEntry> associationRoles,
    List<AssociationRoleMergeReportEntry> suspiciousAssociationRoles
) {

    public RelationshipMergeReport {
        byMergeReason = immutableMap(byMergeReason);
        byMergeConfidence = immutableMap(byMergeConfidence);
        associationRolesByMergeReason = immutableMap(associationRolesByMergeReason);
        associationRolesByMergeConfidence = immutableMap(associationRolesByMergeConfidence);
        exactMatches = immutableList(exactMatches);
        normalizedTokenMatches = immutableList(normalizedTokenMatches);
        ili2dbOnly = immutableList(ili2dbOnly);
        ili2cOnly = immutableList(ili2cOnly);
        mediumConfidence = immutableList(mediumConfidence);
        suspicious = immutableList(suspicious);
        associationRoles = immutableList(associationRoles);
        suspiciousAssociationRoles = immutableList(suspiciousAssociationRoles);
    }

    private static Map<String, Long> immutableMap(Map<String, Long> values) {
        Objects.requireNonNull(values, "values");
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static <T> List<T> immutableList(List<T> values) {
        Objects.requireNonNull(values, "values");
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}

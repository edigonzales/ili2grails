package ch.interlis.generator.report;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Builds a diagnostics report from the already merged core metadata IR.
 */
public final class RelationshipMergeReporter {

    private static final String UNKNOWN = "UNKNOWN";

    public RelationshipMergeReport create(ModelMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");

        List<RelationshipMergeReportEntry> entries = metadata.getAllRelationships().stream()
            .map(this::toEntry)
            .sorted(entryComparator())
            .toList();

        Map<String, Long> byMergeReason = entries.stream()
            .collect(Collectors.groupingBy(
                entry -> nullToUnknown(entry.mergeReason()),
                TreeMap::new,
                Collectors.counting()
            ));

        Map<String, Long> byMergeConfidence = entries.stream()
            .collect(Collectors.groupingBy(
                entry -> nullToUnknown(entry.mergeConfidence()),
                TreeMap::new,
                Collectors.counting()
            ));

        return new RelationshipMergeReport(
            metadata.getModelName(),
            entries.size(),
            byMergeReason,
            byMergeConfidence,
            filterByConfidence(entries, "EXACT"),
            filterByReason(entries, "NORMALIZED_TOKEN"),
            filterByReason(entries, "ILI2DB_ONLY"),
            filterByReason(entries, "ILI2C_ONLY"),
            filterByConfidence(entries, "MEDIUM"),
            entries.stream()
                .filter(this::isSuspicious)
                .toList()
        );
    }

    private RelationshipMergeReportEntry toEntry(RelationshipMetadata relationship) {
        return new RelationshipMergeReportEntry(
            relationship.getName(),
            relationship.getSourceClass(),
            relationship.getTargetClass(),
            enumName(relationship.getSemanticKind()),
            enumName(relationship.getType()),
            relationship.getSource(),
            relationship.getPhysicalName(),
            relationship.getSemanticName(),
            relationship.getSourceAttribute(),
            relationship.getTargetAttribute(),
            relationship.getAssociationName(),
            relationship.getSourceRoleName(),
            relationship.getTargetRoleName(),
            relationship.getOppositeRoleName(),
            enumName(relationship.getMergeReason()),
            enumName(relationship.getMergeConfidence()),
            relationship.getMergeToken(),
            relationship.getCardinality() != null ? relationship.getCardinality().toString() : null,
            relationship.isMandatory(),
            relationship.isOrdered(),
            relationship.isExternal(),
            relationship.isComposition()
        );
    }

    private boolean isSuspicious(RelationshipMergeReportEntry entry) {
        return "MEDIUM".equals(entry.mergeConfidence())
            || "ILI2DB_ONLY".equals(entry.mergeReason())
            || ("ILI2C_ONLY".equals(entry.mergeReason())
                && ("REFERENCE_ATTRIBUTE".equals(entry.semanticKind())
                    || "ASSOCIATION_ROLE".equals(entry.semanticKind())))
            || ("REFERENCE_ATTRIBUTE".equals(entry.semanticKind()) && isBlank(entry.physicalName()));
    }

    private List<RelationshipMergeReportEntry> filterByReason(
        List<RelationshipMergeReportEntry> entries,
        String mergeReason
    ) {
        return entries.stream()
            .filter(entry -> mergeReason.equals(entry.mergeReason()))
            .toList();
    }

    private List<RelationshipMergeReportEntry> filterByConfidence(
        List<RelationshipMergeReportEntry> entries,
        String mergeConfidence
    ) {
        return entries.stream()
            .filter(entry -> mergeConfidence.equals(entry.mergeConfidence()))
            .toList();
    }

    private Comparator<RelationshipMergeReportEntry> entryComparator() {
        return Comparator
            .comparing(RelationshipMergeReportEntry::mergeConfidence,
                Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMergeReportEntry::mergeReason,
                Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMergeReportEntry::sourceClass,
                Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMergeReportEntry::targetClass,
                Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMergeReportEntry::semanticKind,
                Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMergeReportEntry::name,
                Comparator.nullsLast(String::compareTo));
    }

    private String enumName(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    private String nullToUnknown(String value) {
        return value != null ? value : UNKNOWN;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

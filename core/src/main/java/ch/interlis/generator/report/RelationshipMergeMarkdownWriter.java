package ch.interlis.generator.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes relationship merge diagnostics as a human-readable Markdown report.
 */
public final class RelationshipMergeMarkdownWriter {

    public void write(RelationshipMergeReport report, Path outputPath) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, toMarkdown(report), StandardCharsets.UTF_8);
    }

    public String toMarkdown(RelationshipMergeReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Relationship Merge Report: ")
            .append(value(report.modelName()))
            .append("\n\n");
        markdown.append("Total relationships: ")
            .append(report.totalRelationships())
            .append("\n\n");

        appendSummaryTable(markdown, "Summary by mergeReason", "mergeReason", report.byMergeReason());
        appendSummaryTable(markdown, "Summary by mergeConfidence", "mergeConfidence",
            report.byMergeConfidence());
        appendRelationshipTable(markdown, "Suspicious", report.suspicious());
        appendRelationshipTable(markdown, "NORMALIZED_TOKEN matches", report.normalizedTokenMatches());
        appendRelationshipTable(markdown, "Exact matches", report.exactMatches());
        appendRelationshipTable(markdown, "ILI2DB_ONLY", report.ili2dbOnly());
        appendRelationshipTable(markdown, "ILI2C_ONLY", report.ili2cOnly());

        return markdown.toString();
    }

    private void appendSummaryTable(StringBuilder markdown,
                                    String title,
                                    String keyHeading,
                                    Map<String, Long> counts) {
        markdown.append("## ").append(title).append("\n\n");
        markdown.append("| ").append(keyHeading).append(" | Count |\n");
        markdown.append("|---|---:|\n");
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            markdown.append("| ")
                .append(escape(entry.getKey()))
                .append(" | ")
                .append(entry.getValue())
                .append(" |\n");
        }
        markdown.append("\n");
    }

    private void appendRelationshipTable(StringBuilder markdown,
                                         String title,
                                         Iterable<RelationshipMergeReportEntry> entries) {
        markdown.append("## ").append(title).append("\n\n");
        markdown.append("| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |\n");
        markdown.append("|---|---|---|---|---|---|---|---|\n");
        for (RelationshipMergeReportEntry entry : entries) {
            markdown.append("| ")
                .append(escape(entry.sourceClass()))
                .append(" | ")
                .append(escape(entry.targetClass()))
                .append(" | ")
                .append(escape(entry.semanticKind()))
                .append(" | ")
                .append(escape(entry.mergeReason()))
                .append(" | ")
                .append(escape(entry.mergeConfidence()))
                .append(" | ")
                .append(escape(entry.physicalName()))
                .append(" | ")
                .append(escape(entry.semanticName()))
                .append(" | ")
                .append(escape(entry.mergeToken()))
                .append(" |\n");
        }
        markdown.append("\n");
    }

    private String escape(String value) {
        return value(value)
            .replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("\r", " ")
            .replace("\n", " ");
    }

    private String value(String value) {
        return value != null ? value : "";
    }
}

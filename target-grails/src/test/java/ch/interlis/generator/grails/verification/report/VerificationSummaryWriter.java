package ch.interlis.generator.grails.verification.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Schreibt die gemeinsame Verification-Summary als JSON und Markdown
 * (Spezifikation §12.1, §12.2).
 */
public final class VerificationSummaryWriter {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public void writeJson(VerificationSummary summary, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, JSON_MAPPER.writeValueAsString(summary)
            + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    public void writeMarkdown(VerificationSummary summary, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        StringBuilder builder = new StringBuilder();
        builder.append("# ili2grails Verification Summary\n\n");
        builder.append("- Schema-Version: ").append(summary.schemaVersion()).append("\n");
        builder.append("- Commit: ").append(summary.commit() == null ? "unknown" : summary.commit()).append("\n");
        builder.append("- Ergebnis: ").append(summary.passed() ? "PASSED" : "FAILED")
            .append(summary.complete() ? " (complete)" : " (incomplete)").append("\n\n");
        if (summary.environment() != null) {
            builder.append("## Umgebung\n\n");
            builder.append("- OS: ")
                .append(summary.environment().osName()).append(" ")
                .append(summary.environment().osArchitecture()).append("\n");
            builder.append("- Java: ").append(summary.environment().javaVersion()).append("\n");
            builder.append("- JDBC (redigiert): ")
                .append(summary.environment().jdbcUrlRedacted() == null ? "-" : summary.environment().jdbcUrlRedacted())
                .append("\n");
            builder.append("- Tools:\n");
            summary.environment().tools().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> builder.append("  - ").append(entry.getKey())
                    .append(": ").append(entry.getValue().availability())
                    .append(entry.getValue().version() == null ? "" : " (" + entry.getValue().version() + ")")
                    .append("\n"));
            builder.append("\n");
        }
        builder.append("## Checks\n\n");
        builder.append("| ID | Status | Zusammenfassung |\n|---|---|---|\n");
        for (VerificationCheckResult check : summary.checks()) {
            builder.append("| ").append(check.id()).append(" | ")
                .append(check.status()).append(" | ")
                .append(check.summary() == null ? "" : check.summary().replace("|", "\\|"))
                .append(" |\n");
        }
        builder.append("\n");
        builder.append("## Diagnostics\n\n");
        for (VerificationCheckResult check : summary.checks()) {
            if (check.diagnostics().isEmpty()) {
                continue;
            }
            builder.append("### ").append(check.id()).append("\n\n");
            for (String diagnostic : check.diagnostics()) {
                builder.append("- ").append(diagnostic).append("\n");
            }
            builder.append("\n");
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, builder.toString(), StandardCharsets.UTF_8);
    }
}

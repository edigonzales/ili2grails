package ch.interlis.generator.grails.project.plan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Schreibt den Generationsplan als JSON und Markdown (Spezifikation §49).
 * JSON enthält nicht den vollständigen Dateiinhalt.
 */
public final class GenerationPlanReportWriter {

    public void writeJson(GenerationPlan plan, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"schemaVersion\": ").append(plan.schemaVersion()).append(",\n");
        builder.append("  \"modelName\": \"").append(escape(plan.modelName())).append("\",\n");
        builder.append("  \"modelFingerprint\": \"").append(escape(plan.modelFingerprint())).append("\",\n");
        builder.append("  \"configFingerprint\": \"").append(escape(plan.configFingerprint())).append("\",\n");
        builder.append("  \"blocked\": ").append(plan.hasBlockingDiagnostics()).append(",\n");
        builder.append("  \"changes\": [\n");
        for (int i = 0; i < plan.changes().size(); i++) {
            ProjectChange change = plan.changes().get(i);
            builder.append("    {\"path\": \"").append(escape(change.relativePath().toString()))
                .append("\", \"action\": \"").append(change.type())
                .append("\", \"owner\": \"").append(change.owner())
                .append("\", \"previousSha256\": \"").append(escape(change.previousSha256()))
                .append("\", \"plannedSha256\": \"").append(escape(change.plannedSha256()))
                .append("\", \"reason\": \"").append(escape(change.reason())).append("\"}");
            builder.append(i + 1 < plan.changes().size() ? "," : "");
            builder.append("\n");
        }
        builder.append("  ],\n");
        builder.append("  \"diagnostics\": [\n");
        for (int i = 0; i < plan.diagnostics().size(); i++) {
            GenerationDiagnostic diagnostic = plan.diagnostics().get(i);
            builder.append("    {\"level\": \"").append(diagnostic.level())
                .append("\", \"code\": \"").append(diagnostic.code())
                .append("\", \"path\": \"").append(escape(diagnostic.relativePath() == null
                    ? null : diagnostic.relativePath().toString()))
                .append("\", \"message\": \"").append(escape(diagnostic.message())).append("\"}");
            builder.append(i + 1 < plan.diagnostics().size() ? "," : "");
            builder.append("\n");
        }
        builder.append("  ]\n}\n");
        Files.writeString(target, builder.toString(), StandardCharsets.UTF_8);
    }

    public void writeMarkdown(GenerationPlan plan, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        GenerationPlanSummary summary = plan.summary();
        StringBuilder builder = new StringBuilder();
        builder.append("# Grails generation plan\n\n");
        builder.append("- Modell: `").append(escape(plan.modelName())).append("`\n");
        builder.append("- Modell-Fingerprint: `").append(escape(plan.modelFingerprint())).append("`\n");
        builder.append("- Blockiert: ").append(plan.hasBlockingDiagnostics()).append("\n\n");
        builder.append("| Aktion | Anzahl |\n|---|---:|\n");
        builder.append("| CREATE | ").append(summary.create()).append(" |\n");
        builder.append("| UPDATE | ").append(summary.update()).append(" |\n");
        builder.append("| DELETE | ").append(summary.delete()).append(" |\n");
        builder.append("| UNCHANGED | ").append(summary.unchanged()).append(" |\n");
        builder.append("| BLOCKED | ").append(summary.blocked()).append(" |\n\n");
        builder.append("## Änderungen\n\n");
        builder.append("| Pfad | Aktion | Owner | Grund |\n|---|---|---|---|\n");
        for (ProjectChange change : plan.changes()) {
            if (!change.mutating() && !change.blocked()) {
                continue;
            }
            builder.append("| `").append(escape(change.relativePath().toString()))
                .append("` | ").append(change.type())
                .append(" | ").append(change.owner())
                .append(" | ").append(escape(change.reason())).append(" |\n");
        }
        builder.append("\n## Diagnostics\n\n");
        for (GenerationDiagnostic diagnostic : plan.diagnostics()) {
            builder.append("- [").append(diagnostic.level()).append("] ")
                .append(diagnostic.code()).append(": ")
                .append(escape(diagnostic.message())).append("\n");
        }
        Files.writeString(target, builder.toString(), StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

package ch.interlis.generator.grails.verification.contract;

import ch.interlis.generator.grails.verification.mapping.DatabasePhysicalSnapshot;
import ch.interlis.generator.grails.verification.mapping.ExpectedPersistenceMapping;
import ch.interlis.generator.grails.verification.mapping.HibernateMappingSnapshot;
import ch.interlis.generator.grails.verification.mapping.MappingConsistencyReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Schreibt die Contract-Evidence-Dateien pro Szenario (Spezifikation §30.5,
 * §36). Keine Passwörter, keine kompletten JDBC-URLs.
 */
public final class ContractEvidenceWriter {

    private final Path reportDir;

    public ContractEvidenceWriter(Path reportDir) {
        this.reportDir = reportDir;
    }

    public void writeEnvironment(String environmentJson) throws IOException {
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("environment.json"), environmentJson,
            StandardCharsets.UTF_8);
    }

    public void writeFailure(Throwable failure, String processOutput) throws IOException {
        Files.createDirectories(reportDir);
        StringBuilder builder = new StringBuilder();
        builder.append("FAILURE: ").append(failure).append("\n\n");
        if (processOutput != null) {
            builder.append("PROCESS OUTPUT:\n").append(processOutput).append("\n");
        }
        Files.writeString(reportDir.resolve("failure.log"), builder.toString(),
            StandardCharsets.UTF_8);
    }

    public void writeMappingReport(ExpectedPersistenceMapping expected,
                                   HibernateMappingSnapshot hibernate,
                                   DatabasePhysicalSnapshot database,
                                   MappingConsistencyReport report) throws IOException {
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("expected-mapping.json"),
            mappingJson(expected), StandardCharsets.UTF_8);
        Files.writeString(reportDir.resolve("hibernate-mapping.json"),
            mappingJson(hibernate), StandardCharsets.UTF_8);
        Files.writeString(reportDir.resolve("database-mapping.json"),
            mappingJson(database), StandardCharsets.UTF_8);
        Files.writeString(reportDir.resolve("mapping-comparison.json"),
            comparisonJson(report), StandardCharsets.UTF_8);
        Files.writeString(reportDir.resolve("mapping-comparison.md"),
            comparisonMarkdown(report), StandardCharsets.UTF_8);
    }

    public void writeProcessOutput(String output) throws IOException {
        if (output == null) {
            return;
        }
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("integration-test-output.log"), output,
            StandardCharsets.UTF_8);
    }

    public void writeText(String name, String content) throws IOException {
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private String mappingJson(Object value) throws IOException {
        return new com.fasterxml.jackson.databind.ObjectMapper()
            .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
            .writeValueAsString(value);
    }

    private String comparisonJson(MappingConsistencyReport report) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n  \"scenarioId\": \"").append(report.scenarioId()).append("\",\n")
            .append("  \"consistent\": ").append(report.isConsistent()).append(",\n")
            .append("  \"mismatchCount\": ").append(report.mismatches().size()).append(",\n")
            .append("  \"mismatches\": [\n");
        for (int i = 0; i < report.mismatches().size(); i++) {
            var mismatch = report.mismatches().get(i);
            builder.append("    {\"code\": \"").append(mismatch.code())
                .append("\", \"entity\": \"").append(escape(mismatch.entity()))
                .append("\", \"property\": \"").append(escape(mismatch.property()))
                .append("\", \"expected\": \"").append(escape(mismatch.expected()))
                .append("\", \"actual\": \"").append(escape(mismatch.actual()))
                .append("\", \"explanation\": \"").append(escape(mismatch.explanation()))
                .append("\"}");
            builder.append(i + 1 < report.mismatches().size() ? "," : "");
            builder.append("\n");
        }
        builder.append("  ]\n}\n");
        return builder.toString();
    }

    private String comparisonMarkdown(MappingConsistencyReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Mapping-Consistency Report\n\n");
        builder.append("- Szenario: `").append(report.scenarioId()).append("`\n");
        builder.append("- Konsistent: ").append(report.isConsistent()).append("\n");
        builder.append("- Abweichungen: ").append(report.mismatches().size()).append("\n\n");
        if (report.mismatches().isEmpty()) {
            builder.append("Keine Abweichungen: erwartetes Mapping, Hibernate-Mapping und "
                + "Datenbankschema stimmen überein.\n");
        } else {
            builder.append("| Code | Entity | Property | Erwartet | Tatsächlich | Erklärung |\n");
            builder.append("|---|---|---|---|---|---|\n");
            report.mismatches().stream()
                .sorted(Comparator.comparing(mismatch -> mismatch.code().name()))
                .forEach(mismatch -> builder.append("| `").append(mismatch.code())
                    .append("` | `").append(escape(mismatch.entity()))
                    .append("` | `").append(escape(mismatch.property()))
                    .append("` | `").append(escape(mismatch.expected()))
                    .append("` | `").append(escape(mismatch.actual()))
                    .append("` | ").append(escape(mismatch.explanation())).append(" |\n"));
        }
        return builder.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

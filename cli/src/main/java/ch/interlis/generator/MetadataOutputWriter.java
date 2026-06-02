package ch.interlis.generator;

import ch.interlis.generator.metadata.MetadataJsonWriter;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.report.RelationshipMergeJsonWriter;
import ch.interlis.generator.report.RelationshipMergeMarkdownWriter;
import ch.interlis.generator.report.RelationshipMergeReport;
import ch.interlis.generator.report.RelationshipMergeReporter;

import java.io.IOException;
import java.nio.file.Path;

final class MetadataOutputWriter {

    void write(ModelMetadata metadata, MetadataCommandOptions options) throws IOException {
        if (options.metadataJsonPath() != null) {
            new MetadataJsonWriter().write(metadata, options.metadataJsonPath());
            System.out.println("Metadata JSON written to: "
                + options.metadataJsonPath().toAbsolutePath().normalize());
        }

        if (options.mergeReportDir() != null) {
            writeMergeReport(metadata, options);
        }
    }

    private void writeMergeReport(ModelMetadata metadata, MetadataCommandOptions options) throws IOException {
        RelationshipMergeReport report = new RelationshipMergeReporter().create(metadata);
        String reportName = metadata.getModelName() != null && !metadata.getModelName().isBlank()
            ? metadata.getModelName()
            : options.modelName();
        Path markdownPath = options.mergeReportDir().resolve(reportName + ".md");
        Path jsonPath = options.mergeReportDir().resolve(reportName + ".json");

        new RelationshipMergeMarkdownWriter().write(report, markdownPath);
        new RelationshipMergeJsonWriter().write(report, jsonPath);

        System.out.println("Relationship merge report written to: "
            + markdownPath.toAbsolutePath().normalize());
        System.out.println("Relationship merge report JSON written to: "
            + jsonPath.toAbsolutePath().normalize());
    }
}

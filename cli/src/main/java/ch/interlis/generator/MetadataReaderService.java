package ch.interlis.generator;

import ch.interlis.generator.metadata.MetadataPrinter;
import ch.interlis.generator.metadata.MetadataReadResult;
import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.metadata.merge.MergeDiagnostic;
import ch.interlis.generator.metadata.merge.MetadataMergeException;
import ch.interlis.generator.metadata.merge.MetadataMergePolicy;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.ili2c.Ili2cFailure;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

final class MetadataReaderService {

    /**
     * Liest Metadaten im STRICT-Modus; blockierende Merge-Diagnostics führen zu
     * einer {@link MetadataMergeException} mit strukturierten Diagnostics.
     */
    ModelMetadata read(MetadataCommandOptions options) throws SQLException, Ili2cFailure {
        MetadataReadResult result = readResult(options);
        return result.metadata();
    }

    /**
     * Liest Metadaten inklusive Diagnostics und Modellauswahl. Konsolenausgabe
     * enthält die gewählten Modelle und die Merge-Diagnostics.
     */
    MetadataReadResult readResult(MetadataCommandOptions options)
            throws SQLException, Ili2cFailure {
        File modelFile = options.modelFilePath() != null ? options.modelFilePath().toFile() : null;

        System.out.println("INTERLIS CRUD Generator - Metadata Reader");
        System.out.println("========================================");
        System.out.println("JDBC URL:    " + options.jdbcUrl());
        System.out.println("Model File:  " + (modelFile != null ? modelFile.getAbsolutePath() : "(repository lookup)"));
        System.out.println("Model Name:  " + options.modelName());
        System.out.println("Schema:      " + formatSchema(options.schema()));
        System.out.println("Model Repos: " + String.join(", ", options.modelRepositories()));
        System.out.println();

        try (Connection conn = DriverManager.getConnection(options.jdbcUrl())) {
            System.out.println("Database connection established.");
            System.out.println();

            MetadataReader reader = new MetadataReader(
                conn,
                modelFile,
                options.schema(),
                options.modelRepositories()
            );
            MetadataReadResult result = reader.readMetadataResult(
                options.modelName(), MetadataMergePolicy.STRICT);

            System.out.println("Selected models:");
            for (String modelName : result.modelSelection().includedModelNames()) {
                System.out.println("  - " + modelName);
            }
            printDiagnostics(result.diagnostics());

            System.out.println();
            System.out.println("Metadata reading completed successfully!");
            System.out.println();

            new MetadataPrinter().print(result.metadata());
            return result;
        }
    }

    void printDiagnostics(List<MergeDiagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            System.out.println();
            System.out.println("Metadata diagnostics: none");
            return;
        }
        System.out.println();
        System.out.println("Metadata diagnostics:");
        diagnostics.stream()
            .sorted(Comparator
                .comparing(MergeDiagnostic::severity)
                .thenComparing(MergeDiagnostic::code)
                .thenComparing(diagnostic -> diagnostic.semanticElement() == null
                    ? "" : diagnostic.semanticElement()))
            .forEach(diagnostic -> System.out.println(
                "  " + diagnostic.severity() + " " + diagnostic.code()
                    + (diagnostic.semanticElement() != null
                    ? " " + diagnostic.semanticElement() : "")
                    + " - " + diagnostic.message()));
    }

    /**
     * Gibt blockierende Diagnostics kompakt auf stderr aus und wirft die
     * strukturierte Exception.
     */
    /**
     * Formatierter Schemaname für die Konsolenausgabe.
     */
    String formatSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return "(none)";
        }
        return schema;
    }
}

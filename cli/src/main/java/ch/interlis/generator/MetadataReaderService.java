package ch.interlis.generator;

import ch.interlis.generator.metadata.MetadataPrinter;
import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.ili2c.Ili2cFailure;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

final class MetadataReaderService {

    ModelMetadata read(MetadataCommandOptions options) throws SQLException, Ili2cFailure {
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
            ModelMetadata metadata = reader.readMetadata(options.modelName());

            System.out.println();
            System.out.println("Metadata reading completed successfully!");
            System.out.println();

            new MetadataPrinter().print(metadata);
            return metadata;
        }
    }

    private String formatSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return "(none)";
        }
        return schema;
    }
}

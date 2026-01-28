package ch.interlis.generator;

import ch.interlis.generator.generator.GenerationConfig;
import ch.interlis.generator.generator.GrailsCrudGenerator;
import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.metadata.MetadataPrinter;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.ili2c.Ili2cFailure;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo-Anwendung zum Testen des Metadata-Readers.
 * 
 * Verwendung:
 *   java MetadataReaderApp <jdbcUrl> <modelFile> <modelName> [schema]
 * 
 * Beispiele:
 *   PostgreSQL:
 *     java MetadataReaderApp "jdbc:postgresql://localhost:5432/mydb?user=user&password=pass" \
 *                            model.ili MyModel public
 *   
 *   H2 (embedded):
 *     java MetadataReaderApp "jdbc:h2:./testdb" model.ili MyModel
 */
public class MetadataReaderApp {
    
    public static void main(String[] args) {
        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }

        CliOptions options = parseArgs(args);
        if (options == null) {
            System.exit(1);
        }

        File modelFile = new File(options.modelFilePath);
        
        if (!modelFile.exists()) {
            System.err.println("Model file not found: " + options.modelFilePath);
            System.exit(1);
        }
        
        System.out.println("INTERLIS CRUD Generator - Metadata Reader");
        System.out.println("========================================");
        System.out.println("JDBC URL:    " + options.jdbcUrl);
        System.out.println("Model File:  " + options.modelFilePath);
        System.out.println("Model Name:  " + options.modelName);
        System.out.println("Schema:      " + (options.schema != null ? options.schema : "auto-detect"));
        System.out.println();
        
        try (Connection conn = DriverManager.getConnection(options.jdbcUrl)) {
            System.out.println("Database connection established.");
            System.out.println();
            
            // Metadata Reader erstellen
            MetadataReader reader = new MetadataReader(
                conn, 
                modelFile, 
                options.schema,
                getDefaultModelDirs()
            );
            
            // Metadaten lesen
            ModelMetadata metadata = reader.readMetadata(options.modelName);
            
            System.out.println();
            System.out.println("Metadata reading completed successfully!");
            System.out.println();
            
            // Metadaten ausgeben
            MetadataPrinter printer = new MetadataPrinter();
            printer.print(metadata);

            if (options.grailsOutputDir != null) {
                generateGrailsCrud(metadata, options);
            }
            
            System.out.println();
            System.out.println("===================================================");
            System.out.println("Metadata is now available for code generation.");
            
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Ili2cFailure e) {
            System.err.println("INTERLIS model compilation error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage: MetadataReaderApp <jdbcUrl> <modelFile> <modelName> [schema] [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  jdbcUrl    - JDBC connection URL (e.g., jdbc:postgresql://localhost/db?user=u&password=p)");
        System.out.println("  modelFile  - Path to INTERLIS model file (.ili)");
        System.out.println("  modelName  - Name of the INTERLIS model to process");
        System.out.println("  schema     - Database schema name (optional, will auto-detect if not specified)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --grails-output <dir>             - Output directory for Grails CRUD artifacts");
        System.out.println("  --grails-package <package>        - Base package for generated classes (default: com.example)");
        System.out.println("  --grails-domain-package <package> - Package for domain classes (default: <base>)");
        System.out.println("  --grails-controller-package <package> - Package for controllers (default: <base>)");
        System.out.println("  --grails-enum-package <package>   - Package for enums (default: <base>.enums)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  PostgreSQL:");
        System.out.println("    MetadataReaderApp \"jdbc:postgresql://localhost:5432/mydb?user=u&password=p\" \\");
        System.out.println("                      models/DM01AVCH24LV95D.ili DM01AVCH24LV95D public");
        System.out.println();
        System.out.println("  H2 Database:");
        System.out.println("    MetadataReaderApp \"jdbc:h2:./data/testdb\" models/Simple.ili SimpleModel");
        System.out.println();
        System.out.println("  Grails CRUD generation:");
        System.out.println("    MetadataReaderApp \"jdbc:h2:./data/testdb\" models/Simple.ili SimpleModel \\");
        System.out.println("      --grails-output ./generated-grails --grails-package com.example");
    }
    
    private static List<String> getDefaultModelDirs() {
        return Arrays.asList(
            "http://models.interlis.ch/",
            "http://models.geo.admin.ch/"
        );
    }

    private static void generateGrailsCrud(ModelMetadata metadata, CliOptions options) throws IOException {
        String basePackage = options.grailsBasePackage != null ? options.grailsBasePackage : "com.example";
        GenerationConfig.Builder builder = GenerationConfig.builder(options.grailsOutputDir, basePackage);
        if (options.grailsDomainPackage != null) {
            builder.domainPackage(options.grailsDomainPackage);
        }
        if (options.grailsControllerPackage != null) {
            builder.controllerPackage(options.grailsControllerPackage);
        }
        if (options.grailsEnumPackage != null) {
            builder.enumPackage(options.grailsEnumPackage);
        }
        GenerationConfig config = builder.build();
        new GrailsCrudGenerator().generate(metadata, config);
        System.out.println("Grails CRUD artifacts generated in: " + options.grailsOutputDir.toAbsolutePath());
    }

    private static CliOptions parseArgs(String[] args) {
        List<String> positional = new ArrayList<>();
        Map<String, String> options = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                if (i + 1 >= args.length) {
                    System.err.println("Missing value for option: " + arg);
                    printUsage();
                    return null;
                }
                String value = args[++i];
                options.put(arg, value);
            } else {
                positional.add(arg);
            }
        }

        if (positional.size() < 3 || positional.size() > 4) {
            printUsage();
            return null;
        }

        CliOptions cliOptions = new CliOptions();
        cliOptions.jdbcUrl = positional.get(0);
        cliOptions.modelFilePath = positional.get(1);
        cliOptions.modelName = positional.get(2);
        cliOptions.schema = positional.size() > 3 ? positional.get(3) : null;
        if (options.containsKey("--grails-output")) {
            cliOptions.grailsOutputDir = Path.of(options.get("--grails-output"));
        }
        cliOptions.grailsBasePackage = options.get("--grails-package");
        cliOptions.grailsDomainPackage = options.get("--grails-domain-package");
        cliOptions.grailsControllerPackage = options.get("--grails-controller-package");
        cliOptions.grailsEnumPackage = options.get("--grails-enum-package");
        return cliOptions;
    }

    private static class CliOptions {
        private String jdbcUrl;
        private String modelFilePath;
        private String modelName;
        private String schema;
        private Path grailsOutputDir;
        private String grailsBasePackage;
        private String grailsDomainPackage;
        private String grailsControllerPackage;
        private String grailsEnumPackage;
    }
}

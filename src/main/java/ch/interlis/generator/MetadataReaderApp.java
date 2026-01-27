package ch.interlis.generator;

import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.metadata.MetadataPrinter;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.ili2c.Ili2cFailure;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

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
        
        String jdbcUrl = args[0];
        String modelFilePath = args[1];
        String modelName = args[2];
        String schema = args.length > 3 ? args[3] : null;
        
        File modelFile = new File(modelFilePath);
        
        if (!modelFile.exists()) {
            System.err.println("Model file not found: " + modelFilePath);
            System.exit(1);
        }
        
        System.out.println("INTERLIS CRUD Generator - Phase 1: Metadata Reader");
        System.out.println("===================================================");
        System.out.println("JDBC URL:    " + jdbcUrl);
        System.out.println("Model File:  " + modelFilePath);
        System.out.println("Model Name:  " + modelName);
        System.out.println("Schema:      " + (schema != null ? schema : "auto-detect"));
        System.out.println();
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            System.out.println("Database connection established.");
            System.out.println();
            
            // Metadata Reader erstellen
            MetadataReader reader = new MetadataReader(
                conn, 
                modelFile, 
                schema,
                getDefaultModelDirs()
            );
            
            // Metadaten lesen
            ModelMetadata metadata = reader.readMetadata(modelName);
            
            System.out.println();
            System.out.println("Metadata reading completed successfully!");
            System.out.println();
            
            // Metadaten ausgeben
            MetadataPrinter printer = new MetadataPrinter();
            printer.print(metadata);
            
            System.out.println();
            System.out.println("===================================================");
            System.out.println("Phase 1 complete. Metadata is now available for code generation.");
            
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
        System.out.println("Usage: MetadataReaderApp <jdbcUrl> <modelFile> <modelName> [schema]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  jdbcUrl    - JDBC connection URL (e.g., jdbc:postgresql://localhost/db?user=u&password=p)");
        System.out.println("  modelFile  - Path to INTERLIS model file (.ili)");
        System.out.println("  modelName  - Name of the INTERLIS model to process");
        System.out.println("  schema     - Database schema name (optional, will auto-detect if not specified)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  PostgreSQL:");
        System.out.println("    MetadataReaderApp \"jdbc:postgresql://localhost:5432/mydb?user=u&password=p\" \\");
        System.out.println("                      models/DM01AVCH24LV95D.ili DM01AVCH24LV95D public");
        System.out.println();
        System.out.println("  H2 Database:");
        System.out.println("    MetadataReaderApp \"jdbc:h2:./data/testdb\" models/Simple.ili SimpleModel");
    }
    
    private static List<String> getDefaultModelDirs() {
        return Arrays.asList(
            "http://models.interlis.ch/",
            "http://models.geo.admin.ch/"
        );
    }
}

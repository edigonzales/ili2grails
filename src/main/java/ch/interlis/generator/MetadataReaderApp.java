package ch.interlis.generator;

import ch.interlis.generator.generator.GenerationConfig;
import ch.interlis.generator.generator.GrailsCrudGenerator;
import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.metadata.MetadataPrinter;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.ili2c.Ili2cFailure;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
        System.out.println("Schema:      " + options.schema);
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
        System.out.println("Usage: MetadataReaderApp <jdbcUrl> <modelFile> <modelName> <schema> [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  jdbcUrl    - JDBC connection URL (e.g., jdbc:postgresql://localhost/db?user=u&password=p)");
        System.out.println("  modelFile  - Path to INTERLIS model file (.ili)");
        System.out.println("  modelName  - Name of the INTERLIS model to process");
        System.out.println("  schema     - Database schema name");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --grails-output <dir>             - Output directory for Grails CRUD artifacts");
        System.out.println("  --grails-init [appName]           - Initialize a Grails app in the output directory");
        System.out.println("  --grails-version <x.y>            - Grails version for --grails-init");
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
        System.out.println("    MetadataReaderApp \"jdbc:h2:./data/testdb\" models/Simple.ili SimpleModel PUBLIC");
        System.out.println();
        System.out.println("  Grails CRUD generation:");
        System.out.println("    MetadataReaderApp \"jdbc:h2:./data/testdb\" models/Simple.ili SimpleModel PUBLIC \\");
        System.out.println("      --grails-output ./generated-grails --grails-package com.example");
    }
    
    private static List<String> getDefaultModelDirs() {
        return Arrays.asList(
            "http://models.interlis.ch/",
            "http://models.geo.admin.ch/"
        );
    }

    private static void generateGrailsCrud(ModelMetadata metadata, CliOptions options)
        throws IOException, InterruptedException {
        Path outputDir = Objects.requireNonNull(options.grailsOutputDir, "grailsOutputDir");
        Path grailsProjectDir = outputDir;
        if (options.grailsInitRequested) {
            grailsProjectDir = scaffoldGrailsProjectIfNeeded(options, outputDir);
        }

        String basePackage = options.grailsBasePackage != null ? options.grailsBasePackage : "com.example";
        GenerationConfig.Builder builder = GenerationConfig.builder(grailsProjectDir, basePackage);
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
        System.out.println();
        System.out.println("===================================================");
        System.out.println("Grails CRUD artifacts generated in: " + grailsProjectDir.toAbsolutePath());
    }

    private static CliOptions parseArgs(String[] args) {
        List<String> positional = new ArrayList<>();
        CliOptions cliOptions = new CliOptions();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                positional.add(arg);
                continue;
            }

            switch (arg) {
                case "--grails-init":
                    cliOptions.grailsInitRequested = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        cliOptions.grailsInitAppName = args[++i];
                    }
                    break;
                case "--grails-output":
                    String outputValue = readOptionValue(args, arg, ++i);
                    if (outputValue == null) {
                        return null;
                    }
                    cliOptions.grailsOutputDir = Path.of(outputValue);
                    break;
                case "--grails-version":
                    String versionValue = readOptionValue(args, arg, ++i);
                    if (versionValue == null) {
                        return null;
                    }
                    cliOptions.grailsVersion = versionValue;
                    break;
                case "--grails-package":
                    cliOptions.grailsBasePackage = readOptionValue(args, arg, ++i);
                    if (cliOptions.grailsBasePackage == null) {
                        return null;
                    }
                    break;
                case "--grails-domain-package":
                    cliOptions.grailsDomainPackage = readOptionValue(args, arg, ++i);
                    if (cliOptions.grailsDomainPackage == null) {
                        return null;
                    }
                    break;
                case "--grails-controller-package":
                    cliOptions.grailsControllerPackage = readOptionValue(args, arg, ++i);
                    if (cliOptions.grailsControllerPackage == null) {
                        return null;
                    }
                    break;
                case "--grails-enum-package":
                    cliOptions.grailsEnumPackage = readOptionValue(args, arg, ++i);
                    if (cliOptions.grailsEnumPackage == null) {
                        return null;
                    }
                    break;
                default:
                    System.err.println("Unknown option: " + arg);
                    printUsage();
                    return null;
            }
        }

        if (positional.size() != 4) {
            printUsage();
            return null;
        }

        cliOptions.jdbcUrl = positional.get(0);
        cliOptions.modelFilePath = positional.get(1);
        cliOptions.modelName = positional.get(2);
        cliOptions.schema = positional.get(3);
        if (cliOptions.grailsInitRequested && cliOptions.grailsOutputDir == null) {
            System.err.println("Option --grails-init requires --grails-output.");
            printUsage();
            return null;
        }
        if (cliOptions.grailsVersion != null && !cliOptions.grailsInitRequested) {
            System.err.println("Option --grails-version requires --grails-init.");
            printUsage();
            return null;
        }
        return cliOptions;
    }

    private static String readOptionValue(String[] args, String option, int index) {
        if (index >= args.length) {
            System.err.println("Missing value for option: " + option);
            printUsage();
            return null;
        }
        String value = args[index];
        if (value.startsWith("--")) {
            System.err.println("Missing value for option: " + option);
            printUsage();
            return null;
        }
        return value;
    }

    private static Path scaffoldGrailsProjectIfNeeded(CliOptions options, Path outputDir)
        throws IOException, InterruptedException {
        String appName = options.grailsInitAppName;
        Path appDir = outputDir;
        if (appName == null || appName.isBlank()) {
            Path fileName = outputDir.getFileName();
            appName = fileName != null ? fileName.toString() : "grails-app";
        } else {
            appDir = outputDir.resolve(appName);
        }

        if (isGrailsProject(appDir)) {
            throw new IllegalStateException(
                "Grails scaffold blocked: existing project detected at "
                    + appDir.toAbsolutePath()
            );
        }

        if (Files.exists(outputDir) && !Files.isDirectory(outputDir)) {
            throw new IllegalStateException("Grails scaffold blocked: target is not a directory: "
                + outputDir.toAbsolutePath());
        }
        if (Files.exists(outputDir) && !isDirectoryEmpty(outputDir)) {
            throw new IllegalStateException("Grails scaffold blocked: target directory is not empty: "
                + outputDir.toAbsolutePath());
        }
        if (!appDir.equals(outputDir) && Files.exists(appDir)) {
            throw new IllegalStateException("Grails scaffold blocked: app directory already exists: "
                + appDir.toAbsolutePath());
        }

        Path workingDir = appDir.equals(outputDir)
            ? resolveWorkingDir(outputDir)
            : outputDir.toAbsolutePath().normalize();
        runGrailsCreateApp(workingDir, appName, options.grailsVersion);
        return appDir;
    }

    private static boolean isGrailsProject(Path outputDir) {
        return Files.exists(outputDir.resolve("build.gradle"))
            || Files.exists(outputDir.resolve("settings.gradle"))
            || Files.exists(outputDir.resolve("grails-app"));
    }

    private static boolean isDirectoryEmpty(Path outputDir) throws IOException {
        try (var stream = Files.list(outputDir)) {
            return stream.findAny().isEmpty();
        }
    }

    private static void runGrailsCreateApp(Path workingDir, String appName, String grailsVersion)
        throws IOException, InterruptedException {
        Files.createDirectories(workingDir);

        List<String> command = new ArrayList<>();
        command.add("grails");
        command.add("create-app");
        command.add(appName);
        if (grailsVersion != null && !grailsVersion.isBlank()) {
            command.add("--grails-version");
            command.add(grailsVersion);
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Grails CLI failed (exit " + exitCode + ") while creating app in "
                + workingDir.toAbsolutePath().normalize().resolve(appName) + ". Output:\n" + output);
        }
    }

    private static Path resolveWorkingDir(Path outputDir) {
        Path absoluteOutputDir = outputDir.toAbsolutePath().normalize();
        Path workingDir = absoluteOutputDir.getParent();
        if (workingDir == null) {
            workingDir = Path.of(".").toAbsolutePath().normalize();
        }
        return workingDir;
    }

    private static class CliOptions {
        private String jdbcUrl;
        private String modelFilePath;
        private String modelName;
        private String schema;
        private Path grailsOutputDir;
        private boolean grailsInitRequested;
        private String grailsInitAppName;
        private String grailsVersion;
        private String grailsBasePackage;
        private String grailsDomainPackage;
        private String grailsControllerPackage;
        private String grailsEnumPackage;
    }
}

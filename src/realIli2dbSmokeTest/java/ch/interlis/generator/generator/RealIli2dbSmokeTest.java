package ch.interlis.generator.generator;

import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RealIli2dbSmokeTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret";
    private static final List<String> MODEL_REPOSITORIES = List.of(
        "test-models",
        "https://models.interlis.ch/",
        "https://models.geo.admin.ch/"
    );
    private static final Path CORE_IR_MODEL_FILE = Path.of("test-models/CoreIrTestModel.ili");
    private static final Path VSADSSMINI_MODEL_FILE = Path.of(
        "test-models/VSADSSMINI_2020_2_d_LV95-20251129.ili"
    );
    private static final Path REPORT_DIR = Path.of("build/reports/real-ili2db-smoke");
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(8);
    private static final Duration DB_TIMEOUT = Duration.ofSeconds(90);

    @TempDir
    Path tempDir;

    @Test
    void validatesCoreIrStructureCompositionAgainstRealIli2pgSchema() throws Exception {
        RealSchemaMetadata realSchema = importAndReadMetadata(
            "CoreIrTestModel",
            CORE_IR_MODEL_FILE,
            "rt_coreir_"
        );

        GenerationConfig config = GenerationConfig.builder(tempDir.resolve("coreir-generated"), "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(realSchema.metadata(), config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(realSchema.metadata(), config, registry);
        StructureSummary summary = summarize(realSchema.modelName(), realSchema.schemaName(), realSchema.metadata(), mapper);
        writeSummary(REPORT_DIR.resolve("structure-composition-summary.json"), summary);

        assertThat(summary.structureCount())
            .as("CoreIrTestModel should exercise STRUCTURE handling in a real ili2pg schema")
            .isGreaterThan(0);
        assertThat(summary.compositionTargetCount())
            .as("CoreIrTestModel should exercise Composition targets")
            .isGreaterThan(0);

        structures(realSchema.metadata())
            .filter(this::hasPhysicalMapping)
            .forEach(structure -> assertThat(mapper.shouldGenerateClass(structure))
                .as("physical structure should be generated: %s", structure.getName())
                .isTrue());

        structures(realSchema.metadata())
            .filter(structure -> !hasPhysicalMapping(structure))
            .filter(structure -> !summary.compositionTargetNames().contains(structure.getName()))
            .forEach(structure -> assertThat(mapper.shouldGenerateClass(structure))
                .as("unused non-persistent structure should not be generated: %s", structure.getName())
                .isFalse());

        structures(realSchema.metadata())
            .filter(structure -> summary.compositionTargetNames().contains(structure.getName()))
            .filter(structure -> !structure.isAbstract())
            .forEach(structure -> assertThat(mapper.shouldGenerateClass(structure))
                .as("composition target should be generated: %s", structure.getName())
                .isTrue());

        assertNoNamingCollisions(realSchema.metadata(), registry, mapper);
        new GrailsCrudGenerator().generate(realSchema.metadata(), config);
        GeneratedGroovyCompiler.compileGeneratedSources(config.getOutputDir());
    }

    @Test
    void validatesVsadssminiLargeModelAgainstRealIli2pgSchema() throws Exception {
        RealSchemaMetadata realSchema = importAndReadMetadata(
            "VSADSSMINI_2020_LV95",
            VSADSSMINI_MODEL_FILE,
            "rt_vsadssmini_"
        );

        GenerationConfig config = GenerationConfig.builder(tempDir.resolve("vsadssmini-generated"), "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(realSchema.metadata(), config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(realSchema.metadata(), config, registry);
        writeSummary(
            REPORT_DIR.resolve("vsadssmini-structure-composition-summary.json"),
            summarize(realSchema.modelName(), realSchema.schemaName(), realSchema.metadata(), mapper)
        );

        assertThat(realSchema.metadata().getAllClasses()).isNotEmpty();
        assertThat(realSchema.metadata().getAllRelationships()).isNotEmpty();
        assertNoNamingCollisions(realSchema.metadata(), registry, mapper);
        new GrailsCrudGenerator().generate(realSchema.metadata(), config);
        GeneratedGroovyCompiler.compileGeneratedSources(config.getOutputDir());
    }

    private RealSchemaMetadata importAndReadMetadata(String modelName, Path modelFile, String schemaPrefix)
        throws Exception {
        if (!Files.exists(modelFile)) {
            throw new TestAbortedException("Model file not available: " + modelFile);
        }
        Path ili2pgHome = requireIli2pgHome();
        requireDockerCompose();
        startComposeDb();
        waitForDatabase();

        String schemaName = uniqueSchemaName(schemaPrefix);
        try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
            dropSchema(connection, schemaName);
        }

        try {
            runIli2pgImport(ili2pgHome, modelName, schemaName);
            try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
                MetadataReader reader = new MetadataReader(connection, modelFile.toFile(), schemaName, MODEL_REPOSITORIES);
                return new RealSchemaMetadata(modelName, schemaName, reader.readMetadata(modelName));
            }
        } finally {
            try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
                dropSchema(connection, schemaName);
            } catch (SQLException ignored) {
                // The schema is temporary. A cleanup failure must not hide the original test failure.
            }
        }
    }

    private Path requireIli2pgHome() {
        String configuredHome = System.getProperty("ili2pgHome", "/Users/stefan/apps/ili2pg-5.5.1");
        Path ili2pgHome = Path.of(configuredHome);
        if (!Files.exists(ili2pgHome.resolve("ili2pg-5.5.1.jar"))
            || !Files.isDirectory(ili2pgHome.resolve("libs"))) {
            throw new TestAbortedException("ili2pg home not available: " + ili2pgHome);
        }
        return ili2pgHome;
    }

    private void requireDockerCompose() throws IOException, InterruptedException {
        CommandResult result = runCommand(List.of("docker", "compose", "version"), Path.of("."), Duration.ofSeconds(30));
        if (result.exitCode() != 0) {
            throw new TestAbortedException("docker compose not available: " + result.output());
        }
    }

    private void startComposeDb() throws IOException, InterruptedException {
        CommandResult result = runCommand(
            List.of("docker", "compose", "up", "-d", "edit-db"),
            Path.of("."),
            Duration.ofMinutes(3)
        );
        if (result.exitCode() != 0) {
            throw new TestAbortedException("Could not start docker compose edit-db: " + result.output());
        }
    }

    private void waitForDatabase() throws InterruptedException {
        long deadline = System.nanoTime() + DB_TIMEOUT.toNanos();
        SQLException lastError = null;
        while (System.nanoTime() < deadline) {
            try (Connection ignored = DriverManager.getConnection(JDBC_URL)) {
                return;
            } catch (SQLException e) {
                lastError = e;
                Thread.sleep(1000);
            }
        }
        throw new TestAbortedException("PostGIS database not reachable at " + JDBC_URL, lastError);
    }

    private void runIli2pgImport(Path ili2pgHome, String modelName, String schemaName)
        throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = ili2pgHome.resolve("ili2pg-5.5.1.jar")
            + File.pathSeparator
            + ili2pgHome.resolve("libs/*");

        List<String> command = new ArrayList<>(List.of(
            javaExecutable.toString(),
            "-cp", classpath,
            "ch.ehi.ili2pg.PgMain",
            "--dbhost", "localhost",
            "--dbport", "54321",
            "--dbdatabase", "edit",
            "--dbusr", "postgres",
            "--dbpwd", "secret",
            "--defaultSrsCode", "2056",
            "--createFk",
            "--nameByTopic",
            "--strokeArcs",
            "--smart2Inheritance",
            "--createEnumTabs",
            "--createBasketCol",
            "--modeldir", String.join(";", MODEL_REPOSITORIES),
            "--models", modelName,
            "--dbschema", schemaName,
            "--schemaimport"
        ));

        CommandResult result = runCommand(command, Path.of("."), PROCESS_TIMEOUT);
        if (result.exitCode() == 0) {
            return;
        }
        if (looksLikeRepositoryProblem(result.output())) {
            throw new TestAbortedException("ili2pg model repository lookup unavailable: " + result.output());
        }
        throw new IOException("ili2pg import failed for " + modelName + " in schema " + schemaName
            + " (exit " + result.exitCode() + "):\n" + result.output());
    }

    private CommandResult runCommand(List<String> command, Path workingDir, Duration timeout)
        throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(124, "Command timed out after " + timeout + ": "
                + String.join(" ", command) + "\n" + output);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new CommandResult(process.exitValue(), output);
    }

    private boolean looksLikeRepositoryProblem(String output) {
        String lower = output == null ? "" : output.toLowerCase(Locale.ROOT);
        return lower.contains("unknownhost")
            || lower.contains("timed out")
            || lower.contains("could not find model")
            || lower.contains("failed to get model");
    }

    private void dropSchema(Connection connection, String schemaName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    private String uniqueSchemaName(String prefix) {
        return prefix + Long.toUnsignedString(System.nanoTime(), 36).toLowerCase(Locale.ROOT);
    }

    private StructureSummary summarize(String modelName,
                                       String schemaName,
                                       ModelMetadata metadata,
                                       GrailsRelationshipMapper mapper) {
        Set<String> compositionTargets = metadata.getAllRelationships().stream()
            .filter(relationship -> relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE)
            .map(RelationshipMetadata::getTargetClass)
            .filter(name -> name != null && !name.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ClassMetadata> structures = structures(metadata).toList();
        long physicalStructures = structures.stream().filter(this::hasPhysicalMapping).count();
        long generatedStructures = structures.stream().filter(mapper::shouldGenerateClass).count();
        long nonGeneratedStructures = structures.size() - generatedStructures;
        Map<String, Long> relationshipCounts = metadata.getAllRelationships().stream()
            .collect(Collectors.groupingBy(
                relationship -> relationship.getSemanticKind() == null
                    ? "UNSPECIFIED"
                    : relationship.getSemanticKind().name(),
                LinkedHashMap::new,
                Collectors.counting()
            ));
        return new StructureSummary(
            modelName,
            schemaName,
            metadata.getAllClasses().size(),
            structures.size(),
            physicalStructures,
            compositionTargets.size(),
            generatedStructures,
            nonGeneratedStructures,
            compositionTargets,
            relationshipCounts
        );
    }

    private Stream<ClassMetadata> structures(ModelMetadata metadata) {
        return metadata.getAllClasses().stream()
            .filter(classMetadata -> classMetadata.getKind() == ClassMetadata.ClassKind.STRUCTURE);
    }

    private boolean hasPhysicalMapping(ClassMetadata classMetadata) {
        return (classMetadata.getTableName() != null && !classMetadata.getTableName().isBlank())
            || (classMetadata.getSqlName() != null && !classMetadata.getSqlName().isBlank());
    }

    private void assertNoNamingCollisions(ModelMetadata metadata,
                                          TargetNameRegistry registry,
                                          GrailsRelationshipMapper mapper) {
        List<String> classNames = mapper.generatedClasses().stream()
            .map(registry::className)
            .toList();
        assertThat(classNames).doesNotHaveDuplicates();

        List<String> enumNames = metadata.getAllEnums().stream()
            .map(registry::enumName)
            .toList();
        assertThat(enumNames).doesNotHaveDuplicates();

        for (ClassMetadata classMetadata : mapper.generatedClasses()) {
            GrailsRelationshipMapper.DomainMapping mapping = mapper.map(classMetadata);
            List<String> propertyNames = new ArrayList<>();
            mapping.properties().forEach(property -> propertyNames.add(property.name()));
            mapping.collections().forEach(collection -> propertyNames.add(collection.name()));
            assertThat(propertyNames)
                .as("generated properties for %s", classMetadata.getName())
                .doesNotHaveDuplicates();
        }
    }

    private void writeSummary(Path target, StructureSummary summary) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, summary.toJson(), StandardCharsets.UTF_8);
    }

    private record RealSchemaMetadata(String modelName, String schemaName, ModelMetadata metadata) {
    }

    private record CommandResult(int exitCode, String output) {
    }

    private record StructureSummary(
        String modelName,
        String schemaName,
        int classCount,
        int structureCount,
        long physicalStructureCount,
        int compositionTargetCount,
        long generatedStructureCount,
        long nonGeneratedStructureCount,
        Set<String> compositionTargetNames,
        Map<String, Long> relationshipCounts
    ) {
        String toJson() {
            return "{\n"
                + "  \"modelName\": \"" + escape(modelName) + "\",\n"
                + "  \"schemaName\": \"" + escape(schemaName) + "\",\n"
                + "  \"classCount\": " + classCount + ",\n"
                + "  \"structureCount\": " + structureCount + ",\n"
                + "  \"physicalStructureCount\": " + physicalStructureCount + ",\n"
                + "  \"compositionTargetCount\": " + compositionTargetCount + ",\n"
                + "  \"generatedStructureCount\": " + generatedStructureCount + ",\n"
                + "  \"nonGeneratedStructureCount\": " + nonGeneratedStructureCount + ",\n"
                + "  \"compositionTargetNames\": " + stringArray(compositionTargetNames) + ",\n"
                + "  \"relationshipCounts\": " + countObject(relationshipCounts) + "\n"
                + "}\n";
        }

        private static String stringArray(Set<String> values) {
            return values.stream()
                .sorted()
                .map(value -> "\"" + escape(value) + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
        }

        private static String countObject(Map<String, Long> counts) {
            return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "\"" + escape(entry.getKey()) + "\": " + entry.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
        }

        private static String escape(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}

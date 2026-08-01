package ch.interlis.generator.grails;

import ch.interlis.generator.metadata.MetadataReadResult;
import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.metadata.merge.MergeDiagnostic;
import ch.interlis.generator.metadata.merge.MetadataMergeException;
import ch.interlis.generator.metadata.merge.MetadataMergePolicy;
import ch.interlis.generator.metadata.merge.MergeDiagnostic;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ch.interlis.generator.grails.verification.contract.CommandResult;
import ch.interlis.generator.grails.verification.contract.CommandRunner;
import ch.interlis.generator.grails.verification.environment.ExternalToolStatus;
import ch.interlis.generator.grails.verification.environment.InfrastructureSupport;
import ch.interlis.generator.grails.verification.environment.VerificationEnvironmentDetector;
import org.opentest4j.TestAbortedException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kombinierter Vertragstest: generierte Grails-App + GORM + Runtime-Services
 * gegen ein echtes ili2pg-PostgreSQL-Schema.
 *
 * <p>Kette: {@code P0PersistenceContract.ili} → ili2pg-Import → physischer
 * Reader → semantischer Reader → ModelSelection → MetadataMerger →
 * Grails-Generator → temporäre echte Grails-App → GORM → echtes PostgreSQL →
 * Runtime-Service-Aufrufe. Direktes JDBC-SQL wird nur für Infrastruktur,
 * Setup und unabhängige Verifikation verwendet; die Businessoperationen laufen
 * über die generierten Runtime-Services.</p>
 *
 * <p>Infrastrukturmodus: Bei {@code contractTestRequired=false} wird fehlende
 * Infrastruktur mit {@link TestAbortedException} gemeldet. Im obligatorischen
 * Modus ({@code -PcontractTestRequired=true}) ist jedes fehlende Werkzeug ein
 * Fehler, kein Skip.</p>
 */
public class GrailsPostgresContractTest {

    private static final Path MODEL_FILE = Path.of("test-models/P0PersistenceContract.ili");
    private static final String MODEL_NAME = "P0PersistenceContract";
    private static final List<String> MODEL_REPOSITORIES = List.of(
        "test-models",
        "https://models.interlis.ch/",
        "https://models.geo.admin.ch/"
    );
    private static final Path REPORT_DIR =
        Path.of("target-grails/build/reports/grails-postgres-contract");
    private static final String APP_NAME = "p0-contract-app";
    private static final String BASE_PACKAGE = "com.example";
    private static final String DOMAIN_PACKAGE = "com.example.domain";
    private static final String ENUM_PACKAGE = "com.example.enums";
    private static final String SPEC_PATH =
        "src/integration-test/groovy/com/example/P0PostgresPersistenceContractSpec.groovy";
    private static final String JOURNEY_LINK_ASSOCIATION =
        "P0PersistenceContract.Contract.JourneyLink";
    private static final String PARCEL_OWNER_LINK_ASSOCIATION =
        "P0PersistenceContract.Contract.ParcelOwnerLink";
    private static final String DOCUMENT_ILI_CLASS = "P0PersistenceContract.Contract.Document";
    private static final String PERSON_ILI_CLASS = "P0PersistenceContract.Contract.Person";
    private static final String JOURNEY_ILI_CLASS = "P0PersistenceContract.Contract.Journey";
    private static final String STATION_ILI_CLASS = "P0PersistenceContract.Contract.Station";
    private static final String PARCEL_ILI_CLASS = "P0PersistenceContract.Contract.Parcel";
    private static final String BUILDING_ILI_CLASS = "P0PersistenceContract.Contract.Building";
    private static final String COMPONENT_ILI_CLASS = "P0PersistenceContract.Contract.Component";
    private static final String JOURNEY_LINK_ILI_CLASS =
        "P0PersistenceContract.Contract.JourneyLink";
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration DB_READY_TIMEOUT = Duration.ofMinutes(3);

    private final ObjectMapper objectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @TempDir
    Path tempDir;

    private boolean contractTestRequired() {
        return Boolean.parseBoolean(System.getProperty("contractTestRequired", "false"));
    }

    private String contractJdbcUrl() {
        return System.getProperty("contractJdbcUrl",
            "jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret");
    }

    private Path ili2pgHome() {
        String configured = System.getProperty("ili2pgHome");
        Path configuredHome = configured == null || configured.isBlank() ? null : Path.of(configured);
        ExternalToolStatus status = new VerificationEnvironmentDetector().detectIli2pg(configuredHome);
        if (!status.available()) {
            failInfrastructure("ili2pg home not available: " + status.diagnostic()
                + " (override with -Pili2pgHome=... or ILI2PG_HOME)");
        }
        return Path.of(status.resolvedPath());
    }

    @Test
    void generatedAppPersistsAgainstRealIli2pgPostgresSchema() throws Exception {
        boolean required = contractTestRequired();
        requireGrailsCli(required);
        requireDockerCompose(required);
        Path ili2pgHome = ili2pgHome();
        ensurePostgresDatabase(required);

        Files.createDirectories(REPORT_DIR);
        cleanStaleReports();
        writeEnvironmentReport();

        String schemaName = "p0ct_" + Long.toUnsignedString(System.nanoTime(), 36)
            .toLowerCase(Locale.ROOT);
        Path generatedAppPath = null;
        String integrationOutput = null;
        ModelMetadata metadata = null;

        try (Connection connection = DriverManager.getConnection(contractJdbcUrl())) {
            dropSchema(connection, schemaName);

            runIli2pgImport(ili2pgHome, schemaName);

            // Setup für den Optimistic-Locking-Vertrag (Test 8): version-Spalte
            // wie in der etablierten E2E-Fixture. Die Spalte wird über JDBC angelegt
            // und über t_ili2db_attrname dem physischen Reader gemeldet, damit die
            // generierte Domain GORM-Optimistic-Locking aktiviert.
            addVersionColumn(connection, schemaName, "contract_document");
            insertVersionAttrNameRow(connection, schemaName);

            MetadataReader reader = new MetadataReader(
                connection, MODEL_FILE.toFile(), schemaName, MODEL_REPOSITORIES);
            MetadataReadResult readResult = reader.readMetadataResult(
                MODEL_NAME, MetadataMergePolicy.STRICT);
            metadata = readResult.metadata();

            writeMetadataDiagnosticsReport(readResult.diagnostics());
            assertThat(readResult.diagnostics().stream()
                .anyMatch(ch.interlis.generator.metadata.merge.MergeDiagnostic::isBlocking))
                .as("STRICT metadata merge must not produce blocking diagnostics:\n%s",
                    readResult.diagnostics())
                .isFalse();
            assertThat(readResult.modelSelection().includedModelNames())
                .contains(MODEL_NAME);

            GenerationConfig config = GenerationConfig.builder(
                tempDir.resolve(APP_NAME), BASE_PACKAGE)
                .domainPackage(DOMAIN_PACKAGE)
                .controllerPackage(BASE_PACKAGE)
                .enumPackage(ENUM_PACKAGE)
                .jdbcUrl(contractJdbcUrl())
                .schema(schemaName)
                .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
                .mapEditor(GenerationConfig.MAP_EDITOR_NONE)
                .geometryEnabled(false)
                .build();
            Path appDir = config.getOutputDir();

            generatedAppPath = createGrailsApp(appDir);
            new GrailsTemplateOverlayInstaller().install(appDir, config);
            new GrailsCrudGenerator().generate(metadata, config);
            generateScaffolding(appDir, metadata, config);
            patchDataSource(appDir, schemaName);

            TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
            GrailsRelationshipMapper mapper =
                GrailsRelationshipMapper.forMetadata(metadata, config, registry);
            GrailsAssociationPlanner planner =
                GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);
            GrailsInverseRelationshipPlanner inversePlanner =
                GrailsInverseRelationshipPlanner.forMetadata(metadata, config, registry, mapper);

            writeGeneratedDomainSummary(metadata, registry, mapper, planner, appDir);
            writeDatabaseMappingSummary(connection, schemaName, metadata, registry, mapper, planner);

            String spec = renderSpec(metadata, config, registry, mapper, planner, inversePlanner,
                schemaName);
            Files.createDirectories(appDir.resolve(SPEC_PATH).getParent());
            Files.writeString(appDir.resolve(SPEC_PATH), spec, StandardCharsets.UTF_8);

            integrationOutput = runIntegrationTests(appDir);

            verifyNoJoinTable(connection, schemaName);
        } catch (AssertionError | RuntimeException | IOException e) {
            if (generatedAppPath != null) {
                writeFailureArtifacts(generatedAppPath, integrationOutput, e);
            }
            throw e;
        } finally {
            try (Connection connection = DriverManager.getConnection(contractJdbcUrl())) {
                dropSchema(connection, schemaName);
            }
            writeGeneratedAppPathReport(generatedAppPath);
            writeIntegrationOutputReport(integrationOutput);
        }
    }

    // ------------------------------------------------------------------
    // Infrastruktur
    // ------------------------------------------------------------------

    private void requireGrailsCli(boolean required) {
        ExternalToolStatus status = new VerificationEnvironmentDetector().detectGrails(new CommandRunner());
        if (!status.available()) {
            failInfrastructure("grails CLI not available in PATH: " + status.diagnostic());
        }
    }

    private void requireDockerCompose(boolean required) {
        ExternalToolStatus status = new VerificationEnvironmentDetector().detectDockerCompose(new CommandRunner());
        if (!status.available()) {
            failInfrastructure("docker compose not available: " + status.diagnostic());
        }
    }

    private void ensurePostgresDatabase(boolean required) throws IOException, InterruptedException {
        CommandResult result = runCommand(Path.of(".").toAbsolutePath().normalize(),
            List.of("docker", "compose", "up", "-d", "edit-db"), Duration.ofMinutes(5));
        if (result.exitCode() != 0) {
            failInfrastructure("could not start docker-compose edit-db PostgreSQL:\n"
                + result.output());
        }
        Throwable lastError = null;
        long deadline = System.currentTimeMillis() + DB_READY_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = DriverManager.getConnection(contractJdbcUrl())) {
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("SELECT 1")) {
                    if (rs.next()) {
                        return;
                    }
                }
            } catch (Exception e) {
                lastError = e;
            }
            Thread.sleep(1000);
        }
        failInfrastructure("PostgreSQL not reachable at " + contractJdbcUrl()
            + " within " + DB_READY_TIMEOUT + (required ? "" : " (set contractTestRequired=false to skip)")
            + (lastError == null ? "" : ": " + lastError.getMessage()));
    }

    private void failInfrastructure(String message) {
        if (contractTestRequired()) {
            throw new AssertionError("FAILED_INFRASTRUCTURE Contract test required but infrastructure missing: "
                + message);
        }
        throw new TestAbortedException("SKIPPED_INFRASTRUCTURE Contract test skipped: " + message);
    }

    // ------------------------------------------------------------------
    // ili2pg und Schema
    // ------------------------------------------------------------------

    private void runIli2pgImport(Path ili2pgHome, String schemaName)
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
            "--modeldir", String.join(";", MODEL_REPOSITORIES),
            "--models", MODEL_NAME,
            "--dbschema", schemaName,
            "--schemaimport"
        ));

        CommandResult result = runCommand(Path.of("."), command, PROCESS_TIMEOUT);
        if (result.exitCode() != 0) {
            throw new IOException("ili2pg import failed for " + MODEL_NAME + " in schema "
                + schemaName + " (exit " + result.exitCode() + "):\n" + result.output());
        }
    }

    private void addVersionColumn(Connection connection, String schemaName, String tableName)
        throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + quote(schemaName) + "." + quote(tableName)
                + " ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0");
        }
    }

    private void insertVersionAttrNameRow(Connection connection, String schemaName) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + quote(schemaName) + ".t_ili2db_attrname "
                + "(iliname, sqlname, colowner, target) VALUES ("
                + "'P0PersistenceContract.Contract.Document.version', 'version', "
                + "'contract_document', NULL)");
        }
    }

    private void dropSchema(Connection connection, String schemaName) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + quote(schemaName) + " CASCADE");
        }
    }

    private void verifyNoJoinTable(Connection connection, String schemaName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                 "SELECT table_name FROM information_schema.tables "
                     + "WHERE table_schema = '" + schemaName.replace("'", "''") + "' "
                     + "AND table_name LIKE '%component%'")) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            assertThat(tables)
                .as("composition must not create a join table; found %s", tables)
                .containsExactly("contract_component");
        }
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    // ------------------------------------------------------------------
    // Grails-App
    // ------------------------------------------------------------------

    private Path createGrailsApp(Path appDir) throws IOException, InterruptedException {
        CommandResult result = runCommand(tempDir, List.of("grails", "create-app", APP_NAME),
            PROCESS_TIMEOUT);
        if (result.exitCode() != 0) {
            throw new IOException("grails create-app failed:\n" + result.output());
        }
        appDir.resolve("gradlew").toFile().setExecutable(true);
        appDir.resolve("grailsw").toFile().setExecutable(true);
        if (!Files.isRegularFile(appDir.resolve("grailsw"))) {
            throw new IOException("grails create-app did not produce " + appDir);
        }
        RuntimeApiTestSupport.installRuntimePluginDependency(appDir);
        return appDir;
    }

    private void generateScaffolding(Path appDir, ModelMetadata metadata, GenerationConfig config)
        throws IOException, InterruptedException {
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        List<String> classNames = metadata.getAllClasses().stream()
            .filter(classMetadata -> !classMetadata.isAbstract())
            .sorted(Comparator.comparing(ClassMetadata::getName,
                Comparator.nullsLast(String::compareTo)))
            .map(classMetadata -> DOMAIN_PACKAGE + "." + registry.className(classMetadata))
            .toList();
        for (String domainClass : classNames) {
            CommandResult result = runCommand(appDir,
                List.of("./grailsw", "generate-all", domainClass), PROCESS_TIMEOUT);
            if (result.exitCode() != 0) {
                throw new IOException("grailsw generate-all failed for " + domainClass
                    + ":\n" + result.output());
            }
        }
        CommandResult result = runCommand(appDir, List.of("./gradlew", "compileGroovy"),
            PROCESS_TIMEOUT);
        if (result.exitCode() != 0) {
            throw new IOException("compileGroovy failed:\n" + result.output());
        }
    }

    private void patchDataSource(Path appDir, String schemaName) throws IOException {
        String url = contractJdbcUrl();
        if (!url.contains("currentSchema=")) {
            char separator = url.contains("?") ? '&' : '?';
            url = url + separator + "currentSchema=" + schemaName;
        }
        Path applicationYaml = appDir.resolve("grails-app/conf/application.yml");
        String content = Files.readString(applicationYaml)
            .replace("dbCreate: \"update\"", "dbCreate: \"none\"");
        Files.writeString(applicationYaml, content);
        Files.writeString(appDir.resolve("grails-app/conf/application-test.yml"), """
            environments:
                test:
                    dataSource:
                        url: %s
                        username: postgres
                        password: secret
                        dbCreate: none
                        driverClassName: org.postgresql.Driver
                    hibernate:
                        dialect: org.hibernate.dialect.PostgreSQLDialect
                        default_schema: %s
            """.formatted(url, schemaName));
    }

    private String runIntegrationTests(Path appDir) throws IOException, InterruptedException {
        CommandResult result = runCommand(appDir,
            List.of("./gradlew", "integrationTest", "--tests",
                "com.example.P0PostgresPersistenceContractSpec", "--no-daemon"),
            PROCESS_TIMEOUT);
        if (result.exitCode() != 0) {
            throw new IOException("integrationTest failed (exit " + result.exitCode()
                + "):\n" + result.output());
        }
        return result.output();
    }

    private final CommandRunner commandRunner = new CommandRunner();

    private CommandResult runCommand(Path workingDir, List<String> command, Duration timeout)
        throws IOException, InterruptedException {
        return commandRunner.run(workingDir, command, timeout);
    }

    // ------------------------------------------------------------------
    // Reports
    // ------------------------------------------------------------------

    private void cleanStaleReports() throws IOException {
        if (!Files.isDirectory(REPORT_DIR)) {
            return;
        }
        try (var files = Files.list(REPORT_DIR)) {
            files.filter(path -> path.getFileName().toString().startsWith("failure-"))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best effort
                    }
                });
        }
    }

    private void writeEnvironmentReport() throws IOException {
        Files.writeString(REPORT_DIR.resolve("environment.txt"), """
            os: %s
            java: %s
            grails: %s
            ili2pg: %s
            jdbc: %s
            contractTestRequired: %s
            """.formatted(
            System.getProperty("os.name"),
            System.getProperty("java.version"),
            System.getProperty("grails.version", "7.0.6 (CLI)"),
            System.getProperty("ili2pgHome", "unset"),
            redactJdbc(contractJdbcUrl()),
            contractTestRequired()
        ));
    }

    private String redactJdbc(String jdbcUrl) {
        return jdbcUrl.replaceAll("password=[^&]*", "password=***");
    }

    private void writeGeneratedAppPathReport(Path generatedAppPath) throws IOException {
        Files.writeString(REPORT_DIR.resolve("generated-app-path.txt"),
            generatedAppPath == null
                ? "no app generated\n"
                : generatedAppPath + "\n"
                    + "Note: the temporary app is kept for diagnosis on failure.\n");
    }

    private void writeIntegrationOutputReport(String output) throws IOException {
        Files.writeString(REPORT_DIR.resolve("integration-test-output.log"),
            output == null ? "no integration test output\n" : output);
    }

    private void writeMetadataDiagnosticsReport(List<MergeDiagnostic> diagnostics)
        throws IOException {
        List<Map<String, Object>> entries = diagnostics.stream()
            .map(diagnostic -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("severity", diagnostic.severity().name());
                entry.put("code", diagnostic.code().name());
                entry.put("message", diagnostic.message());
                entry.put("semanticElement", diagnostic.semanticElement());
                entry.put("physicalElement", diagnostic.physicalElement());
                entry.put("details", diagnostic.details());
                return entry;
            })
            .toList();
        Files.writeString(REPORT_DIR.resolve("metadata-diagnostics.json"),
            objectMapper.writeValueAsString(entries));
    }

    private void writeGeneratedDomainSummary(ModelMetadata metadata,
                                             TargetNameRegistry registry,
                                             GrailsRelationshipMapper mapper,
                                             GrailsAssociationPlanner planner,
                                             Path appDir) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Generated Domain Summary\n");
        for (ClassMetadata classMetadata : metadata.getAllClasses().stream()
            .sorted(Comparator.comparing(ClassMetadata::getName,
                Comparator.nullsLast(String::compareTo)))
            .toList()) {
            if (!mapper.shouldGenerate(classMetadata)) {
                continue;
            }
            GrailsRelationshipMapper.DomainMapping mapping = mapper.map(classMetadata);
            lines.add("## " + classMetadata.getName());
            lines.add("- domain: " + DOMAIN_PACKAGE + "." + registry.className(classMetadata));
            lines.add("- table: " + classMetadata.getTableName());
            for (GrailsRelationshipMapper.DomainProperty property : mapping.properties()) {
                lines.add("- property " + property.name() + ": " + property.type()
                    + " (column " + property.columnName() + ")");
            }
            for (GrailsRelationshipMapper.PersistentCollection collection : mapping.collections()) {
                lines.add("- collection " + collection.name() + ": "
                    + collection.elementType() + " mappedBy " + collection.mappedByProperty());
            }
        }
        Files.writeString(REPORT_DIR.resolve("generated-domain-summary.md"),
            String.join("\n", lines) + "\n");
    }

    private void writeDatabaseMappingSummary(Connection connection,
                                             String schemaName,
                                             ModelMetadata metadata,
                                             TargetNameRegistry registry,
                                             GrailsRelationshipMapper mapper,
                                             GrailsAssociationPlanner planner) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("# Database Mapping Summary\n");
        lines.add("| ILI class | DB table | Domain class | Property | Physical column | "
            + "Relationship kind | hasMany | mappedBy | belongsTo |");
        lines.add("|---|---|---|---|---|---|---|---|---|");

        for (ClassMetadata classMetadata : metadata.getAllClasses().stream()
            .sorted(Comparator.comparing(ClassMetadata::getName,
                Comparator.nullsLast(String::compareTo)))
            .toList()) {
            if (!mapper.shouldGenerate(classMetadata)) {
                continue;
            }
            GrailsRelationshipMapper.DomainMapping mapping = mapper.map(classMetadata);
            String domainClass = registry.className(classMetadata);
            Map<String, String> belongsToByProperty = mapping.belongsTo().stream()
                .collect(Collectors.toMap(
                    GrailsRelationshipMapper.DomainOwnership::name,
                    GrailsRelationshipMapper.DomainOwnership::type,
                    (left, right) -> left,
                    LinkedHashMap::new));
            for (GrailsRelationshipMapper.DomainProperty property : mapping.properties()) {
                String kind = property.relationship() != null
                    ? String.valueOf(property.relationship().getSemanticKind())
                    : "-";
                lines.add("| " + classMetadata.getName() + " | " + classMetadata.getTableName()
                    + " | " + domainClass + " | " + property.name() + " | "
                    + property.columnName() + " | " + kind + " | - | - | "
                    + belongsToByProperty.getOrDefault(property.name(), "-") + " |");
            }
            for (GrailsRelationshipMapper.PersistentCollection collection : mapping.collections()) {
                lines.add("| " + classMetadata.getName() + " | " + classMetadata.getTableName()
                    + " | " + domainClass + " | " + collection.name() + " | - | "
                    + String.valueOf(collection.relationship().getSemanticKind()) + " | yes | "
                    + collection.mappedByProperty() + " | - |");
            }
        }
        lines.add("");
        lines.add("## FK constraints in schema " + schemaName + "\n");
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                 "SELECT tc.table_name, kcu.column_name, ccu.table_name AS references_table "
                     + "FROM information_schema.table_constraints tc "
                     + "JOIN information_schema.key_column_usage kcu "
                     + "  ON tc.constraint_name = kcu.constraint_name "
                     + "JOIN information_schema.constraint_column_usage ccu "
                     + "  ON tc.constraint_name = ccu.constraint_name "
                     + "WHERE tc.constraint_type = 'FOREIGN KEY' "
                     + "AND tc.table_schema = '" + schemaName.replace("'", "''") + "' "
                     + "ORDER BY tc.table_name, kcu.column_name")) {
            while (rs.next()) {
                lines.add("- " + rs.getString(1) + "." + rs.getString(2)
                    + " -> " + rs.getString(3));
            }
        }
        Files.writeString(REPORT_DIR.resolve("database-mapping-summary.md"),
            String.join("\n", lines) + "\n");
    }

    private void writeFailureArtifacts(Path appDir, String integrationOutput, Throwable failure)
        throws IOException {
        Files.writeString(REPORT_DIR.resolve("failure-diagnosis.txt"),
            "Failure: " + failure + "\n\nApp kept at: " + appDir + "\n");
        if (integrationOutput != null) {
            writeIntegrationOutputReport(integrationOutput);
        }
        Path testResults = appDir.resolve("build/test-results");
        if (Files.isDirectory(testResults)) {
            try (var files = Files.walk(testResults)) {
                files.filter(path -> path.getFileName().toString().startsWith("TEST-"))
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("<failure");
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.copy(path, REPORT_DIR.resolve(
                                "failure-" + path.getFileName().toString()),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException ignored) {
                            // best effort
                        }
                    });
            }
        }
    }

    // ------------------------------------------------------------------
    // Spock-Spec-Rendering
    // ------------------------------------------------------------------

    private String renderSpec(ModelMetadata metadata,
                              GenerationConfig config,
                              TargetNameRegistry registry,
                              GrailsRelationshipMapper mapper,
                              GrailsAssociationPlanner planner,
                              GrailsInverseRelationshipPlanner inversePlanner,
                              String schemaName) {
        Map<String, String> classNames = new LinkedHashMap<>();
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            if (mapper.shouldGenerate(classMetadata)) {
                classNames.put(classMetadata.getName(), registry.className(classMetadata));
            }
        }

        Map<String, Map<String, String>> propertyNames = new LinkedHashMap<>();
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            Map<String, String> names = new LinkedHashMap<>();
            for (GrailsRelationshipMapper.DomainProperty property
                : mapper.map(classMetadata).properties()) {
                if (property.attribute() != null) {
                    names.put(property.attribute().getName(), property.name());
                }
            }
            propertyNames.put(classMetadata.getName(), names);
        }

        String journeyLinkContext = quickLinkContextId(planner, JOURNEY_LINK_ASSOCIATION);
        String journeyLinkEditableRole = quickLinkEditableRole(planner, JOURNEY_LINK_ASSOCIATION);
        String parcelOwnerContext = quickLinkContextId(planner, PARCEL_OWNER_LINK_ASSOCIATION);
        String parcelOwnerEditableRole =
            quickLinkEditableRole(planner, PARCEL_OWNER_LINK_ASSOCIATION);

        GrailsInverseRelationshipPlan documentPlan = inversePlan(inversePlanner,
            PERSON_ILI_CLASS, DOCUMENT_ILI_CLASS, "P0PersistenceContract.Contract.Document_Owner");
        GrailsInverseRelationshipPlan departurePlan = inversePlan(inversePlanner,
            STATION_ILI_CLASS, JOURNEY_ILI_CLASS,
            "P0PersistenceContract.Contract.Journey_DepartureStation");
        GrailsInverseRelationshipPlan arrivalPlan = inversePlan(inversePlanner,
            STATION_ILI_CLASS, JOURNEY_ILI_CLASS,
            "P0PersistenceContract.Contract.Journey_ArrivalStation");

        GrailsRelationshipMapper.DomainMapping buildingMapping = mapper.map(
            metadata.getClass(BUILDING_ILI_CLASS));
        GrailsRelationshipMapper.PersistentCollection components =
            buildingMapping.collections().stream()
                .filter(collection -> collection.relationship() != null)
                .filter(collection -> COMPONENT_ILI_CLASS.equals(
                    collection.relationship().getTargetClass()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "composition collection for Building not resolved"));
        GrailsRelationshipMapper.DomainMapping componentMapping = mapper.map(
            metadata.getClass(COMPONENT_ILI_CLASS));

        Map<String, String> journeyProps = propertyNames.get(JOURNEY_ILI_CLASS);
        Map<String, String> documentProps = propertyNames.get(DOCUMENT_ILI_CLASS);
        Map<String, String> stationProps = propertyNames.get(STATION_ILI_CLASS);
        Map<String, String> personProps = propertyNames.get(PERSON_ILI_CLASS);
        Map<String, String> parcelProps = propertyNames.get(PARCEL_ILI_CLASS);
        Map<String, String> buildingProps = propertyNames.get(BUILDING_ILI_CLASS);
        Map<String, String> componentProps = propertyNames.get(COMPONENT_ILI_CLASS);
        Map<String, String> journeyLinkProps = propertyNames.get(JOURNEY_LINK_ILI_CLASS);
        Map<String, String> componentCollectionChildProp = new LinkedHashMap<>();
        componentMapping.properties().stream()
            .filter(property -> property.columnName() != null)
            .findFirst()
            .ifPresent(property -> componentCollectionChildProp.put("fk", property.name()));

        String journeyDomain = classNames.get(JOURNEY_ILI_CLASS);
        String stationDomain = classNames.get(STATION_ILI_CLASS);
        String documentDomain = classNames.get(DOCUMENT_ILI_CLASS);
        String personDomain = classNames.get(PERSON_ILI_CLASS);
        String parcelDomain = classNames.get(PARCEL_ILI_CLASS);
        String buildingDomain = classNames.get(BUILDING_ILI_CLASS);
        String componentDomain = classNames.get(COMPONENT_ILI_CLASS);
        String journeyLinkDomain = classNames.get(JOURNEY_LINK_ILI_CLASS);
        String parcelOwnerLinkDomain = classNames.get(
            "P0PersistenceContract.Contract.ParcelOwnerLink");

        String spec = """
            package com.example

            import ch.interlis.generator.grails.runtime.InterlisAssociationCommandService
            import ch.interlis.generator.grails.runtime.InterlisInverseRelationshipCommandService
            import ch.interlis.generator.grails.runtime.InterlisInverseRelationshipQueryService
            import ch.interlis.generator.grails.runtime.api.command.CommandCode
            import %1$s.%2$s
            import %1$s.%3$s
            import %1$s.%4$s
            import %1$s.%5$s
            import %1$s.%6$s
            import %1$s.%7$s
            import %1$s.%8$s
            import %1$s.%9$s
            import %1$s.%27$s
            import grails.gorm.transactions.Rollback
            import grails.testing.mixin.integration.Integration
            import spock.lang.Specification

            /* Generiert von GrailsPostgresContractTest: alle Namen stammen aus
               TargetNameRegistry / GrailsRelationshipMapper / GrailsAssociationPlanner. */
            @Integration
            @Rollback
            class P0PostgresPersistenceContractSpec extends Specification {

                InterlisAssociationCommandService interlisAssociationCommandService
                InterlisInverseRelationshipCommandService interlisInverseRelationshipCommandService
                InterlisInverseRelationshipQueryService interlisInverseRelationshipQueryService
                def sessionFactory

                private static final String JOURNEY_LINK_CONTEXT = '%10$s'
                private static final String JOURNEY_LINK_EDITABLE_ROLE = '%11$s'
                private static final String PARCEL_OWNER_LINK_CONTEXT = '%12$s'
                private static final String PARCEL_OWNER_EDITABLE_ROLE = '%13$s'
                private static final String DOCUMENT_OWNER_RELATIONSHIP = '%14$s'
                private static final String DEPARTURE_RELATIONSHIP = '%15$s'
                private static final String ARRIVAL_RELATIONSHIP = '%16$s'
                private static final String BUILDING_COMPONENTS_COLLECTION = '%17$s'
                private static final String COMPONENT_CHILD_FK_PROPERTY = '%18$s'

                private static final Map<String, String> JOURNEY_PROPS = %19$s
                private static final Map<String, String> STATION_PROPS = %20$s
                private static final Map<String, String> DOCUMENT_PROPS = %21$s
                private static final Map<String, String> PERSON_PROPS = %22$s
                private static final Map<String, String> PARCEL_PROPS = %23$s
                private static final Map<String, String> BUILDING_PROPS = %24$s
                private static final Map<String, String> COMPONENT_PROPS = %25$s
                private static final Map<String, String> JOURNEY_LINK_PROPS = %26$s

                def "normal direct reference persists and reloads without synthetic hasMany"() {
                    given:
                    def person = new %5$s((PERSON_PROPS['Name']): 'Anna').save(failOnError: true, flush: true)
                    def document = new %4$s((DOCUMENT_PROPS['Name']): 'Rechnung',
                        (DOCUMENT_PROPS['Owner']): person).save(failOnError: true, flush: true)
                    sessionFactory.currentSession.flush()
                    sessionFactory.currentSession.clear()

                    when:
                    def reloaded = %4$s.get(document.id)
                    def page = interlisInverseRelationshipQueryService.page(
                        %5$s, person.id, DOCUMENT_OWNER_RELATIONSHIP, null, 10, 0)

                    then:
                    reloaded[DOCUMENT_PROPS['Owner']].id == person.id
                    !%5$s.declaredFields*.name.contains('hasMany')
                    page.total == 1
                    page.rows[0].id == document.id.toString()
                }

                def "two foreign keys to the same target stay separated"() {
                    given:
                    def departure = new %3$s((STATION_PROPS['Name']): 'Bern').save(failOnError: true, flush: true)
                    def arrival = new %3$s((STATION_PROPS['Name']): 'Zuerich').save(failOnError: true, flush: true)
                    def journey = new %2$s((JOURNEY_PROPS['Name']): 'J1',
                        (JOURNEY_PROPS['DepartureStation']): departure,
                        (JOURNEY_PROPS['ArrivalStation']): arrival).save(failOnError: true, flush: true)
                    sessionFactory.currentSession.flush()
                    sessionFactory.currentSession.clear()

                    when:
                    def reloaded = %2$s.get(journey.id)
                    def departurePage = interlisInverseRelationshipQueryService.page(
                        %3$s, departure.id, DEPARTURE_RELATIONSHIP, null, 10, 0)
                    def arrivalPage = interlisInverseRelationshipQueryService.page(
                        %3$s, arrival.id, ARRIVAL_RELATIONSHIP, null, 10, 0)

                    then:
                    reloaded[JOURNEY_PROPS['DepartureStation']].id == departure.id
                    reloaded[JOURNEY_PROPS['ArrivalStation']].id == arrival.id
                    departurePage.total == 1
                    departurePage.rows[0].id == journey.id.toString()
                    arrivalPage.total == 1
                    arrivalPage.rows[0].id == journey.id.toString()

                    when:
                    def wrongSide = interlisInverseRelationshipQueryService.page(
                        %3$s, departure.id, ARRIVAL_RELATIONSHIP, null, 10, 0)

                    then:
                    wrongSide.total == 0
                }

                def "quick link create duplicate and delete via runtime service"() {
                    given:
                    def person = new %5$s((PERSON_PROPS['Name']): 'Anna').save(failOnError: true, flush: true)
                    def parcel = new %6$s((PARCEL_PROPS['Number']): 'P-1').save(failOnError: true, flush: true)
                    def journey = new %2$s((JOURNEY_PROPS['Name']): 'J1').save(failOnError: true, flush: true)

                    when:
                    def created = interlisAssociationCommandService.createQuickLink(
                        %6$s, parcel.id, JOURNEY_LINK_CONTEXT, JOURNEY_LINK_EDITABLE_ROLE, journey.id)
                    def duplicate = interlisAssociationCommandService.createQuickLink(
                        %6$s, parcel.id, JOURNEY_LINK_CONTEXT, JOURNEY_LINK_EDITABLE_ROLE, journey.id)

                    then:
                    created.success() == true
                    created.httpStatus() == 201
                    created.associationId != null
                    duplicate.success() == false
                    duplicate.code() == ch.interlis.generator.grails.runtime.api.command.CommandCode.DUPLICATE_LINK

                    when:
                    def link = %9$s.get(created.associationId as Long)
                    def deleted = interlisAssociationCommandService.deleteLink(
                        %6$s, parcel.id, JOURNEY_LINK_CONTEXT, created.associationId)

                    then:
                    link != null
                    link[JOURNEY_LINK_PROPS['LinkedJourney']].id == parcel.id
                    link[JOURNEY_LINK_PROPS['LinkedParcel']].id == journey.id
                    deleted.success() == true
                    deleted.httpStatus() == 204
                    %9$s.get(created.associationId as Long) == null
                    %6$s.get(parcel.id) != null
                    %2$s.get(journey.id) != null
                }

                def "ownership mismatch blocks foreign deletes"() {
                    given:
                    def parcel = new %6$s((PARCEL_PROPS['Number']): 'P-1').save(failOnError: true, flush: true)
                    def journeyA = new %2$s((JOURNEY_PROPS['Name']): 'J-A').save(failOnError: true, flush: true)
                    def parcelB = new %6$s((PARCEL_PROPS['Number']): 'P-2').save(failOnError: true, flush: true)
                    def created = interlisAssociationCommandService.createQuickLink(
                        %6$s, parcel.id, JOURNEY_LINK_CONTEXT, JOURNEY_LINK_EDITABLE_ROLE, journeyA.id)

                    when:
                    def foreignDelete = interlisAssociationCommandService.deleteLink(
                        %6$s, parcelB.id, JOURNEY_LINK_CONTEXT, created.associationId)

                    then:
                    created.success() == true
                    foreignDelete.success() == false
                    foreignDelete.code() == ch.interlis.generator.grails.runtime.api.command.CommandCode.OWNERSHIP_MISMATCH
                    %9$s.get(created.associationId as Long) != null
                }

                def "inverse assign and reassign require confirmation"() {
                    given:
                    def ownerA = new %5$s((PERSON_PROPS['Name']): 'Anna').save(failOnError: true, flush: true)
                    def ownerB = new %5$s((PERSON_PROPS['Name']): 'Bea').save(failOnError: true, flush: true)
                    def related = new %4$s((DOCUMENT_PROPS['Name']): 'Rechnung').save(failOnError: true, flush: true)

                    when:
                    def assigned = interlisInverseRelationshipCommandService.assign(
                        %5$s, ownerA.id, DOCUMENT_OWNER_RELATIONSHIP, related.id, false)
                    sessionFactory.currentSession.clear()
                    def firstReload = %4$s.get(related.id)

                    then:
                    assigned.success() == true
                    assigned.code() == ch.interlis.generator.grails.runtime.api.command.CommandCode.ASSIGNED
                    firstReload[DOCUMENT_PROPS['Owner']].id == ownerA.id

                    when:
                    def needsConfirmation = interlisInverseRelationshipCommandService.assign(
                        %5$s, ownerB.id, DOCUMENT_OWNER_RELATIONSHIP, related.id, false)

                    then:
                    needsConfirmation.success() == false
                    needsConfirmation.code() == ch.interlis.generator.grails.runtime.api.command.CommandCode.REASSIGNMENT_CONFIRMATION_REQUIRED

                    when:
                    def reassigned = interlisInverseRelationshipCommandService.assign(
                        %5$s, ownerB.id, DOCUMENT_OWNER_RELATIONSHIP, related.id, true)
                    sessionFactory.currentSession.clear()
                    def secondReload = %4$s.get(related.id)

                    then:
                    reassigned.success() == true
                    reassigned.code() == ch.interlis.generator.grails.runtime.api.command.CommandCode.REASSIGNED
                    secondReload[DOCUMENT_PROPS['Owner']].id == ownerB.id
                }

                def "invalid inputs fail without partial rows"() {
                    given:
                    def parcel = new %6$s((PARCEL_PROPS['Number']): 'P-1').save(failOnError: true, flush: true)

                    when:
                    def unknownTarget = interlisAssociationCommandService.createQuickLink(
                        %6$s, parcel.id, JOURNEY_LINK_CONTEXT, JOURNEY_LINK_EDITABLE_ROLE, 999999999L)

                    then:
                    unknownTarget.success() == false
                    unknownTarget.code() == ch.interlis.generator.grails.runtime.api.command.CommandCode.TARGET_NOT_FOUND
                    %9$s.count() == 0

                    when:
                    def unknownOwner = interlisInverseRelationshipCommandService.assign(
                        %5$s, 999999999L, DOCUMENT_OWNER_RELATIONSHIP, 999999999L, false)

                    then:
                    unknownOwner.success() == false
                    unknownOwner.code() == ch.interlis.generator.grails.runtime.api.command.CommandCode.OWNER_NOT_FOUND
                    %4$s.count() == 0
                }

                def "cardinality maximum is enforced without persisting the invalid link"() {
                    given:
                    def parcel = new %6$s((PARCEL_PROPS['Number']): 'P-1').save(failOnError: true, flush: true)
                    def first = new %5$s((PERSON_PROPS['Name']): 'Anna').save(failOnError: true, flush: true)
                    def second = new %5$s((PERSON_PROPS['Name']): 'Bea').save(failOnError: true, flush: true)
                    def third = new %5$s((PERSON_PROPS['Name']): 'Cedric').save(failOnError: true, flush: true)

                    when:
                    def firstLink = interlisAssociationCommandService.createQuickLink(
                        %6$s, parcel.id, PARCEL_OWNER_LINK_CONTEXT, PARCEL_OWNER_EDITABLE_ROLE, first.id)
                    def secondLink = interlisAssociationCommandService.createQuickLink(
                        %6$s, parcel.id, PARCEL_OWNER_LINK_CONTEXT, PARCEL_OWNER_EDITABLE_ROLE, second.id)
                    def tooMany = interlisAssociationCommandService.createQuickLink(
                        %6$s, parcel.id, PARCEL_OWNER_LINK_CONTEXT, PARCEL_OWNER_EDITABLE_ROLE, third.id)

                    then:
                    firstLink.success() == true
                    secondLink.success() == true
                    tooMany.success() == false
                    tooMany.code() == ch.interlis.generator.grails.runtime.api.command.CommandCode.CARDINALITY_MAX_EXCEEDED
                    %27$s.count() == 2
                }

                def "concurrent modification surfaces the intended error code"() {
                    given:
                    def owner = new %5$s((PERSON_PROPS['Name']): 'Anna').save(failOnError: true, flush: true)
                    def related = new %4$s((DOCUMENT_PROPS['Name']): 'Rechnung').save(failOnError: true, flush: true)
                    def staleVersion = related.version
                    // Simulierte parallele Änderung: Version wird direkt per JDBC erhöht.
                    def sql = sessionFactory.currentSession.connection().prepareStatement(
                        'UPDATE %28$s.%29$s SET version = version + 1 WHERE t_id = ?')
                    sql.setLong(1, related.id as long)
                    sql.executeUpdate()

                    when:
                    def result = interlisInverseRelationshipCommandService.assign(
                        %5$s, owner.id, DOCUMENT_OWNER_RELATIONSHIP, related.id, true)

                    then:
                    result.success() == false
                    result.code() == ch.interlis.generator.grails.runtime.api.command.CommandCode.CONCURRENT_MODIFICATION
                    sessionFactory.currentSession.clear()
                    %4$s.get(related.id)[DOCUMENT_PROPS['Owner']] == null
                }

                def "composition collection persists via child foreign key without join table"() {
                    given:
                    def building = new %7$s((BUILDING_PROPS['Name']): 'Haus A').save(failOnError: true, flush: true)
                    def componentOne = new %8$s((COMPONENT_PROPS['Label']): 'Teil 1').save(failOnError: true, flush: true)
                    def componentTwo = new %8$s((COMPONENT_PROPS['Label']): 'Teil 2').save(failOnError: true, flush: true)

                    when:
                    building."addTo${BUILDING_COMPONENTS_COLLECTION.capitalize()}"(componentOne)
                    building."addTo${BUILDING_COMPONENTS_COLLECTION.capitalize()}"(componentTwo)
                    building.save(failOnError: true, flush: true)
                    sessionFactory.currentSession.flush()
                    sessionFactory.currentSession.clear()
                    def reloaded = %7$s.get(building.id)

                    then:
                    reloaded."$BUILDING_COMPONENTS_COLLECTION".size() == 2
                    reloaded."$BUILDING_COMPONENTS_COLLECTION".collect { it.id }.sort()
                        == [componentOne.id, componentTwo.id].sort()

                    when:
                    def child = %8$s.get(componentOne.id)

                    then:
                    child[COMPONENT_CHILD_FK_PROPERTY].id == building.id
                }
            }
            """.formatted(
            DOMAIN_PACKAGE,
            journeyDomain, stationDomain, documentDomain, personDomain,
            parcelDomain, buildingDomain, componentDomain, journeyLinkDomain,
            journeyLinkContext, journeyLinkEditableRole, parcelOwnerContext,
            parcelOwnerEditableRole,
            documentPlan != null ? documentPlan.collectionPropertyName() : "documents",
            departurePlan != null ? departurePlan.collectionPropertyName() : "departureStations",
            arrivalPlan != null ? arrivalPlan.collectionPropertyName() : "arrivalStations",
            components.name(),
            componentCollectionChildProp.getOrDefault("fk", "buildingId"),
            renderMap(journeyProps), renderMap(stationProps), renderMap(documentProps),
            renderMap(personProps), renderMap(parcelProps), renderMap(buildingProps),
            renderMap(componentProps), renderMap(journeyLinkProps),
            parcelOwnerLinkDomain,
            schemaName,
            "contract_document"
        );
        return spec;
    }

    private String quickLinkContextId(GrailsAssociationPlanner planner, String associationName) {
        return planner.findPlan(associationName).orElseThrow().contexts().stream()
            .filter(context -> context.createMode() == AssociationCreateMode.QUICK)
            .map(GrailsAssociationContextPlan::contextId)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "no QUICK context for " + associationName));
    }

    private String quickLinkEditableRole(GrailsAssociationPlanner planner, String associationName) {
        return planner.findPlan(associationName).orElseThrow().contexts().stream()
            .filter(context -> context.createMode() == AssociationCreateMode.QUICK)
            .flatMap(context -> context.editableRoleNames().stream())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "no editable role for QUICK context of " + associationName));
    }

    private GrailsInverseRelationshipPlan inversePlan(GrailsInverseRelationshipPlanner planner,
                                                      String ownerIliClass,
                                                      String relatedIliClass,
                                                      String relationshipName) {
        return planner.plans().stream()
            .filter(plan -> ownerIliClass.equals(plan.ownerIliClassName()))
            .filter(plan -> relatedIliClass.equals(plan.relatedIliClassName()))
            .filter(plan -> relationshipName.equals(plan.relationshipName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "inverse plan not found for owner " + ownerIliClass
                    + " related " + relatedIliClass + " relationship " + relationshipName));
    }

    private String renderMap(Map<String, String> map) {
        if (map == null) {
            return "[:]";
        }
        return map.entrySet().stream()
            .map(entry -> "'" + entry.getKey().replace("'", "\\'") + "': '"
                + entry.getValue().replace("'", "\\'") + "'")
            .collect(Collectors.joining(", ", "[", "]"));
    }

}

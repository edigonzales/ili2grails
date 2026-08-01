package ch.interlis.generator.grails.verification.contract;

import ch.interlis.generator.grails.verification.corpus.CorpusScenario;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Testseitiges ili2pg-Schema-Fixture: importiert ein Corpus-Szenario in ein
 * temporäres PostgreSQL-Schema und räumt es wieder ab (Spezifikation §30.3).
 */
public final class Ili2pgSchemaFixture implements AutoCloseable {

    private final Connection adminConnection;
    private final Path ili2pgHome;
    private final CorpusScenario scenario;
    private final String schemaName;
    private final CommandRunner commandRunner;
    private final String jdbcUrl;
    private boolean imported;

    private Ili2pgSchemaFixture(Connection adminConnection,
                                Path ili2pgHome,
                                CorpusScenario scenario,
                                String schemaName,
                                CommandRunner commandRunner,
                                String jdbcUrl) {
        this.adminConnection = adminConnection;
        this.ili2pgHome = ili2pgHome;
        this.scenario = scenario;
        this.schemaName = schemaName;
        this.commandRunner = commandRunner;
        this.jdbcUrl = jdbcUrl;
    }

    public static Ili2pgSchemaFixture create(Connection adminConnection,
                                             Path ili2pgHome,
                                             CorpusScenario scenario,
                                             String schemaName,
                                             CommandRunner commandRunner,
                                             String jdbcUrl) throws Exception {
        Ili2pgSchemaFixture fixture = new Ili2pgSchemaFixture(
            adminConnection, ili2pgHome, scenario, schemaName, commandRunner, jdbcUrl);
        fixture.dropSchemaIfExists();
        return fixture;
    }

    public void importSchema() throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = ili2pgHome.resolve("ili2pg-5.5.1.jar")
            + File.pathSeparator
            + ili2pgHome.resolve("libs/*");
        String modelPath = scenario.modelFile().toAbsolutePath().toString();

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
            "--createEnumTabs"
        ));
        // Nur Modelle mit OID benötigen die T_basket-Spalte; bei anderen
        // Modellen würde die NOT-NULL-Spalte bestehende Verträge brechen.
        if (Files.readString(scenario.modelFile()).contains("OID AS")) {
            command.add("--createBasketCol");
        }
        command.addAll(List.of(
            "--modeldir", String.join(";", scenario.repositories() == null
                ? List.of("test-models") : scenario.repositories()),
            "--models", scenario.modelName(),
            "--dbschema", schemaName,
            "--schemaimport"
        ));
        command.add(modelPath);

        CommandResult result = commandRunner.run(Path.of("."), command, Duration.ofMinutes(8));
        if (result.exitCode() != 0) {
            throw new IOException("ili2pg import failed for " + scenario.id() + " in schema "
                + schemaName + " (exit " + result.exitCode() + "):\n" + result.output());
        }
        imported = true;
    }

    public String schemaName() {
        return schemaName;
    }

    public String jdbcUrlWithSchema() {
        String url = jdbcUrl;
        if (!url.contains("currentSchema=")) {
            char separator = url.contains("?") ? '&' : '?';
            url = url + separator + "currentSchema=" + schemaName;
        }
        return url;
    }

    public Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrlWithSchema());
    }

    public void dropSchemaIfExists() throws SQLException {
        try (Statement statement = adminConnection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + quote(schemaName) + " CASCADE");
        }
        imported = false;
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void close() throws Exception {
        dropSchemaIfExists();
    }
}

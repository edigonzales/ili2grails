package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.reader.Ili2dbMetadataReader;
import ch.interlis.generator.reader.ili2db.metrics.CountingConnection;
import ch.interlis.generator.reader.ili2db.metrics.CountingJdbcProxy;
import ch.interlis.generator.reader.ili2db.metrics.JdbcInvocationKind;
import ch.interlis.generator.reader.ili2db.metrics.JdbcInvocationSummary;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Strukturierte Diagnostics an der Reader-Grenze (Spezifikation §15, §17.1).
 */
class Ili2dbReadCoordinatorDiagnosticTest {

    @Test
    void missingRootModelProducesRequestedModelMissingDiagnostic() throws Exception {
        try (Connection connection = DriverManager.getConnection(inMemoryUrl("root_missing"));
             Statement stmt = connection.createStatement()) {
            createIli2dbFixture(stmt, true, false);

            Ili2dbMetadataReader reader = Ili2dbMetadataReader.create(connection, null);
            Ili2dbReadResult result = reader.read(ModelSelection.rootOnly("MissingRoot"));

            assertThat(result.hasFatalDiagnostics()).isTrue();
            assertThat(result.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.REQUESTED_MODEL_MISSING)
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.severity()).isEqualTo(Ili2dbSeverity.FATAL);
                    assertThat(diagnostic.details())
                        .containsEntry("requestedModel", "MissingRoot")
                        .containsEntry("availableModelCount", "1");
                });
            assertThat(result.optionalMetadata()).isEmpty();
        }
    }

    @Test
    void missingDependencyProducesSelectedDependencyMissingWarning() throws Exception {
        try (Connection connection = DriverManager.getConnection(inMemoryUrl("dep_missing"));
             Statement stmt = connection.createStatement()) {
            createIli2dbFixture(stmt, true, false);

            Ili2dbMetadataReader reader = Ili2dbMetadataReader.create(connection, null);
            Ili2dbReadResult result = reader.read(ModelSelection.rootOnly("FixtureModel"));

            // Root vorhanden; die Anfrage enthält eine nicht vorhandene Dependency.
            Ili2dbReadResult dependencyResult = reader.read(
                new ModelSelection("FixtureModel",
                    new java.util.LinkedHashSet<>(List.of("FixtureModel", "NotImportedDependency")),
                    ch.interlis.generator.metadata.selection.ModelSelectionSource.ROOT_ONLY_FALLBACK));

            assertThat(dependencyResult.hasFatalDiagnostics()).isFalse();
            assertThat(dependencyResult.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.SELECTED_DEPENDENCY_MISSING)
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.severity()).isEqualTo(Ili2dbSeverity.WARNING);
                    assertThat(diagnostic.details())
                        .containsEntry("rootModel", "FixtureModel")
                        .containsEntry("missingDependency", "NotImportedDependency");
                });
            assertThat(dependencyResult.optionalMetadata()).isPresent();
            assertThat(result.optionalMetadata()).isPresent();
        }
    }

    @Test
    void missingTablePropIsFatalBecauseClassDiscoveryRequiresIt() throws Exception {
        try (Connection connection = DriverManager.getConnection(inMemoryUrl("table_prop_missing"));
             Statement stmt = connection.createStatement()) {
            createIli2dbFixture(stmt, false, true);

            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            new Ili2dbReadCoordinator().read(
                new Ili2dbReadContext(connection, null,
                    ch.interlis.generator.reader.sql.SqlIdentifierRenderer.from(connection.getMetaData()),
                    ch.interlis.generator.reader.ili2db.schema.DatabaseDialect.H2),
                ModelSelection.rootOnly("FixtureModel"), true);
            // detektieren über den Katalog-Reader, um die Klassifikation direkt zu prüfen
            new ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogReader().detectCapabilities(
                new Ili2dbReadContext(connection, null,
                    ch.interlis.generator.reader.sql.SqlIdentifierRenderer.from(connection.getMetaData()),
                    ch.interlis.generator.reader.ili2db.schema.DatabaseDialect.H2),
                diagnostics);

            assertThat(diagnostics)
                .filteredOn(Ili2dbDiagnostic::isBlocking)
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.REQUIRED_META_TABLE_MISSING)
                .extracting(Ili2dbDiagnostic::physicalElement)
                .contains("t_ili2db_table_prop");
        }
    }

    @Test
    void readMetadataRejectsErrorDiagnostics() throws Exception {
        try (Connection connection = DriverManager.getConnection(inMemoryUrl("strict_error"));
             Statement stmt = connection.createStatement()) {
            createIli2dbFixture(stmt, true, false);
            // Attribute mit unauflösbarem FK-Ziel erzeugen ERROR TARGET_CLASS_UNRESOLVED
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                + "('FixtureModel.Topic.Sample.broken', 'broken_fk', 'sample', 'NoSuchClass')");

            Ili2dbMetadataReader reader = Ili2dbMetadataReader.create(connection, null);
            assertThatThrownBy(() -> reader.readMetadata(fullSelection()))
                .isInstanceOf(Ili2dbReadException.class)
                .hasMessageContaining("TARGET_CLASS_UNRESOLVED");
        }
    }

    @Test
    void readReturnsUsablePartialMetadataForRecoverableErrors() throws Exception {
        try (Connection connection = DriverManager.getConnection(inMemoryUrl("diag_partial"));
             Statement stmt = connection.createStatement()) {
            createIli2dbFixture(stmt, true, false);
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                + "('FixtureModel.Topic.Sample.broken', 'broken_fk', 'sample', 'NoSuchClass')");

            Ili2dbMetadataReader reader = Ili2dbMetadataReader.create(connection, null);
            Ili2dbReadResult result = reader.read(fullSelection());

            assertThat(result.hasErrorDiagnostics()).isTrue();
            assertThat(result.hasFatalDiagnostics()).isFalse();
            assertThat(result.isUsable()).isTrue();
            assertThat(result.optionalMetadata()).isPresent();
            assertThat(result.metadata().getClasses().values())
                .extracting(clazz -> clazz.getName())
                .contains("FixtureModel.Topic.Sample");
        }
    }

    @Test
    void fatalDiagnosticsNeverLeakAsIllegalArgumentException() throws Exception {
        try (Connection connection = DriverManager.getConnection(inMemoryUrl("no_iae"));
             Statement stmt = connection.createStatement()) {
            createIli2dbFixture(stmt, true, false);

            Ili2dbMetadataReader reader = Ili2dbMetadataReader.create(connection, null);
            assertThatThrownBy(() -> reader.readMetadata(ModelSelection.rootOnly("MissingRoot")))
                .isNotInstanceOf(IllegalArgumentException.class)
                .isInstanceOf(Ili2dbReadException.class);
        }
    }

    @Test
    void readerExposesOneDiagnosticReadPathAndContextStaysTechnical() throws Exception {
        assertThat(Ili2dbReadContext.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .contains("connection", "schema", "identifiers", "dialect");
        assertThat(Ili2dbMetadataReader.class.getMethod("read", ModelSelection.class))
            .isNotNull();
        assertThat(java.util.Arrays.stream(Ili2dbMetadataReader.class.getMethods())
            .filter(method -> method.getName().equals("read")))
            .hasSize(1);
    }

    @Test
    void capabilityDetectionScansTablesAndColumnsOnce() throws Exception {
        try (Connection connection = DriverManager.getConnection(inMemoryUrl("single_pass"));
             Statement stmt = connection.createStatement()) {
            createIli2dbFixture(stmt, true, false);

            try (CountingConnection counting = CountingJdbcProxy.wrap(connection)) {
                List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
                new ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogReader().detectCapabilities(
                    new Ili2dbReadContext(counting, null,
                        ch.interlis.generator.reader.sql.SqlIdentifierRenderer.from(connection.getMetaData()),
                        ch.interlis.generator.reader.ili2db.schema.DatabaseDialect.H2),
                    diagnostics);

                JdbcInvocationSummary summary = counting.snapshot();
                assertThat(summary.count(JdbcInvocationKind.METADATA_GET_TABLES))
                    .as("getTables single pass")
                    .isLessThanOrEqualTo(1);
                assertThat(summary.count(JdbcInvocationKind.METADATA_GET_COLUMNS))
                    .as("getColumns single pass")
                    .isLessThanOrEqualTo(1);
            }
        }
    }

    private String inMemoryUrl(String name) {
        return "jdbc:h2:mem:diag_" + name + "_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
    }

    /** Auswahl mit echten Abhängigkeiten (ili2c-Graph); FK-Ziele müssen auflösbar sein. */
    private ModelSelection fullSelection() {
        return new ModelSelection("FixtureModel",
            new java.util.LinkedHashSet<>(List.of("FixtureModel")),
            ch.interlis.generator.metadata.selection.ModelSelectionSource.ILI2C_DEPENDENCY_GRAPH);
    }

    /**
     * @param withModelRow ob t_ili2db_model das FixtureModel enthält
     * @param withoutTableProp ob t_ili2db_table_prop weggelassen werden soll
     */
    private void createIli2dbFixture(Statement stmt, boolean withModelRow, boolean withoutTableProp)
        throws Exception {
        stmt.execute("CREATE TABLE t_ili2db_classname ("
            + "iliname VARCHAR(1024) PRIMARY KEY, sqlname VARCHAR(1024))");
        stmt.execute("CREATE TABLE t_ili2db_attrname ("
            + "iliname VARCHAR(1024), sqlname VARCHAR(1024), colowner VARCHAR(1024), "
            + "target VARCHAR(1024))");
        stmt.execute("CREATE TABLE t_ili2db_settings ("
            + "tag VARCHAR(1024), setting VARCHAR(1024))");
        stmt.execute("CREATE TABLE t_ili2db_inheritance ("
            + "thisclass VARCHAR(1024), baseclass VARCHAR(1024))");
        stmt.execute("CREATE TABLE t_ili2db_column_prop ("
            + "tablename VARCHAR(255), columnname VARCHAR(255), tag VARCHAR(1024), "
            + "setting VARCHAR(1024))");
        stmt.execute("CREATE TABLE t_ili2db_model ("
            + "modelname VARCHAR(1024), content VARCHAR(1024))");
        if (!withoutTableProp) {
            stmt.execute("CREATE TABLE t_ili2db_table_prop ("
                + "tablename VARCHAR(255), setting VARCHAR(255))");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('sample', 'CLASS')");
        }
        stmt.execute("INSERT INTO t_ili2db_classname VALUES "
            + "('FixtureModel.Topic.Sample', 'sample')");
        stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
            + "('FixtureModel.Topic.Sample.name', 'name', 'sample', NULL)");
        if (withModelRow) {
            stmt.execute("INSERT INTO t_ili2db_model VALUES ('FixtureModel', 'MODEL')");
        }
        stmt.execute("CREATE TABLE sample (t_id INTEGER PRIMARY KEY, name VARCHAR(100))");
    }
}

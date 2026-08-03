package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.metadata.selection.ModelSelectionSource;
import ch.interlis.generator.reader.ili2db.assemble.Ili2dbAssociationDeriver;
import ch.interlis.generator.reader.ili2db.assemble.Ili2dbMetadataAssembler;
import ch.interlis.generator.reader.ili2db.assemble.Ili2dbRelationshipDeriver;
import ch.interlis.generator.reader.ili2db.metrics.CountingConnection;
import ch.interlis.generator.reader.ili2db.metrics.CountingJdbcProxy;
import ch.interlis.generator.reader.ili2db.metrics.JdbcInvocationKind;
import ch.interlis.generator.reader.ili2db.metrics.JdbcInvocationSummary;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reader-Query-Budgets (Spezifikation §51, §52): N+1-Introspection ist
 * messbar begrenzt; Deriver und Assembler führen keine JDBC-Aufrufe aus.
 */
class Ili2dbQueryBudgetTest {

    @Test
    void capabilityDetectionScansMetadataOnce() throws Exception {
        try (Connection raw = connection("cap_once");
             Statement stmt = raw.createStatement()) {
            createSchemaFixture(stmt, 3, 5);
            try (CountingConnection counting = CountingJdbcProxy.wrap(raw)) {
                List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
                new ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogReader()
                    .detectCapabilities(context(counting), diagnostics);
                JdbcInvocationSummary summary = counting.snapshot();
                assertThat(summary.count(JdbcInvocationKind.METADATA_GET_TABLES))
                    .as("getTables exactly once for capability detection")
                    .isEqualTo(1);
                assertThat(summary.count(JdbcInvocationKind.METADATA_GET_COLUMNS))
                    .as("getColumns exactly once for capability detection")
                    .isEqualTo(1);
            }
        }
    }

    @Test
    void increasingAttributesDoesNotIncreasePerTableSchemaCalls() throws Exception {
        try (Connection rawA = connection("attr_10");
             Connection rawB = connection("attr_100")) {
            try (Statement stmtA = rawA.createStatement()) {
                createSchemaFixture(stmtA, 3, 10);
            }
            try (Statement stmtB = rawB.createStatement()) {
                createSchemaFixture(stmtB, 3, 100);
            }
            try (CountingConnection countingA = CountingJdbcProxy.wrap(rawA);
                 CountingConnection countingB = CountingJdbcProxy.wrap(rawB)) {
                readFull(countingA);
                readFull(countingB);
                JdbcInvocationSummary summaryA = countingA.snapshot();
                JdbcInvocationSummary summaryB = countingB.snapshot();
                long perTableCallsA = summaryA.count(JdbcInvocationKind.METADATA_GET_PRIMARY_KEYS)
                    + summaryA.count(JdbcInvocationKind.METADATA_GET_IMPORTED_KEYS);
                long perTableCallsB = summaryB.count(JdbcInvocationKind.METADATA_GET_PRIMARY_KEYS)
                    + summaryB.count(JdbcInvocationKind.METADATA_GET_IMPORTED_KEYS);
                assertThat(perTableCallsB)
                    .as("10x attributes must not increase per-table schema calls")
                    .isLessThanOrEqualTo(perTableCallsA + 2);
            }
        }
    }

    @Test
    void queryCountScalesWithTablesNotAttributes() throws Exception {
        try (Connection raw1 = connection("tables_2");
             Connection raw5 = connection("tables_5")) {
            try (Statement stmt1 = raw1.createStatement()) {
                createSchemaFixture(stmt1, 2, 5);
            }
            try (Statement stmt5 = raw5.createStatement()) {
                createSchemaFixture(stmt5, 5, 5);
            }
            try (CountingConnection counting1 = CountingJdbcProxy.wrap(raw1);
                 CountingConnection counting5 = CountingJdbcProxy.wrap(raw5)) {
                readFull(counting1);
                readFull(counting5);
                JdbcInvocationSummary summary1 = counting1.snapshot();
                JdbcInvocationSummary summary5 = counting5.snapshot();
                long schemaCalls1 = summary1.count(JdbcInvocationKind.METADATA_GET_PRIMARY_KEYS)
                    + summary1.count(JdbcInvocationKind.METADATA_GET_IMPORTED_KEYS);
                long schemaCalls5 = summary5.count(JdbcInvocationKind.METADATA_GET_PRIMARY_KEYS)
                    + summary5.count(JdbcInvocationKind.METADATA_GET_IMPORTED_KEYS);
                // Schema-Calls skalieren mit den Tabellen (2 -> 5 Tabellen),
                // nicht mit der Attributzahl.
                assertThat(schemaCalls5)
                    .as("schema calls scale with tables")
                    .isGreaterThanOrEqualTo(schemaCalls1);
                assertThat(schemaCalls5 - schemaCalls1)
                    .as("roughly proportional to the added tables (3 tables -> <= 6 calls)")
                    .isLessThanOrEqualTo(8);
            }
        }
    }

    @Test
    void enumValuesAreLoadedWhenGeometryIntrospectionIsDisabled() throws Exception {
        try (Connection raw = connection("enum_once");
             Statement stmt = raw.createStatement()) {
            createSchemaFixture(stmt, 3, 5);
            createEnumFixture(stmt, 2);
            try (CountingConnection counting = CountingJdbcProxy.wrap(raw)) {
                readFull(counting);
                long enumQueries = countEnumQueries(counting.snapshot());
                assertThat(enumQueries)
                    .as("each enum table is still read exactly once (no per-attribute N+1)")
                    .isEqualTo(2);
            }
        }
    }

    @Test
    void geometryDisabledSkipsGeometryIntrospection() throws Exception {
        try (Connection raw = connection("geometry_off");
             Statement stmt = raw.createStatement()) {
            createSchemaFixture(stmt, 1, 1);

            Ili2dbReadResult result = new Ili2dbReadCoordinator()
                .read(context(raw), selection(), false);

            assertThat(result.geometry().metadataAvailable()).isTrue();
            assertThat(result.geometry().columns()).isEmpty();
            assertThat(result.diagnostics())
                .noneMatch(diagnostic ->
                    diagnostic.code() == Ili2dbDiagnosticCode.GEOMETRY_METADATA_UNAVAILABLE);
        }
    }

    @Test
    void relationshipDerivationExecutesNoJdbcCalls() throws Exception {
        try (Connection raw = connection("rel_derive");
             Statement stmt = raw.createStatement()) {
            createSchemaFixture(stmt, 3, 5);
            try (CountingConnection counting = CountingJdbcProxy.wrap(raw)) {
                AssembleResult result = assembleOnly(counting);
                result.assemble();
                JdbcInvocationSummary before = counting.snapshot();
                new Ili2dbRelationshipDeriver().derive(result.builder());
                JdbcInvocationSummary after = counting.snapshot();
                assertThat(after.total() - before.total())
                    .as("relationship derivation must not touch JDBC")
                    .isZero();
            }
        }
    }

    @Test
    void associationDerivationExecutesNoJdbcCalls() throws Exception {
        try (Connection raw = connection("assoc_derive");
             Statement stmt = raw.createStatement()) {
            createSchemaFixture(stmt, 3, 5);
            try (CountingConnection counting = CountingJdbcProxy.wrap(raw)) {
                AssembleResult result = assembleOnly(counting);
                result.assemble();
                new Ili2dbRelationshipDeriver().derive(result.builder());
                JdbcInvocationSummary before = counting.snapshot();
                new Ili2dbAssociationDeriver().derive(result.builder(), new ArrayList<>());
                JdbcInvocationSummary after = counting.snapshot();
                assertThat(after.total() - before.total())
                    .as("association derivation must not touch JDBC")
                    .isZero();
            }
        }
    }

    @Test
    void metadataAssemblyExecutesNoJdbcCalls() throws Exception {
        try (Connection raw = connection("assemble_nojdbc");
             Statement stmt = raw.createStatement()) {
            createSchemaFixture(stmt, 3, 5);
            try (CountingConnection counting = CountingJdbcProxy.wrap(raw)) {
                AssembleResult result = assembleOnly(counting);
                JdbcInvocationSummary before = counting.snapshot();
                result.assemble();
                JdbcInvocationSummary after = counting.snapshot();
                assertThat(after.total() - before.total())
                    .as("assembly itself must not execute any JDBC calls")
                    .isZero();
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void readFull(CountingConnection counting) throws Exception {
        List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
        new Ili2dbReadCoordinator().read(context(counting), selection(), false);
    }

    private AssembleResult assembleOnly(CountingConnection counting) throws Exception {
        List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
        var catalogReader = new ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogReader();
        var capabilities = catalogReader.detectCapabilities(context(counting), diagnostics);
        var models = catalogReader.readModels(context(counting), capabilities, diagnostics);
        var classes = catalogReader.readClasses(context(counting), capabilities, diagnostics,
            List.of("BudgetModel"));
        var tableNames = classes.stream()
            .map(ch.interlis.generator.reader.ili2db.catalog.ClassMappingRow::tableName)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .toList();
        var catalog = new ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogSnapshot(
            catalogReader.readSettings(context(counting), capabilities, diagnostics),
            models,
            classes,
            catalogReader.readAttributes(context(counting), capabilities, diagnostics,
                List.of("BudgetModel"), tableNames),
            catalogReader.readInheritance(context(counting), capabilities, diagnostics,
                List.of("BudgetModel")),
            catalogReader.readColumnProperties(context(counting), capabilities, diagnostics),
            catalogReader.readEnumDomains(context(counting), capabilities, diagnostics),
            capabilities);
        var schema = new ch.interlis.generator.reader.ili2db.schema.DefaultJdbcSchemaIntrospector()
            .inspect(context(counting), tableNames.stream()
                .map(name -> new ch.interlis.generator.reader.sql.QualifiedSqlName(
                    null, ch.interlis.generator.reader.sql.SqlIdentifier.discovered(name)))
                .toList());
        return new AssembleResult(counting, catalog, schema, diagnostics);
    }

    private static final class AssembleResult {
        private final CountingConnection counting;
        private final ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogSnapshot catalog;
        private final ch.interlis.generator.reader.ili2db.schema.JdbcSchemaSnapshot schema;
        private final List<Ili2dbDiagnostic> diagnostics;

        private AssembleResult(CountingConnection counting,
                               ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogSnapshot catalog,
                               ch.interlis.generator.reader.ili2db.schema.JdbcSchemaSnapshot schema,
                               List<Ili2dbDiagnostic> diagnostics) {
            this.counting = counting;
            this.catalog = catalog;
            this.schema = schema;
            this.diagnostics = diagnostics;
        }

        ch.interlis.generator.model.builder.ModelMetadataBuilder builder;

        void assemble() {
            builder = new Ili2dbMetadataAssembler().assemble(context(), catalog, schema,
                ch.interlis.generator.reader.ili2db.schema.GeometrySchemaSnapshot.empty(),
                selection(), diagnostics);
        }

        ch.interlis.generator.model.builder.ModelMetadataBuilder builder() {
            if (builder == null) {
                assemble();
            }
            return builder;
        }

        private Ili2dbReadContext context() {
            try {
                return new Ili2dbReadContext(counting, null,
                    ch.interlis.generator.reader.sql.SqlIdentifierRenderer.from(
                        counting.getMetaData()),
                    ch.interlis.generator.reader.ili2db.schema.DatabaseDialect.H2);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private ModelSelection selection() {
            return new ModelSelection("BudgetModel",
                new LinkedHashSet<>(List.of("BudgetModel")),
                ModelSelectionSource.ILI2C_DEPENDENCY_GRAPH);
        }
    }

    private long countEnumQueries(JdbcInvocationSummary summary) {
        return summary.normalizedSqlCounts().entrySet().stream()
            .filter(entry -> entry.getKey().toLowerCase().contains("budget_enum_"))
            .mapToLong(Map.Entry::getValue)
            .sum();
    }

    private void createEnumFixture(Statement stmt, int enumTableCount) throws Exception {
        for (int e = 0; e < enumTableCount; e++) {
            String table = "budget_enum_" + e;
            stmt.execute("INSERT INTO t_ili2db_classname VALUES "
                + "('BudgetModel.Topic.Status" + e + "', '" + table + "')");
            stmt.execute("INSERT INTO t_ili2db_column_prop VALUES "
                + "('budget_table_0', 'attr_" + e + "', 'ch.ehi.ili2db.enumDomain', "
                + "'BudgetModel.Topic.Status" + e + "')");
            stmt.execute("CREATE TABLE " + table + " ("
                + "ilicode VARCHAR(100), dispname VARCHAR(100), seq INT)");
            stmt.execute("INSERT INTO " + table + " VALUES ('a', 'A', 0)");
        }
    }

    private ModelSelection selection() {
        return new ModelSelection("BudgetModel",
            new LinkedHashSet<>(List.of("BudgetModel")), ModelSelectionSource.ILI2C_DEPENDENCY_GRAPH);
    }

    private Ili2dbReadContext context(Connection connection) throws Exception {
        return new Ili2dbReadContext(connection, null,
            ch.interlis.generator.reader.sql.SqlIdentifierRenderer.from(connection.getMetaData()),
            ch.interlis.generator.reader.ili2db.schema.DatabaseDialect.H2);
    }

    private Connection connection(String name) throws Exception {
        return DriverManager.getConnection(
            "jdbc:h2:mem:budget_" + name + "_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    }

    private void createSchemaFixture(Statement stmt, int tableCount, int attributeCount)
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
        stmt.execute("CREATE TABLE t_ili2db_table_prop ("
            + "tablename VARCHAR(255), setting VARCHAR(255))");
        stmt.execute("CREATE TABLE t_ili2db_model ("
            + "modelname VARCHAR(1024), content VARCHAR(1024))");
        stmt.execute("INSERT INTO t_ili2db_model VALUES ('BudgetModel', 'MODEL')");
        for (int t = 0; t < tableCount; t++) {
            String table = "budget_table_" + t;
            stmt.execute("INSERT INTO t_ili2db_classname VALUES "
                + "('BudgetModel.Topic.Class" + t + "', '" + table + "')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('" + table + "', 'CLASS')");
            StringBuilder columns = new StringBuilder("t_id INTEGER PRIMARY KEY");
            for (int a = 0; a < attributeCount; a++) {
                String column = "attr_" + a;
                columns.append(", ").append(column).append(" VARCHAR(100)");
                stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                    + "('BudgetModel.Topic.Class" + t + ".attr" + a + "', '" + column
                    + "', '" + table + "', NULL)");
            }
            stmt.execute("CREATE TABLE " + table + " (" + columns + ")");
        }
    }
}

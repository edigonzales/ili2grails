package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.metadata.selection.ModelSelectionSource;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogReader;
import ch.interlis.generator.reader.ili2db.schema.DatabaseDialect;
import ch.interlis.generator.reader.sql.SqlIdentifierRenderer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fokussierte Erreichbarkeitstests für jeden Reader-Diagnostic-Code
 * (Spezifikation §15.4).
 */
class Ili2dbDiagnosticReachabilityTest {

    private final Ili2dbCatalogReader catalogReader = new Ili2dbCatalogReader();

    @Test
    void optionalMetaTableMissingProducesWarning() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            baseFixture(stmt);
            stmt.execute("DROP TABLE t_ili2db_settings");
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            catalogReader.readSettings(context(connection), capabilities(connection), diagnostics);
            assertThat(diagnostics)
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.OPTIONAL_META_TABLE_MISSING)
                .isNotEmpty();
        }
    }

    @Test
    void metaTableColumnsUnsupportedProducesWarning() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            baseFixture(stmt);
            stmt.execute("DROP TABLE t_ili2db_model");
            stmt.execute("CREATE TABLE t_ili2db_model (content VARCHAR(1024))");
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            catalogReader.readModels(context(connection), capabilities(connection), diagnostics);
            assertThat(diagnostics)
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.META_TABLE_COLUMNS_UNSUPPORTED)
                .isNotEmpty();
        }
    }

    @Test
    void classMappingIncompleteProducesDiagnostic() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            baseFixture(stmt);
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('weird', 'BOGUS_KIND')");
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            new Ili2dbMetadataAssemblerProbe().assemble(connection, stmt, diagnostics);
            assertThat(diagnostics)
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.CLASS_MAPPING_INCOMPLETE)
                .isNotEmpty();
        }
    }

    @Test
    void attributeOwnerUnresolvedProducesDiagnostic() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            baseFixture(stmt);
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                + "('OtherModel.Topic.Orphan.name', 'name', 'orphan_table', NULL)");
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            new Ili2dbMetadataAssemblerProbe().assemble(connection, stmt, diagnostics);
            assertThat(diagnostics)
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.ATTRIBUTE_OWNER_UNRESOLVED)
                .isNotEmpty();
        }
    }

    @Test
    void columnSchemaMissingProducesDiagnostic() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            baseFixture(stmt);
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                + "('ReadModel.Topic.Sample.ghost', 'ghost', 'sample', NULL)");
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            new Ili2dbMetadataAssemblerProbe().assemble(connection, stmt, diagnostics);
            assertThat(diagnostics)
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.COLUMN_SCHEMA_MISSING)
                .isNotEmpty();
        }
    }

    @Test
    void primaryKeyAssumedProducesWarning() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            baseFixture(stmt);
            // gequoteter, kleingeschriebener Tabellenname: H2 findet Spalten nur
            // bei exakter Case-Übereinstimmung (pattern matching)
            stmt.execute("CREATE TABLE \"sample2\" (t_id INTEGER NOT NULL, name VARCHAR(100))");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES ('ReadModel.Topic.Sample2', 'sample2')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('sample2', 'CLASS')");
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            new Ili2dbMetadataAssemblerProbe().assemble(connection, stmt, diagnostics);
            assertThat(diagnostics)
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.PRIMARY_KEY_ASSUMED)
                .isNotEmpty();
        }
    }

    @Test
    void enumDomainUnresolvedProducesDiagnostic() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            baseFixture(stmt);
            stmt.execute("DROP TABLE t_ili2db_column_prop");
            stmt.execute("CREATE TABLE t_ili2db_column_prop ("
                + "tablename VARCHAR(255), columnname VARCHAR(255), tag VARCHAR(1024))");
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            catalogReader.readEnumDomains(context(connection), capabilities(connection), diagnostics);
            assertThat(diagnostics)
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.ENUM_DOMAIN_UNRESOLVED)
                .isNotEmpty();
        }
    }

    @Test
    void inheritanceUnresolvedProducesDiagnostic() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            baseFixture(stmt);
            stmt.execute("DROP TABLE t_ili2db_inheritance");
            stmt.execute("CREATE TABLE t_ili2db_inheritance (baseclass VARCHAR(1024))");
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            catalogReader.readInheritance(context(connection), capabilities(connection),
                diagnostics, List.of("ReadModel"));
            assertThat(diagnostics)
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.INHERITANCE_UNRESOLVED)
                .isNotEmpty();
        }
    }

    @Test
    void geometryMetadataUnavailableProducesWarning() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            baseFixture(stmt);
            ch.interlis.generator.reader.Ili2dbMetadataReader reader =
                ch.interlis.generator.reader.Ili2dbMetadataReader.create(connection, null);
            Ili2dbReadResult result = reader.read(request().modelSelection(), Ili2dbFailurePolicy.DIAGNOSTIC);
            assertThat(result.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.GEOMETRY_METADATA_UNAVAILABLE)
                .isNotEmpty();
        }
    }

    @Test
    void unknownDialectProducesDialectUnsupportedWarning() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            baseFixture(stmt);
            Connection otherDialect = dialectOverride(connection, "Some Unknown Database");
            ch.interlis.generator.reader.Ili2dbMetadataReader reader =
                ch.interlis.generator.reader.Ili2dbMetadataReader.create(otherDialect, null);
            Ili2dbReadResult result = reader.read(request().modelSelection(), Ili2dbFailurePolicy.DIAGNOSTIC);
            assertThat(result.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.DATABASE_DIALECT_UNSUPPORTED)
                .isNotEmpty();
        }
    }

    @Test
    void associationMappingIncompleteProducesDiagnostic() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            baseFixture(stmt);
            stmt.execute("CREATE TABLE link (t_id INTEGER PRIMARY KEY)");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES ('ReadModel.Topic.Link', 'link')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('link', 'ASSOCIATION')");
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            new Ili2dbMetadataAssemblerProbe().assemble(connection, stmt, diagnostics);
            assertThat(diagnostics)
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.ASSOCIATION_MAPPING_INCOMPLETE)
                .isNotEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
            "jdbc:h2:mem:reach_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    }

    private Ili2dbReadContext context(Connection connection) throws Exception {
        return new Ili2dbReadContext(connection, null,
            SqlIdentifierRenderer.from(connection.getMetaData()), DatabaseDialect.H2);
    }

    private ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogCapabilities capabilities(
        Connection connection) throws Exception {
        List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
        return catalogReader.detectCapabilities(context(connection), diagnostics);
    }

    private Ili2dbReadRequest request() {
        return new Ili2dbReadRequest(new ModelSelection("ReadModel",
            new LinkedHashSet<>(List.of("ReadModel")), ModelSelectionSource.ILI2C_DEPENDENCY_GRAPH),
            Ili2dbFailurePolicy.DIAGNOSTIC, true, true);
    }

    private void baseFixture(Statement stmt) throws Exception {
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
        stmt.execute("INSERT INTO t_ili2db_classname VALUES "
            + "('ReadModel.Topic.Sample', 'sample')");
        stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('sample', 'CLASS')");
        stmt.execute("INSERT INTO t_ili2db_model VALUES ('ReadModel', 'MODEL')");
        stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
            + "('ReadModel.Topic.Sample.name', 'name', 'sample', NULL)");
        stmt.execute("CREATE TABLE sample (t_id INTEGER PRIMARY KEY, name VARCHAR(100))");
    }

    private Connection dialectOverride(Connection delegate, String productName) throws Exception {
        DatabaseMetaData realMeta = delegate.getMetaData();
        DatabaseMetaData overridden = (DatabaseMetaData) Proxy.newProxyInstance(
            DatabaseMetaData.class.getClassLoader(),
            new Class<?>[] {DatabaseMetaData.class},
            (proxy, method, args) -> {
                if ("getDatabaseProductName".equals(method.getName())) {
                    return productName;
                }
                return method.invoke(realMeta, args);
            });
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
                if ("getMetaData".equals(method.getName())) {
                    return overridden;
                }
                if ("isClosed".equals(method.getName())) {
                    return delegate.isClosed();
                }
                if ("close".equals(method.getName())) {
                    return null;
                }
                return method.invoke(delegate, args);
            });
    }

    /**
     * Treibt den kompletten Assembler-Lauf für einfache Diagnose-Tests.
     */
    private static final class Ili2dbMetadataAssemblerProbe {

        void assemble(Connection connection, Statement stmt, List<Ili2dbDiagnostic> diagnostics)
            throws Exception {
            Ili2dbReadContext context = new Ili2dbReadContext(connection, null,
                SqlIdentifierRenderer.from(connection.getMetaData()), DatabaseDialect.H2);
            List<ch.interlis.generator.reader.ili2db.catalog.ModelRow> models = List.of(
                new ch.interlis.generator.reader.ili2db.catalog.ModelRow("ReadModel", "MODEL"));
            List<ch.interlis.generator.reader.ili2db.catalog.ClassMappingRow> classes = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery(
                "SELECT tp.tablename, tp.setting, c.iliname FROM t_ili2db_table_prop tp "
                    + "LEFT JOIN t_ili2db_classname c ON upper(tp.tablename) = upper(c.sqlname)")) {
                while (rs.next()) {
                    classes.add(new ch.interlis.generator.reader.ili2db.catalog.ClassMappingRow(
                        rs.getString("iliname"), rs.getString("tablename"), rs.getString("setting")));
                }
            }
            List<ch.interlis.generator.reader.ili2db.catalog.AttributeMappingRow> attributes = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM t_ili2db_attrname")) {
                while (rs.next()) {
                    attributes.add(new ch.interlis.generator.reader.ili2db.catalog.AttributeMappingRow(
                        rs.getString("iliname"), rs.getString("sqlname"),
                        rs.getString("colowner"), rs.getString("target")));
                }
            }
            List<String> tableNames = classes.stream()
                .map(ch.interlis.generator.reader.ili2db.catalog.ClassMappingRow::tableName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
            ch.interlis.generator.reader.ili2db.schema.JdbcSchemaSnapshot schema =
                new ch.interlis.generator.reader.ili2db.schema.DefaultJdbcSchemaIntrospector().inspect(
                    context, tableNames.stream()
                        .map(name -> new ch.interlis.generator.reader.sql.QualifiedSqlName(
                            null, ch.interlis.generator.reader.sql.SqlIdentifier.discovered(name)))
                        .toList());
            ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogSnapshot catalog =
                new ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogSnapshot(
                    new java.util.LinkedHashMap<>(), models, classes, attributes, List.of(),
                    List.of(), List.of(), new ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogCapabilities(
                    java.util.Set.of(), java.util.Map.of()));
            ch.interlis.generator.model.builder.ModelMetadataBuilder builder =
                new ch.interlis.generator.reader.ili2db.assemble.Ili2dbMetadataAssembler().assemble(
                    context, catalog, schema, ch.interlis.generator.reader.ili2db.schema.GeometrySchemaSnapshot.empty(),
                    new ModelSelection("ReadModel", new LinkedHashSet<>(List.of("ReadModel")),
                        ModelSelectionSource.ILI2C_DEPENDENCY_GRAPH),
                    diagnostics);
            new ch.interlis.generator.reader.ili2db.assemble.Ili2dbRelationshipDeriver().derive(builder);
            new ch.interlis.generator.reader.ili2db.assemble.Ili2dbAssociationDeriver().derive(builder, diagnostics);
        }
    }
}

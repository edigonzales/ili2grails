package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.reader.ili2db.catalog.AttributeMappingRow;
import ch.interlis.generator.reader.ili2db.catalog.ClassMappingRow;
import ch.interlis.generator.reader.ili2db.catalog.EnumDomainRow;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogCapabilities;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogReader;
import ch.interlis.generator.reader.ili2db.catalog.InheritanceRow;
import ch.interlis.generator.reader.ili2db.catalog.ModelRow;
import ch.interlis.generator.reader.ili2db.schema.DatabaseDialect;
import ch.interlis.generator.reader.sql.SqlIdentifierRenderer;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Katalog-Schicht: erzeugt ausschliesslich typed Rows/Snapshots,
 * keine IR.
 */
class Ili2dbCatalogReaderTest {

    @Test
    void detectsCapabilitiesCaseInsensitivelyAndReadsTypedRows() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:catalog_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            createFixture(connection);

            Ili2dbCatalogReader reader = new Ili2dbCatalogReader();
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            Ili2dbCatalogCapabilities capabilities =
                reader.detectCapabilities(context(connection), diagnostics);

            assertThat(capabilities.hasTable("t_ili2db_classname")).isTrue();
            assertThat(capabilities.hasTable("T_ILI2DB_ATTRNAME")).isTrue();
            assertThat(capabilities.hasColumn("t_ili2db_attrname", "COLOWNER")).isTrue();
            assertThat(diagnostics).isEmpty();

            List<ModelRow> models = reader.readModels(context(connection), capabilities, diagnostics);
            assertThat(models).extracting(ModelRow::modelName)
                .containsExactly("CatalogModel");

            List<ClassMappingRow> classes = reader.readClasses(context(connection),
                capabilities, diagnostics, List.of("CatalogModel"));
            assertThat(classes).extracting(ClassMappingRow::iliName)
                .containsExactly("CatalogModel.Topic.Child", "CatalogModel.Topic.Sample");

            List<AttributeMappingRow> attributes = reader.readAttributes(context(connection),
                capabilities, diagnostics, List.of("CatalogModel"), List.of("sample"));
            assertThat(attributes)
                .extracting(AttributeMappingRow::sqlName)
                .contains("name", "owner_fk");

            List<InheritanceRow> inheritance = reader.readInheritance(context(connection),
                capabilities, diagnostics, List.of("CatalogModel"));
            assertThat(inheritance)
                .extracting(InheritanceRow::thisClass)
                .containsExactly("CatalogModel.Topic.Child");

            List<EnumDomainRow> enumDomains = reader.readEnumDomains(context(connection),
                capabilities, diagnostics);
            assertThat(enumDomains).hasSize(1);
            assertThat(enumDomains.get(0).enumIliName()).isEqualTo("CatalogModel.Topic.Status");
            assertThat(enumDomains.get(0).enumTableName()).isEqualTo("sample_status");
        }
    }

    @Test
    void missingRequiredMetaTableProducesFatalDiagnostic() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:catalog_missing_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE t_ili2db_attrname ("
                    + "iliname VARCHAR(1024), sqlname VARCHAR(1024), colowner VARCHAR(1024), "
                    + "target VARCHAR(1024))");
            }
            Ili2dbCatalogReader reader = new Ili2dbCatalogReader();
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();
            reader.detectCapabilities(context(connection), diagnostics);

            assertThat(diagnostics)
                .filteredOn(Ili2dbDiagnostic::isBlocking)
                .extracting(Ili2dbDiagnostic::code)
                .contains(Ili2dbDiagnosticCode.REQUIRED_META_TABLE_MISSING);
        }
    }

    private Ili2dbReadContext context(Connection connection) throws Exception {
        return new Ili2dbReadContext(
            connection,
            null,
            SqlIdentifierRenderer.from(connection.getMetaData()),
            DatabaseDialect.H2
        );
    }

    private void createFixture(Connection connection) throws Exception {
        try (Statement stmt = connection.createStatement()) {
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
                + "('CatalogModel.Topic.Sample', 'sample')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES "
                + "('CatalogModel.Topic.Child', 'child')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES "
                + "('CatalogModel.Topic.Status', 'sample_status')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('sample', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('child', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                + "('CatalogModel.Topic.Sample.name', 'name', 'sample', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                + "('CatalogModel.Topic.Sample.owner', 'owner_fk', 'sample', "
                + "'CatalogModel.Topic.Sample')");
            stmt.execute("INSERT INTO t_ili2db_inheritance VALUES "
                + "('CatalogModel.Topic.Child', 'CatalogModel.Topic.Sample')");
            stmt.execute("INSERT INTO t_ili2db_column_prop VALUES "
                + "('sample', 'status', 'ch.ehi.ili2db.enumDomain', 'CatalogModel.Topic.Status')");
            stmt.execute("INSERT INTO t_ili2db_model VALUES ('CatalogModel', 'MODEL')");
            stmt.execute("INSERT INTO t_ili2db_settings VALUES "
                + "('ch.ehi.ili2db.sender', 'ili2pg-5.5.1')");
            stmt.execute("CREATE TABLE sample (t_id INTEGER PRIMARY KEY, name VARCHAR(100), "
                + "owner_fk INTEGER)");
            stmt.execute("CREATE TABLE child (t_id INTEGER PRIMARY KEY, name VARCHAR(100))");
            stmt.execute("CREATE TABLE sample_status ("
                + "ilicode VARCHAR(100), dispname VARCHAR(100), seq INT)");
            stmt.execute("INSERT INTO sample_status VALUES ('a', 'A', 0)");
        }
    }
}

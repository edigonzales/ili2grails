package ch.interlis.generator.reader;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.*;

/**
 * Integrationstests für den Ili2dbMetadataReader mit problematischen Schemanamen
 * (Grossbuchstaben, Bindestrich) und sicherem Quoting.
 */
class Ili2dbMetadataReaderSqlIdentifierIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"MySchema", "my-schema"})
    void readsIli2dbMetadataFromSchemaWithSpecialCharacters(String schemaName) throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:schema_special_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            createSchemaAndMetaTables(connection, schemaName);

            Ili2dbMetadataReader reader = Ili2dbMetadataReader.create(connection, schemaName);
            ModelMetadata metadata = reader.readMetadata("SqlIdentModel");

            assertThat(metadata.getSchemaName()).isEqualTo(schemaName);
            assertThat(metadata.getClass("SqlIdentModel.Topic.Sample")).isNotNull();
            ClassMetadata sample = metadata.getClass("SqlIdentModel.Topic.Sample");
            assertThat(sample.getTableName()).isEqualTo("sample");
            assertThat(sample.getAttribute("name").getColumnName()).isEqualTo("name");
        }
    }

    @Test
    void readsWithoutSchemaUsingDefaultSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:schema_default_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            createSchemaAndMetaTables(connection, null);

            Ili2dbMetadataReader reader = Ili2dbMetadataReader.create(connection, null);
            ModelMetadata metadata = reader.readMetadata("SqlIdentModel");

            assertThat(metadata.getSchemaName()).isNull();
            assertThat(metadata.getClass("SqlIdentModel.Topic.Sample")).isNotNull();
        }
    }

    @Test
    void rejectsInjectionLikeSchemaNames() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:schema_reject_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            assertThatThrownBy(() -> Ili2dbMetadataReader.create(connection, "public;DROP TABLE x"))
                .isInstanceOf(ch.interlis.generator.reader.sql.InvalidSqlIdentifierException.class);
            assertThatThrownBy(() -> Ili2dbMetadataReader.create(connection, "a--comment"))
                .isInstanceOf(ch.interlis.generator.reader.sql.InvalidSqlIdentifierException.class);
            assertThatThrownBy(() -> Ili2dbMetadataReader.create(connection, "public.other"))
                .isInstanceOf(ch.interlis.generator.reader.sql.InvalidSqlIdentifierException.class);
        }
    }

    @Test
    void readsEnumTableWithDiscoveredName() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:schema_enum_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            createSchemaAndMetaTables(connection, null);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE sample_status ("
                    + "ilicode VARCHAR(100), dispname VARCHAR(100), seq INT)");
                stmt.execute("INSERT INTO sample_status VALUES ('a', 'A', 0)");
                stmt.execute("INSERT INTO t_ili2db_column_prop VALUES "
                    + "('sample', 'status', 'ch.ehi.ili2db.enumDomain', 'SqlIdentModel.Topic.Status')");
                stmt.execute("INSERT INTO t_ili2db_classname VALUES "
                    + "('SqlIdentModel.Topic.Status', 'sample_status')");
            }

            Ili2dbMetadataReader reader = Ili2dbMetadataReader.create(connection, null);
            ModelMetadata metadata = reader.readMetadata("SqlIdentModel");

            ClassMetadata sample = metadata.getClass("SqlIdentModel.Topic.Sample");
            assertThat(sample.getAttribute("status").getEnumType())
                .isEqualTo("SqlIdentModel.Topic.Status");
            assertThat(sample.getAttribute("status").getEnumValues())
                .extracting(v -> v.getIliCode())
                .containsExactly("a");
        }
    }

    private void createSchemaAndMetaTables(Connection connection, String schemaName) throws Exception {
        try (Statement stmt = connection.createStatement()) {
            if (schemaName != null) {
                stmt.execute("CREATE SCHEMA \"" + schemaName.replace("\"", "\"\"") + "\"");
            }
            String prefix = schemaName == null ? "" : "\"" + schemaName.replace("\"", "\"\"") + "\".";
            stmt.execute("CREATE TABLE " + prefix + "t_ili2db_classname ("
                + "iliname VARCHAR(1024) PRIMARY KEY, sqlname VARCHAR(1024))");
            stmt.execute("CREATE TABLE " + prefix + "t_ili2db_attrname ("
                + "iliname VARCHAR(1024), sqlname VARCHAR(1024), colowner VARCHAR(1024), target VARCHAR(1024))");
            stmt.execute("CREATE TABLE " + prefix + "t_ili2db_settings ("
                + "tag VARCHAR(1024), setting VARCHAR(1024))");
            stmt.execute("CREATE TABLE " + prefix + "t_ili2db_inheritance ("
                + "thisclass VARCHAR(1024), baseclass VARCHAR(1024))");
            stmt.execute("CREATE TABLE " + prefix + "t_ili2db_column_prop ("
                + "tablename VARCHAR(255), columnname VARCHAR(255), tag VARCHAR(1024), setting VARCHAR(1024))");
            stmt.execute("CREATE TABLE " + prefix + "t_ili2db_table_prop ("
                + "tablename VARCHAR(255), setting VARCHAR(255))");
            stmt.execute("CREATE TABLE " + prefix + "t_ili2db_model ("
                + "modelname VARCHAR(1024), content VARCHAR(1024))");

            stmt.execute("INSERT INTO " + prefix + "t_ili2db_classname VALUES "
                + "('SqlIdentModel.Topic.Sample', 'sample')");
            stmt.execute("INSERT INTO " + prefix + "t_ili2db_table_prop VALUES ('sample', 'CLASS')");
            stmt.execute("INSERT INTO " + prefix + "t_ili2db_attrname VALUES "
                + "('name', 'name', 'SqlIdentModel.Topic.Sample', NULL)");
            stmt.execute("INSERT INTO " + prefix + "t_ili2db_attrname VALUES "
                + "('status', 'status', 'SqlIdentModel.Topic.Sample', NULL)");
            stmt.execute("INSERT INTO " + prefix + "t_ili2db_settings VALUES "
                + "('ch.ehi.ili2db.version', '5.5.1')");
            stmt.execute("INSERT INTO " + prefix + "t_ili2db_model VALUES "
                + "('SqlIdentModel', 'MODEL')");

            String tablePrefix = schemaName == null ? "" : "\"" + schemaName.replace("\"", "\"\"") + "\".";
            stmt.execute("CREATE TABLE " + tablePrefix + "sample ("
                + "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                + "name VARCHAR(100), status VARCHAR(10))");
        }
    }
}

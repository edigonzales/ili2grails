package ch.interlis.generator.metadata;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.Ili2cModelReader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataJsonWriterTest {

    @ParameterizedTest
    @MethodSource("ili2cGoldenCases")
    void writesDeterministicIli2cGoldenJson(String modelFile,
                                            String modelName,
                                            String goldenFile) throws Exception {
        ModelMetadata metadata = new Ili2cModelReader(new File(modelFile))
            .readMetadata(modelName);

        assertGoldenJson(metadata, goldenFile);
    }

    @ParameterizedTest
    @MethodSource("mergedGoldenCases")
    void writesDeterministicMergedGoldenJson(String modelFile,
                                             String modelName,
                                             String goldenFile) throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:metadata_json_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"
        )) {
            createSimpleAddressIli2dbFixture(connection);
            ModelMetadata metadata = new MetadataReader(connection, new File(modelFile), null, null)
                .readMetadata(modelName);

            assertGoldenJson(metadata, goldenFile);
        }
    }

    static Stream<Arguments> ili2cGoldenCases() {
        return Stream.of(
            Arguments.of(
                "test-models/CoreIrTestModel.ili",
                "CoreIrTestModel",
                "CoreIrTestModel.json"
            ),
            Arguments.of(
                "test-models/SimpleAddressModel.ili",
                "SimpleAddressModel",
                "SimpleAddressModel.ili2c.json"
            ),
            Arguments.of(
                "test-models/StructureCompositionCases.ili",
                "StructureCompositionCases",
                "StructureCompositionCases.ili2c.json"
            )
        );
    }

    static Stream<Arguments> mergedGoldenCases() {
        return Stream.of(
            Arguments.of(
                "test-models/SimpleAddressModel.ili",
                "SimpleAddressModel",
                "SimpleAddressModel.merged-h2.json"
            )
        );
    }

    private void assertGoldenJson(ModelMetadata metadata, String goldenFile) throws Exception {
        String json = new MetadataJsonWriter().toJson(metadata);
        String expected = Files.readString(Path.of("src/test/resources/metadata-golden", goldenFile));

        assertThat(json).isEqualTo(expected);
    }

    private void createSimpleAddressIli2dbFixture(Connection connection) throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE t_ili2db_classname (" +
                    "iliname VARCHAR(1024) PRIMARY KEY, " +
                    "sqlname VARCHAR(1024)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE t_ili2db_attrname (" +
                    "iliname VARCHAR(1024), " +
                    "sqlname VARCHAR(1024), " +
                    "colowner VARCHAR(1024), " +
                    "target VARCHAR(1024)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE t_ili2db_settings (" +
                    "tag VARCHAR(1024), " +
                    "setting VARCHAR(1024)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE t_ili2db_inheritance (" +
                    "thisclass VARCHAR(1024), " +
                    "baseclass VARCHAR(1024)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE t_ili2db_column_prop (" +
                    "tablename VARCHAR(255), " +
                    "columnname VARCHAR(255), " +
                    "tag VARCHAR(1024), " +
                    "setting VARCHAR(1024)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE t_ili2db_table_prop (" +
                    "tablename VARCHAR(255), " +
                    "setting VARCHAR(255)" +
                    ")"
            );

            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('SimpleAddressModel.Addresses.Address', 'address')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('SimpleAddressModel.Addresses.Person', 'person')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('SimpleAddressModel.Addresses.PersonAddress', 'personaddress')");

            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('address', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('person', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('personaddress', 'ASSOCIATION')");

            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('street', 'astreet', 'SimpleAddressModel.Addresses.Address', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('houseNumber', 'housenumber', 'SimpleAddressModel.Addresses.Address', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('postalCode', 'postalcode', 'SimpleAddressModel.Addresses.Address', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('firstName', 'firstname', 'SimpleAddressModel.Addresses.Person', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('lastName', 'lastname', 'SimpleAddressModel.Addresses.Person', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('birthDate', 'birthdate', 'SimpleAddressModel.Addresses.Person', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('person', 'person_id', 'SimpleAddressModel.Addresses.PersonAddress', " +
                    "'SimpleAddressModel.Addresses.Person')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('address', 'address_id', 'SimpleAddressModel.Addresses.PersonAddress', " +
                    "'SimpleAddressModel.Addresses.Address')");

            stmt.execute("INSERT INTO t_ili2db_settings VALUES " +
                "('ch.ehi.ili2db.version', '4.9.1')");

            stmt.execute(
                "CREATE TABLE address (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "astreet VARCHAR(100) NOT NULL, " +
                    "housenumber VARCHAR(10), " +
                    "postalcode VARCHAR(10) NOT NULL" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE person (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "firstname VARCHAR(50) NOT NULL, " +
                    "lastname VARCHAR(50) NOT NULL, " +
                    "birthdate DATE" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE personaddress (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "person_id INTEGER, " +
                    "address_id INTEGER, " +
                    "FOREIGN KEY (person_id) REFERENCES person(t_id), " +
                    "FOREIGN KEY (address_id) REFERENCES address(t_id)" +
                    ")"
            );
        }
    }
}

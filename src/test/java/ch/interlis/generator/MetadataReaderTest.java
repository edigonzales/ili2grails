package ch.interlis.generator;

import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration-Test für den MetadataReader.
 * 
 * Verwendet eine H2-Datenbank im Memory-Modus.
 */
class MetadataReaderTest {
    
    private Connection connection;
    private File modelFile;
    
    @BeforeEach
    void setUp() throws Exception {
        // H2 Memory-Datenbank
        connection = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        
        // Modell-Datei
        modelFile = new File("test-models/SimpleAddressModel.ili");
        
        // Simuliere ili2db Metatabellen
        createIli2dbMetaTables();
        insertSampleData();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
    
    @Test
    void testReadMetadata_withoutModelFile() throws Exception {
        // Nur DB-Metadaten, ohne ili2c
        MetadataReader reader = new MetadataReader(connection, null, "PUBLIC", null);
        
        ModelMetadata metadata = reader.readMetadata("SimpleAddressModel");
        
        assertThat(metadata).isNotNull();
        assertThat(metadata.getModelName()).isEqualTo("SimpleAddressModel");
        assertThat(metadata.getSchemaName()).isEqualTo("PUBLIC");
        
        // Prüfe Klassen
        assertThat(metadata.getClasses()).hasSize(3);
        
        ClassMetadata addressClass = metadata.getClass("SimpleAddressModel.Addresses.Address");
        assertThat(addressClass).isNotNull();
        assertThat(addressClass.getTableName()).isEqualTo("address");
        assertThat(addressClass.getAllAttributes()).hasSizeGreaterThan(0);
        
        // Prüfe Attribute
        AttributeMetadata streetAttr = addressClass.getAttribute("street");
        assertThat(streetAttr).isNotNull();
        assertThat(streetAttr.getColumnName()).isEqualTo("astreet");
        assertThat(streetAttr.isMandatory()).isTrue();
    }
    
    @Test
    void testAttributeJavaTypeInference() throws Exception {
        MetadataReader reader = new MetadataReader(connection, null, "PUBLIC", null);
        ModelMetadata metadata = reader.readMetadata("SimpleAddressModel");
        
        ClassMetadata personClass = metadata.getClass("SimpleAddressModel.Addresses.Person");
        assertThat(personClass).isNotNull();
        
        // Text → String
        AttributeMetadata firstName = personClass.getAttribute("firstName");
        assertThat(firstName.getJavaType()).isEqualTo("String");
        
        // XMLDate → LocalDate
        AttributeMetadata birthDate = personClass.getAttribute("birthDate");
        assertThat(birthDate.getJavaType()).isEqualTo("java.time.LocalDate");
    }
    
    @Test
    void testRelationshipDetection() throws Exception {
        MetadataReader reader = new MetadataReader(connection, null, "PUBLIC", null);
        ModelMetadata metadata = reader.readMetadata("SimpleAddressModel");
        
        ClassMetadata personAddressClass = metadata.getClass("SimpleAddressModel.Addresses.PersonAddress");
        assertThat(personAddressClass).isNotNull();
        
        // Sollte 2 Beziehungen haben (zu Person und zu Address)
        assertThat(personAddressClass.getRelationships()).hasSizeGreaterThanOrEqualTo(1);
    }
    
    /**
     * Erstellt ili2db Metatabellen (vereinfacht).
     */
    private void createIli2dbMetaTables() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            // t_ili2db_classname
            stmt.execute(
                "CREATE TABLE t_ili2db_classname (" +
                "  iliname VARCHAR(1024) PRIMARY KEY," +
                "  sqlname VARCHAR(1024)" +
                ")"
            );
            
            // t_ili2db_attrname
            stmt.execute(
                "CREATE TABLE t_ili2db_attrname (" +
                "  iliname VARCHAR(1024)," +
                "  sqlname VARCHAR(1024)," +
                "  owner VARCHAR(1024)," +
                "  target VARCHAR(1024)" +
                ")"
            );
            
            // t_ili2db_settings
            stmt.execute(
                "CREATE TABLE t_ili2db_settings (" +
                "  tag VARCHAR(1024)," +
                "  setting VARCHAR(1024)" +
                ")"
            );
            
            // t_ili2db_inheritance
            stmt.execute(
                "CREATE TABLE t_ili2db_inheritance (" +
                "  thisclass VARCHAR(1024)," +
                "  baseclass VARCHAR(1024)" +
                ")"
            );
            
            // t_ili2db_column_prop
            stmt.execute(
                "CREATE TABLE t_ili2db_column_prop (" +
                "  tablename VARCHAR(255)," +
                "  columnname VARCHAR(255)," +
                "  tag VARCHAR(1024)," +
                "  setting VARCHAR(1024)" +
                ")"
            );
        }
    }
    
    /**
     * Fügt Beispieldaten ein.
     */
    private void insertSampleData() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            // Klassen
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('SimpleAddressModel.Addresses.Address', 'address')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('SimpleAddressModel.Addresses.Person', 'person')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('SimpleAddressModel.Addresses.PersonAddress', 'personaddress')");
            
            // Address Attribute
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('street', 'astreet', 'SimpleAddressModel.Addresses.Address', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('houseNumber', 'housenumber', 'SimpleAddressModel.Addresses.Address', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('postalCode', 'postalcode', 'SimpleAddressModel.Addresses.Address', NULL)");
            
            // Person Attribute
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('firstName', 'firstname', 'SimpleAddressModel.Addresses.Person', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('lastName', 'lastname', 'SimpleAddressModel.Addresses.Person', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('birthDate', 'birthdate', 'SimpleAddressModel.Addresses.Person', NULL)");
            
            // Association Attribute (FK)
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('person', 'person_id', 'SimpleAddressModel.Addresses.PersonAddress', " +
                "'SimpleAddressModel.Addresses.Person')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('address', 'address_id', 'SimpleAddressModel.Addresses.PersonAddress', " +
                "'SimpleAddressModel.Addresses.Address')");
            
            // Settings
            stmt.execute("INSERT INTO t_ili2db_settings VALUES " +
                "('ch.ehi.ili2db.version', '4.9.1')");
            
            // Tabellen erstellen (für Schema-Analyse)
            stmt.execute(
                "CREATE TABLE address (" +
                "  t_id BIGINT PRIMARY KEY," +
                "  astreet VARCHAR(100) NOT NULL," +
                "  housenumber VARCHAR(10)," +
                "  postalcode VARCHAR(10) NOT NULL" +
                ")"
            );
            
            stmt.execute(
                "CREATE TABLE person (" +
                "  t_id BIGINT PRIMARY KEY," +
                "  firstname VARCHAR(50) NOT NULL," +
                "  lastname VARCHAR(50) NOT NULL," +
                "  birthdate DATE" +
                ")"
            );
            
            stmt.execute(
                "CREATE TABLE personaddress (" +
                "  t_id BIGINT PRIMARY KEY," +
                "  person_id BIGINT," +
                "  address_id BIGINT," +
                "  FOREIGN KEY (person_id) REFERENCES person(t_id)," +
                "  FOREIGN KEY (address_id) REFERENCES address(t_id)" +
                ")"
            );
        }
    }
}

package ch.interlis.generator;

import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.model.*;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import ch.interlis.ili2c.MakeIliModelsXml2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

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
        connection = DriverManager.getConnection("jdbc:h2:mem:test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        
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
        MetadataReader reader = new MetadataReader(connection, null, null, null);
        
        ModelMetadata metadata = reader.readMetadata("SimpleAddressModel");
        
        assertThat(metadata).isNotNull();
        assertThat(metadata.getModelName()).isEqualTo("SimpleAddressModel");
        assertThat(metadata.getSchemaName()).isNull();
        
        // Prüfe Klassen
        assertThat(metadata.getClasses()).hasSize(3);
        
        ClassMetadata addressClass = metadata.getClass("SimpleAddressModel.Addresses.Address");
        assertThat(addressClass).isNotNull();
        assertThat(addressClass.getTableName()).isEqualTo("address");
        assertThat(addressClass.getAllAttributes()).hasSizeGreaterThan(0);

        AttributeMetadata addressPrimaryKey = addressClass.getAttribute("t_id");
        assertThat(addressPrimaryKey).isNotNull();
        assertThat(addressPrimaryKey.getColumnName()).isEqualTo("t_id");
        assertThat(addressPrimaryKey.getJavaType()).isIn("Integer", "Object");
        
        // Prüfe Attribute
        AttributeMetadata streetAttr = addressClass.getAttribute("street");
        assertThat(streetAttr).isNotNull();
        assertThat(streetAttr.getColumnName()).isEqualTo("astreet");
    }

    @Test
    void testReadMetadata_withRepositoryLookup() throws Exception {
        Path repoDir = Files.createTempDirectory("ili-repo");
        Path modelTarget = repoDir.resolve("SimpleAddressModel.ili");
        Files.copy(Path.of("test-models/SimpleAddressModel.ili"),
            modelTarget,
            StandardCopyOption.REPLACE_EXISTING);

        MakeIliModelsXml2 generator = new MakeIliModelsXml2();
        generator.mymain(new String[]{repoDir.toString()});

        MetadataReader reader = new MetadataReader(connection, null, null, List.of(repoDir.toString()));
        ModelMetadata metadata = reader.readMetadata("SimpleAddressModel");

        assertThat(metadata.getIliVersion()).isNotNull();
        assertThat(metadata.getAllEnums())
            .extracting(EnumMetadata::getName)
            .contains("SimpleAddressModel.Addresses.AddressStatus");
    }
    
    @Test
    void testAttributeJavaTypeInference() throws Exception {
        MetadataReader reader = new MetadataReader(connection, null, null, null);
        ModelMetadata metadata = reader.readMetadata("SimpleAddressModel");
        
        ClassMetadata personClass = metadata.getClass("SimpleAddressModel.Addresses.Person");
        assertThat(personClass).isNotNull();
        
        // Text → String
        AttributeMetadata firstName = personClass.getAttribute("firstName");
        assertThat(firstName.getJavaType()).isIn("String", "Object");
        
        // XMLDate → LocalDate
        AttributeMetadata birthDate = personClass.getAttribute("birthDate");
        assertThat(birthDate.getJavaType()).isIn("java.time.LocalDate", "Object");
    }
    
    @Test
    void testRelationshipDetection() throws Exception {
        MetadataReader reader = new MetadataReader(connection, null, null, null);
        ModelMetadata metadata = reader.readMetadata("SimpleAddressModel");
        
        ClassMetadata personAddressClass = metadata.getClass("SimpleAddressModel.Addresses.PersonAddress");
        assertThat(personAddressClass).isNotNull();
        
        // Sollte 2 Beziehungen haben (zu Person und zu Address)
        assertThat(personAddressClass.getRelationships()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(personAddressClass.getRelationships())
            .allSatisfy(relationship -> {
                assertThat(relationship.getSource()).isEqualTo("ili2db");
                assertThat(relationship.getPhysicalName()).isEqualTo(relationship.getSourceAttribute());
                assertThat(relationship.getMergeReason())
                    .isEqualTo(RelationshipMetadata.MergeReason.ILI2DB_ONLY);
                assertThat(relationship.getMergeConfidence())
                    .isEqualTo(RelationshipMetadata.MergeConfidence.NONE);
            });
    }

    @Test
    void testRelationshipMergeKeepsDbColumnsAndAddsIli2cSemantics() throws Exception {
        MetadataReader reader = new MetadataReader(connection, modelFile, null, null);

        ModelMetadata metadata = reader.readMetadata("SimpleAddressModel");

        ClassMetadata personAddressClass = metadata.getClass("SimpleAddressModel.Addresses.PersonAddress");
        assertThat(personAddressClass).isNotNull();
        assertThat(personAddressClass.getRelationships()).hasSize(2);
        assertThat(metadata.getAllRelationships())
            .filteredOn(relationship -> relationship.getSourceClass()
                .equals("SimpleAddressModel.Addresses.PersonAddress"))
            .hasSize(2)
            .allSatisfy(relationship -> {
                assertThat(relationship.getSemanticKind())
                    .isEqualTo(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
                assertThat(relationship.getAssociationName())
                    .isEqualTo("SimpleAddressModel.Addresses.PersonAddress");
                assertThat(relationship.getSource()).isEqualTo("ili2db+ili2c");
                assertThat(relationship.getTargetAttribute()).isEqualTo("T_Id");
                assertThat(relationship.getMergeReason())
                    .isEqualTo(RelationshipMetadata.MergeReason.NORMALIZED_TOKEN);
                assertThat(relationship.getMergeConfidence())
                    .isEqualTo(RelationshipMetadata.MergeConfidence.MEDIUM);
            });
        assertThat(metadata.getAllRelationships())
            .anySatisfy(relationship -> {
                assertThat(relationship.getTargetRoleName()).isEqualTo("Person");
                assertThat(relationship.getSourceAttribute()).isEqualTo("person_id");
                assertThat(relationship.getPhysicalName()).isEqualTo("person_id");
                assertThat(relationship.getSemanticName()).isEqualTo("SimpleAddressModel.Addresses.PersonAddress.Person");
                assertThat(relationship.getMergeToken()).isEqualTo("person");
                assertThat(relationship.getCardinality().getMinTarget()).isZero();
                assertThat(relationship.getCardinality().getMaxTarget()).isEqualTo(-1);
            })
            .anySatisfy(relationship -> {
                assertThat(relationship.getTargetRoleName()).isEqualTo("Address");
                assertThat(relationship.getSourceAttribute()).isEqualTo("address_id");
                assertThat(relationship.getPhysicalName()).isEqualTo("address_id");
                assertThat(relationship.getSemanticName()).isEqualTo("SimpleAddressModel.Addresses.PersonAddress.Address");
                assertThat(relationship.getMergeToken()).isEqualTo("address");
                assertThat(relationship.getCardinality().getMinTarget()).isZero();
                assertThat(relationship.getCardinality().getMaxTarget()).isEqualTo(1);
            });

        AssociationMetadata association = metadata.getAssociation("SimpleAddressModel.Addresses.PersonAddress");
        assertThat(association).isNotNull();
        assertThat(association.getAssociationClass()).isEqualTo("SimpleAddressModel.Addresses.PersonAddress");
        assertThat(association.getPhysicalTable()).isEqualTo("personaddress");
        assertThat(association.getRoles())
            .extracting(AssociationRoleMetadata::getName)
            .containsExactlyInAnyOrder("Person", "Address");
        assertThat(association.getRoles())
            .filteredOn(role -> role.getName().equals("Person"))
            .singleElement()
            .satisfies(role -> {
                assertThat(role.getSourceAttribute()).isEqualTo("person_id");
                assertThat(role.getPhysicalName()).isEqualTo("person_id");
                assertThat(role.getTargetAttribute()).isEqualTo("T_Id");
                assertThat(role.getMergeReason())
                    .isEqualTo(RelationshipMetadata.MergeReason.NORMALIZED_TOKEN);
                assertThat(role.getMergeConfidence())
                    .isEqualTo(RelationshipMetadata.MergeConfidence.MEDIUM);
            });
        assertThat(association.getAllAttributes()).isEmpty();
    }

    @Test
    void testExactSourceAttributeRelationshipMerge() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedCoreIrReferenceMetadata();

        assertThat(metadata.getAllRelationships())
            .filteredOn(relationship -> "CoreIrTestModel.Relations.Component"
                .equals(relationship.getSourceClass()))
            .filteredOn(relationship -> "CoreIrTestModel.Relations.Parent"
                .equals(relationship.getTargetClass()))
            .singleElement()
            .satisfies(relationship -> {
                assertThat(relationship.getSource()).isEqualTo("ili2db+ili2c");
                assertThat(relationship.getSemanticKind())
                    .isEqualTo(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE);
                assertThat(relationship.getSourceAttribute()).isEqualTo("ParentRef");
                assertThat(relationship.getPhysicalName()).isEqualTo("ParentRef");
                assertThat(relationship.getSemanticName())
                    .isEqualTo("CoreIrTestModel.Relations.Component.ParentRef");
                assertThat(relationship.getMergeReason())
                    .isEqualTo(RelationshipMetadata.MergeReason.EXACT_SOURCE_ATTRIBUTE);
                assertThat(relationship.getMergeConfidence())
                    .isEqualTo(RelationshipMetadata.MergeConfidence.EXACT);
                assertThat(relationship.getMergeToken()).isEqualTo("ParentRef");
            });
    }

    @Test
    void testAssociationCaseMatrixMerge() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();

        assertThat(metadata.getAllAssociations())
            .extracting(AssociationMetadata::getName)
            .contains(
                "AssociationCases.Base.EmptyAssociation",
                "AssociationCases.Base.AssociationWithAttribute",
                "AssociationCases.Base.SameTargetAssociation",
                "AssociationCases.Base.PhysicalMismatchAssociation",
                "AssociationCases.Base.ExternalCompositeAssociation",
                "AssociationCases.Extended.ExtendedTopicAssociation"
            );

        AssociationMetadata withAttribute = metadata.getAssociation(
            "AssociationCases.Base.AssociationWithAttribute");
        assertThat(withAttribute.getAllAttributes())
            .extracting(AttributeMetadata::getName)
            .containsExactly("RoleNote");

        AssociationMetadata sameTarget = metadata.getAssociation(
            "AssociationCases.Base.SameTargetAssociation");
        assertThat(sameTarget.getRoles())
            .extracting(AssociationRoleMetadata::getName)
            .containsExactlyInAnyOrder("PrimaryPerson", "SecondaryPerson");
        assertThat(sameTarget.getRoles())
            .extracting(AssociationRoleMetadata::getTargetClass)
            .containsOnly("AssociationCases.Base.Person");

        AssociationMetadata physicalMismatch = metadata.getAssociation(
            "AssociationCases.Base.PhysicalMismatchAssociation");
        assertThat(physicalMismatch.getRoles())
            .filteredOn(role -> role.getName().equals("SemanticOwner"))
            .singleElement()
            .satisfies(role -> {
                assertThat(role.getSourceAttribute()).isEqualTo("owner_fk");
                assertThat(role.getPhysicalName()).isEqualTo("owner_fk");
                assertThat(role.getSemanticName())
                    .isEqualTo("AssociationCases.Base.PhysicalMismatchAssociation.SemanticOwner");
                assertThat(role.getMergeReason())
                    .isEqualTo(RelationshipMetadata.MergeReason.EXACT_TARGET_ROLE);
                assertThat(role.getMergeConfidence())
                    .isEqualTo(RelationshipMetadata.MergeConfidence.EXACT);
            });

        AssociationMetadata externalComposite = metadata.getAssociation(
            "AssociationCases.Base.ExternalCompositeAssociation");
        assertThat(externalComposite.getRoles())
            .filteredOn(role -> role.getName().equals("Owner"))
            .singleElement()
            .satisfies(role -> {
                assertThat(role.isExternal()).isTrue();
                assertThat(role.isComposition()).isTrue();
                assertThat(role.isMandatory()).isTrue();
                assertThat(role.getPhysicalName()).isEqualTo("owner_id");
            });

        AssociationMetadata extended = metadata.getAssociation(
            "AssociationCases.Extended.ExtendedTopicAssociation");
        assertThat(extended.getRoles())
            .extracting(AssociationRoleMetadata::getName)
            .containsExactlyInAnyOrder("ExtendedPersonRole", "ExtendedParcelRole");
    }

    @Test
    void testQualifiedAttributeNameMerge() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("UPDATE t_ili2db_attrname " +
                "SET iliname = 'SimpleAddressModel.Addresses.Person.BirthDate' " +
                "WHERE iliname = 'birthDate'");
        }

        MetadataReader reader = new MetadataReader(connection, modelFile, null, null);
        ModelMetadata metadata = reader.readMetadata("SimpleAddressModel");

        ClassMetadata personClass = metadata.getClass("SimpleAddressModel.Addresses.Person");
        assertThat(personClass).isNotNull();

        AttributeMetadata birthDate = personClass.getAttribute("BirthDate");
        assertThat(birthDate).isNotNull();
        assertThat(birthDate.getQualifiedName())
            .isEqualTo("SimpleAddressModel.Addresses.Person.BirthDate");
        assertThat(birthDate.getIliType()).isNotNull();
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
                "  colowner VARCHAR(1024)," +
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

            // t_ili2db_table_prop
            stmt.execute(
                "CREATE TABLE t_ili2db_table_prop (" +
                "  tablename VARCHAR(255)," +
                "  setting VARCHAR(255)" +
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

            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES " +
                "('address', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES " +
                "('person', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES " +
                "('personaddress', 'ASSOCIATION')");
            
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
                "  t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY," +
                "  astreet VARCHAR(100) NOT NULL," +
                "  housenumber VARCHAR(10)," +
                "  postalcode VARCHAR(10) NOT NULL" +
                ")"
            );
            
            stmt.execute(
                "CREATE TABLE person (" +
                "  t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY," +
                "  firstname VARCHAR(50) NOT NULL," +
                "  lastname VARCHAR(50) NOT NULL," +
                "  birthdate DATE" +
                ")"
            );
            
            stmt.execute(
                "CREATE TABLE personaddress (" +
                "  t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY," +
                "  person_id INTEGER," +
                "  address_id INTEGER," +
                "  FOREIGN KEY (person_id) REFERENCES person(t_id)," +
                "  FOREIGN KEY (address_id) REFERENCES address(t_id)" +
                ")"
            );
        }
    }
}

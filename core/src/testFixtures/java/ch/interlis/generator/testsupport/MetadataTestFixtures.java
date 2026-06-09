package ch.interlis.generator.testsupport;

import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.model.ModelMetadata;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class MetadataTestFixtures {

    private MetadataTestFixtures() {
    }

    public static ModelMetadata readMergedSimpleAddressMetadata() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:simple_address_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"
        )) {
            createSimpleAddressIli2dbFixture(connection);
            return new MetadataReader(
                connection,
                new File("test-models/SimpleAddressModel.ili"),
                null,
                null
            ).readMetadata("SimpleAddressModel");
        }
    }

    public static ModelMetadata readMergedCoreIrReferenceMetadata() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:core_ir_reference_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"
        )) {
            createCoreIrReferenceIli2dbFixture(connection);
            return new MetadataReader(
                connection,
                new File("test-models/CoreIrTestModel.ili"),
                null,
                null
            ).readMetadata("CoreIrTestModel");
        }
    }

    public static ModelMetadata readMergedAssociationCasesMetadata() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:association_cases_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"
        )) {
            createAssociationCasesIli2dbFixture(connection);
            return new MetadataReader(
                connection,
                new File("test-models/AssociationCases.ili"),
                null,
                null
            ).readMetadata("AssociationCases");
        }
    }

    public static void createSimpleAddressIli2dbFixture(Connection connection) throws Exception {
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
                "CREATE TABLE t_ili2db_model (" +
                    "modelname VARCHAR(1024), " +
                    "content VARCHAR(1024)" +
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
            stmt.execute("INSERT INTO t_ili2db_model VALUES ('SimpleAddressModel', 'model')");

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

    public static void createCoreIrReferenceIli2dbFixture(Connection connection) throws Exception {
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
                "CREATE TABLE t_ili2db_model (" +
                    "modelname VARCHAR(1024), " +
                    "content VARCHAR(1024)" +
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
                "('CoreIrTestModel.Relations.Parent', 'parent')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('CoreIrTestModel.Relations.Component', 'component')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('parent', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('component', 'STRUCTURE')");

            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('Name', 'Name', 'CoreIrTestModel.Relations.Parent', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('ParentRef', 'ParentRef', 'CoreIrTestModel.Relations.Component', " +
                    "'CoreIrTestModel.Relations.Parent')");

            stmt.execute("INSERT INTO t_ili2db_settings VALUES " +
                "('ch.ehi.ili2db.version', '4.9.1')");
            stmt.execute("INSERT INTO t_ili2db_model VALUES ('CoreIrTestModel', 'model')");

            stmt.execute(
                "CREATE TABLE parent (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "Name VARCHAR(50)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE component (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "ParentRef INTEGER, " +
                    "FOREIGN KEY (ParentRef) REFERENCES parent(t_id)" +
                    ")"
            );
        }
    }

    public static void createAssociationCasesIli2dbFixture(Connection connection) throws Exception {
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
                "CREATE TABLE t_ili2db_model (" +
                    "modelname VARCHAR(1024), " +
                    "content VARCHAR(1024)" +
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
                "('AssociationCases.Base.Person', 'person')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('AssociationCases.Base.Parcel', 'parcel')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('AssociationCases.Base.Document', 'document')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('AssociationCases.Base.Building', 'building')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('AssociationCases.Extended.ExtendedParcel', 'extendedparcel')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('AssociationCases.Base.EmptyAssociation', 'emptyassociation')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('AssociationCases.Base.AssociationWithAttribute', 'associationwithattribute')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('AssociationCases.Base.SameTargetAssociation', 'sametargetassociation')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('AssociationCases.Base.PhysicalMismatchAssociation', 'physicalmismatchassociation')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('AssociationCases.Base.ExternalCompositeAssociation', 'externalcompositeassociation')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES " +
                "('AssociationCases.Extended.ExtendedTopicAssociation', 'extendedtopicassociation')");

            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('person', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('parcel', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('document', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('building', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('extendedparcel', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('emptyassociation', 'ASSOCIATION')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('associationwithattribute', 'ASSOCIATION')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('sametargetassociation', 'ASSOCIATION')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('physicalmismatchassociation', 'ASSOCIATION')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('externalcompositeassociation', 'ASSOCIATION')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('extendedtopicassociation', 'ASSOCIATION')");

            stmt.execute("INSERT INTO t_ili2db_inheritance VALUES " +
                "('AssociationCases.Extended.ExtendedParcel', 'AssociationCases.Base.Parcel')");

            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('Name', 'name', 'AssociationCases.Base.Person', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('Ident', 'ident', 'AssociationCases.Base.Parcel', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('Title', 'title', 'AssociationCases.Base.Document', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('Name', 'name', 'AssociationCases.Base.Building', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('ExtraCode', 'extra_code', 'AssociationCases.Extended.ExtendedParcel', NULL)");

            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('PersonRole', 'person_role_id', 'AssociationCases.Base.EmptyAssociation', " +
                    "'AssociationCases.Base.Person')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('ParcelRole', 'parcel_role_id', 'AssociationCases.Base.EmptyAssociation', " +
                    "'AssociationCases.Base.Parcel')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('PersonRole', 'person_role_id', 'AssociationCases.Base.AssociationWithAttribute', " +
                    "'AssociationCases.Base.Person')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('DocumentRole', 'document_role_id', 'AssociationCases.Base.AssociationWithAttribute', " +
                    "'AssociationCases.Base.Document')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('RoleNote', 'role_note', 'AssociationCases.Base.AssociationWithAttribute', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('PrimaryPerson', 'primary_person_id', 'AssociationCases.Base.SameTargetAssociation', " +
                    "'AssociationCases.Base.Person')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('SecondaryPerson', 'secondary_person_id', 'AssociationCases.Base.SameTargetAssociation', " +
                    "'AssociationCases.Base.Person')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('SemanticOwner', 'owner_fk', 'AssociationCases.Base.PhysicalMismatchAssociation', " +
                    "'AssociationCases.Base.Person')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('OwnedParcel', 'parcel_fk', 'AssociationCases.Base.PhysicalMismatchAssociation', " +
                    "'AssociationCases.Base.Parcel')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('Owner', 'owner_id', 'AssociationCases.Base.ExternalCompositeAssociation', " +
                    "'AssociationCases.Base.Person')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('Buildings', 'building_id', 'AssociationCases.Base.ExternalCompositeAssociation', " +
                    "'AssociationCases.Base.Building')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('ExtendedPersonRole', 'ext_person_id', 'AssociationCases.Extended.ExtendedTopicAssociation', " +
                    "'AssociationCases.Base.Person')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES " +
                "('ExtendedParcelRole', 'ext_parcel_id', 'AssociationCases.Extended.ExtendedTopicAssociation', " +
                    "'AssociationCases.Extended.ExtendedParcel')");

            stmt.execute("INSERT INTO t_ili2db_settings VALUES " +
                "('ch.ehi.ili2db.version', '4.9.1')");
            stmt.execute("INSERT INTO t_ili2db_model VALUES ('AssociationCases', 'model')");

            stmt.execute(
                "CREATE TABLE person (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "name VARCHAR(50) NOT NULL" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE parcel (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "ident VARCHAR(20) NOT NULL" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE document (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "title VARCHAR(80) NOT NULL" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE building (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "name VARCHAR(40)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE extendedparcel (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "extra_code VARCHAR(20)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE emptyassociation (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "person_role_id INTEGER, " +
                    "parcel_role_id INTEGER, " +
                    "FOREIGN KEY (person_role_id) REFERENCES person(t_id), " +
                    "FOREIGN KEY (parcel_role_id) REFERENCES parcel(t_id)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE associationwithattribute (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "person_role_id INTEGER, " +
                    "document_role_id INTEGER, " +
                    "role_note VARCHAR(30), " +
                    "FOREIGN KEY (person_role_id) REFERENCES person(t_id), " +
                    "FOREIGN KEY (document_role_id) REFERENCES document(t_id)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE sametargetassociation (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "primary_person_id INTEGER, " +
                    "secondary_person_id INTEGER, " +
                    "FOREIGN KEY (primary_person_id) REFERENCES person(t_id), " +
                    "FOREIGN KEY (secondary_person_id) REFERENCES person(t_id)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE physicalmismatchassociation (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "owner_fk INTEGER NOT NULL, " +
                    "parcel_fk INTEGER, " +
                    "FOREIGN KEY (owner_fk) REFERENCES person(t_id), " +
                    "FOREIGN KEY (parcel_fk) REFERENCES parcel(t_id)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE externalcompositeassociation (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "owner_id INTEGER NOT NULL, " +
                    "building_id INTEGER, " +
                    "FOREIGN KEY (owner_id) REFERENCES person(t_id), " +
                    "FOREIGN KEY (building_id) REFERENCES building(t_id)" +
                    ")"
            );
            stmt.execute(
                "CREATE TABLE extendedtopicassociation (" +
                    "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "ext_person_id INTEGER, " +
                    "ext_parcel_id INTEGER, " +
                    "FOREIGN KEY (ext_person_id) REFERENCES person(t_id), " +
                    "FOREIGN KEY (ext_parcel_id) REFERENCES extendedparcel(t_id)" +
                    ")"
            );
        }
    }
}

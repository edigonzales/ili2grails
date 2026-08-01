package ch.interlis.generator.metadata;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MetadataPostProcessorTest {

    private final MetadataPostProcessor processor = new MetadataPostProcessor();

    @Test
    void infersMissingTypes() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata clazz = new ClassMetadata("TestModel.Topic.Class");
        AttributeMetadata attribute = new AttributeMetadata("name");
        attribute.setIliType("TEXT");
        clazz.addAttribute(attribute);
        metadata.addClass(clazz);

        processor.process(metadata);

        assertThat(attribute.getCoreType()).isEqualTo(CoreType.TEXT);
        assertThat(attribute.getJavaType()).isEqualTo("String");
    }

    @Test
    void synchronizesAssociationRolesFromCanonicalRelationships() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata associationClass = new ClassMetadata("TestModel.Topic.PersonAddress");
        associationClass.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        associationClass.setTableName("personaddress");
        AttributeMetadata roleAttribute = new AttributeMetadata("person_id");
        roleAttribute.setColumnName("person_id");
        associationClass.addAttribute(roleAttribute);
        metadata.addClass(associationClass);

        RelationshipMetadata first = canonicalAssociationRole(
            "TestModel.Topic.PersonAddress.Person", "TestModel.Topic.PersonAddress",
            "TestModel.Topic.Person", "person_id", "Person");
        RelationshipMetadata second = canonicalAssociationRole(
            "TestModel.Topic.PersonAddress.Address", "TestModel.Topic.PersonAddress",
            "TestModel.Topic.Address", "address_id", "Address");
        metadata.addRelationship(first);
        metadata.addRelationship(second);

        processor.process(metadata);

        assertThat(metadata.getAssociation("TestModel.Topic.PersonAddress"))
            .satisfies(association -> {
                assertThat(association.getRoles())
                    .extracting(role -> role.getName())
                    .containsExactly("Address", "Person");
                assertThat(association.getRoles())
                    .extracting(role -> role.getSourceAttribute())
                    .containsExactly("address_id", "person_id");
                assertThat(association.getPhysicalTable()).isEqualTo("personaddress");
            });
    }

    @Test
    void ambiguousRelationshipsAreNotUsedForRoles() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata associationClass = new ClassMetadata("TestModel.Topic.PersonAddress");
        associationClass.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        metadata.addClass(associationClass);

        RelationshipMetadata role = new RelationshipMetadata("TestModel.Topic.PersonAddress.Person");
        role.setSourceClass("TestModel.Topic.PersonAddress");
        role.setTargetClass("TestModel.Topic.Person");
        role.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
        role.setAssociationName("TestModel.Topic.PersonAddress");
        role.setTargetRoleName("Person");
        role.setSource("ili2c");
        metadata.addRelationship(role);

        processor.process(metadata);

        assertThat(metadata.getAssociation("TestModel.Topic.PersonAddress").getRoles())
            .isEmpty();
    }

    @Test
    void roleOrderIsDeterministic() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata associationClass = new ClassMetadata("TestModel.Topic.PersonAddress");
        associationClass.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        metadata.addClass(associationClass);

        RelationshipMetadata zebra = canonicalAssociationRole(
            "TestModel.Topic.PersonAddress.Zebra", "TestModel.Topic.PersonAddress",
            "TestModel.Topic.Zebra", "zebra_id", "Zebra");
        RelationshipMetadata alpha = canonicalAssociationRole(
            "TestModel.Topic.PersonAddress.Alpha", "TestModel.Topic.PersonAddress",
            "TestModel.Topic.Alpha", "alpha_id", "Alpha");
        metadata.addRelationship(zebra);
        metadata.addRelationship(alpha);

        processor.process(metadata);

        assertThat(metadata.getAssociation("TestModel.Topic.PersonAddress").getRoles())
            .extracting(role -> role.getName())
            .containsExactly("Alpha", "Zebra");
    }

    private RelationshipMetadata canonicalAssociationRole(String name,
                                                          String sourceClass,
                                                          String targetClass,
                                                          String sourceAttribute,
                                                          String targetRoleName) {
        RelationshipMetadata relationship = new RelationshipMetadata(name);
        relationship.setSourceClass(sourceClass);
        relationship.setTargetClass(targetClass);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
        relationship.setAssociationName(sourceClass);
        relationship.setTargetRoleName(targetRoleName);
        relationship.setSourceAttribute(sourceAttribute);
        relationship.setSource("ili2db+ili2c");
        return relationship;
    }
}

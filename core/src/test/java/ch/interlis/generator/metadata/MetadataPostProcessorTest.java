package ch.interlis.generator.metadata;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.model.builder.RelationshipMetadataBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MetadataPostProcessorTest {

    private final MetadataPostProcessor processor = new MetadataPostProcessor();

    @Test
    void factoryResolvesMissingTypesBeforeFreeze() {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder clazz = builder.classBuilder("TestModel.Topic.Class");
        clazz.attribute(new AttributeMetadataBuilder("name").iliType("TEXT"));

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(builder);

        AttributeMetadata attribute = metadata.getClass("TestModel.Topic.Class").getAttribute("name");
        assertThat(attribute.getCoreType()).isEqualTo(CoreType.TEXT);
        assertThat(attribute.getJavaType()).isEqualTo("String");
    }

    @Test
    void synchronizesAssociationRolesFromCanonicalRelationships() {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder associationClass = builder.classBuilder("TestModel.Topic.PersonAddress");
        associationClass.kind(ClassMetadata.ClassKind.ASSOCIATION);
        associationClass.tableName("personaddress");
        associationClass.attribute(new AttributeMetadataBuilder("person_id").columnName("person_id")
            .foreignKey(true));

        builder.relationshipBuilder("TestModel.Topic.PersonAddress.Person")
            .sourceClass("TestModel.Topic.PersonAddress")
            .targetClass("TestModel.Topic.Person")
            .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .associationName("TestModel.Topic.PersonAddress")
            .targetRoleName("Person")
            .sourceAttribute("person_id")
            .source("ili2db+ili2c");
        builder.relationshipBuilder("TestModel.Topic.PersonAddress.Address")
            .sourceClass("TestModel.Topic.PersonAddress")
            .targetClass("TestModel.Topic.Address")
            .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .associationName("TestModel.Topic.PersonAddress")
            .targetRoleName("Address")
            .sourceAttribute("address_id")
            .source("ili2db+ili2c");

        processor.process(builder);
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(builder);

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
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("TestModel");
        builder.classBuilder("TestModel.Topic.PersonAddress")
            .kind(ClassMetadata.ClassKind.ASSOCIATION);

        builder.relationshipBuilder("TestModel.Topic.PersonAddress.Person")
            .sourceClass("TestModel.Topic.PersonAddress")
            .targetClass("TestModel.Topic.Person")
            .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .associationName("TestModel.Topic.PersonAddress")
            .targetRoleName("Person")
            .source("ili2c");

        processor.process(builder);
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(builder);

        assertThat(metadata.getAssociation("TestModel.Topic.PersonAddress").getRoles())
            .isEmpty();
    }

    @Test
    void roleOrderIsDeterministic() {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("TestModel");
        builder.classBuilder("TestModel.Topic.PersonAddress")
            .kind(ClassMetadata.ClassKind.ASSOCIATION);

        canonicalAssociationRole(builder,
            "TestModel.Topic.PersonAddress.Zebra", "TestModel.Topic.Zebra", "zebra_id", "Zebra");
        canonicalAssociationRole(builder,
            "TestModel.Topic.PersonAddress.Alpha", "TestModel.Topic.Alpha", "alpha_id", "Alpha");

        processor.process(builder);
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(builder);

        assertThat(metadata.getAssociation("TestModel.Topic.PersonAddress").getRoles())
            .extracting(role -> role.getName())
            .containsExactly("Alpha", "Zebra");
    }

    private void canonicalAssociationRole(ModelMetadataBuilder builder,
                                          String name,
                                          String targetClass,
                                          String sourceAttribute,
                                          String targetRoleName) {
        builder.relationshipBuilder(name)
            .sourceClass("TestModel.Topic.PersonAddress")
            .targetClass(targetClass)
            .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .associationName("TestModel.Topic.PersonAddress")
            .targetRoleName(targetRoleName)
            .sourceAttribute(sourceAttribute)
            .source("ili2db+ili2c");
    }
}

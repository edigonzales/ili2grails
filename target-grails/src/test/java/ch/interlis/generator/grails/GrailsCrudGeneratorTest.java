package ch.interlis.generator.grails;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsCrudGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesDomainsAndEnums() throws IOException {
        ModelMetadata metadata = sampleMetadata();
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example")
            .build();

        GrailsCrudGenerator generator = new GrailsCrudGenerator();
        generator.generate(metadata, config);

        Path domainDir = tempDir.resolve("grails-app/domain/com/example");
        Path enumDir = tempDir.resolve("src/main/groovy/com/example/enums");

        assertThat(domainDir.resolve("Address.groovy")).exists();
        assertThat(enumDir.resolve("Status.groovy")).exists();

        String domainContent = Files.readString(domainDir.resolve("Address.groovy"));
        assertThat(domainContent).contains("class Address");
        assertThat(domainContent).contains("String street");
        assertThat(domainContent).contains("Status status");
        assertThat(domainContent).contains("Person person");
        assertThat(domainContent).contains("static mapping");
        assertThat(domainContent).contains("table 'address'");
        assertThat(domainContent).contains("street maxSize: 100");
        assertThat(domainContent).contains("person nullable: true");

        String personDomain = Files.readString(domainDir.resolve("Person.groovy"));
        assertThat(personDomain).doesNotContain("static hasMany");
        assertThat(personDomain).doesNotContain("addresses: Address");
    }

    @Test
    void usesCollisionFreeTargetNamesInDomainsEnumsAndRelations() throws IOException {
        ModelMetadata metadata = namingCollisionMetadata();
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .build();

        new GrailsCrudGenerator().generate(metadata, config);

        Path domainDir = tempDir.resolve("grails-app/domain/com/example/domain");
        Path enumDir = tempDir.resolve("src/main/groovy/com/example/enums");
        Path topicAFile = domainDir.resolve("TopicAGebaeude.groovy");
        Path topicBFile = domainDir.resolve("TopicBGebaeude.groovy");

        assertThat(topicAFile).exists();
        assertThat(topicBFile).exists();
        assertThat(enumDir.resolve("TopicAStatus.groovy")).exists();
        assertThat(enumDir.resolve("TopicBStatus.groovy")).exists();

        String topicADomain = Files.readString(topicAFile);
        assertThat(topicADomain).contains("class TopicAGebaeude");
        assertThat(topicADomain).contains("import com.example.enums.TopicAStatus");
        assertThat(topicADomain).contains("TopicAStatus status");
        assertThat(topicADomain).doesNotContain("static hasMany");
        assertThat(topicADomain).contains(
            "interlisInverseRelationshipMeta");
        assertThat(topicADomain).contains(
            "topicBGebaeudes: [relatedDomainClass: 'com.example.domain.TopicBGebaeude'");

        String topicBDomain = Files.readString(topicBFile);
        assertThat(topicBDomain).contains("class TopicBGebaeude");
        assertThat(topicBDomain).contains("import com.example.enums.TopicBStatus");
        assertThat(topicBDomain).contains("TopicBStatus status");
        assertThat(topicBDomain).contains("TopicAGebaeude owner");
        assertThat(topicBDomain).doesNotContain("static belongsTo");
    }

    private ModelMetadata sampleMetadata() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");

        modelBuilder.enumBuilder("TestModel.Status")
            .value("ACTIVE", 0)
            .value("INACTIVE", 1);

        modelBuilder.classBuilder("TestModel.Person")
            .tableName("person")
            .attribute(new AttributeMetadataBuilder("name")
                .javaType("String")
                .mandatory(true));

        modelBuilder.classBuilder("TestModel.Address")
            .tableName("address")
            .attribute(new AttributeMetadataBuilder("street")
                .javaType("String")
                .maxLength(100)
                .mandatory(true))
            .attribute(new AttributeMetadataBuilder("status")
                .enumType("TestModel.Status")
                .javaType("String")
                .mandatory(false))
            .attribute(new AttributeMetadataBuilder("person")
                .foreignKey(true)
                .referencedClass("TestModel.Person")
                .javaType("Long")
                .mandatory(false));

        modelBuilder.relationship(RelationshipMetadata.builder("Address_Person")
            .sourceClass("TestModel.Address")
            .targetClass("TestModel.Person")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE));

        return new ModelMetadataFactory().buildValidated(modelBuilder);
    }

    private ModelMetadata namingCollisionMetadata() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");

        modelBuilder.enumBuilder("TestModel.TopicA.Status")
            .value("ACTIVE", 0);
        modelBuilder.enumBuilder("TestModel.TopicB.Status")
            .value("ACTIVE", 0);

        modelBuilder.classBuilder("TestModel.TopicA.Gebaeude")
            .tableName("gebaeude_a")
            .attribute(new AttributeMetadataBuilder("status")
                .enumType("TestModel.TopicA.Status")
                .mandatory(true));

        ClassMetadataBuilder topicBGebaeude = modelBuilder.classBuilder("TestModel.TopicB.Gebaeude")
            .tableName("gebaeude_b")
            .attribute(new AttributeMetadataBuilder("status")
                .enumType("TestModel.TopicB.Status")
                .mandatory(true))
            .attribute(new AttributeMetadataBuilder("owner")
                .foreignKey(true)
                .referencedClass("TestModel.TopicA.Gebaeude")
                .columnName("owner")
                .sqlName("owner")
                .mandatory(false));

        modelBuilder.relationship(RelationshipMetadata.builder("TopicB_Gebaeude_owner")
            .sourceClass(topicBGebaeude.name())
            .targetClass("TestModel.TopicA.Gebaeude")
            .sourceAttribute("owner")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK));

        return new ModelMetadataFactory().buildValidated(modelBuilder);
    }
}

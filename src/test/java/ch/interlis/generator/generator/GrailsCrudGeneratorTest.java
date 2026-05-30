package ch.interlis.generator.generator;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        assertThat(personDomain).contains("static hasMany");
        assertThat(personDomain).contains("addresses: Address");
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
        assertThat(topicADomain).contains("static hasMany = [topicBGebaeudes: TopicBGebaeude]");

        String topicBDomain = Files.readString(topicBFile);
        assertThat(topicBDomain).contains("class TopicBGebaeude");
        assertThat(topicBDomain).contains("import com.example.enums.TopicBStatus");
        assertThat(topicBDomain).contains("TopicBStatus status");
        assertThat(topicBDomain).contains("TopicAGebaeude owner");
        assertThat(topicBDomain).doesNotContain("static belongsTo");
    }

    private ModelMetadata sampleMetadata() {
        ModelMetadata metadata = new ModelMetadata("TestModel");

        EnumMetadata statusEnum = new EnumMetadata("TestModel.Status");
        statusEnum.setValues(List.of(
            new EnumMetadata.EnumValue("ACTIVE", 0),
            new EnumMetadata.EnumValue("INACTIVE", 1)
        ));
        metadata.addEnum(statusEnum);

        ClassMetadata person = new ClassMetadata("TestModel.Person");
        person.setTableName("person");
        AttributeMetadata name = new AttributeMetadata("name");
        name.setJavaType("String");
        name.setMandatory(true);
        person.addAttribute(name);

        ClassMetadata address = new ClassMetadata("TestModel.Address");
        address.setTableName("address");
        AttributeMetadata street = new AttributeMetadata("street");
        street.setJavaType("String");
        street.setMaxLength(100);
        street.setMandatory(true);
        address.addAttribute(street);

        AttributeMetadata status = new AttributeMetadata("status");
        status.setEnumType("TestModel.Status");
        status.setJavaType("String");
        status.setMandatory(false);
        address.addAttribute(status);

        AttributeMetadata personRef = new AttributeMetadata("person");
        personRef.setForeignKey(true);
        personRef.setReferencedClass("TestModel.Person");
        personRef.setJavaType("Long");
        personRef.setMandatory(false);
        address.addAttribute(personRef);

        RelationshipMetadata relationship = new RelationshipMetadata("Address_Person");
        relationship.setSourceClass("TestModel.Address");
        relationship.setTargetClass("TestModel.Person");
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        address.addRelationship(relationship);

        metadata.addClass(person);
        metadata.addClass(address);

        return metadata;
    }

    private ModelMetadata namingCollisionMetadata() {
        ModelMetadata metadata = new ModelMetadata("TestModel");

        EnumMetadata topicAStatus = new EnumMetadata("TestModel.TopicA.Status");
        topicAStatus.setValues(List.of(new EnumMetadata.EnumValue("ACTIVE", 0)));
        metadata.addEnum(topicAStatus);

        EnumMetadata topicBStatus = new EnumMetadata("TestModel.TopicB.Status");
        topicBStatus.setValues(List.of(new EnumMetadata.EnumValue("ACTIVE", 0)));
        metadata.addEnum(topicBStatus);

        ClassMetadata topicAGebaeude = new ClassMetadata("TestModel.TopicA.Gebaeude");
        topicAGebaeude.setTableName("gebaeude_a");
        AttributeMetadata statusA = new AttributeMetadata("status");
        statusA.setEnumType(topicAStatus.getName());
        statusA.setMandatory(true);
        topicAGebaeude.addAttribute(statusA);

        ClassMetadata topicBGebaeude = new ClassMetadata("TestModel.TopicB.Gebaeude");
        topicBGebaeude.setTableName("gebaeude_b");
        AttributeMetadata statusB = new AttributeMetadata("status");
        statusB.setEnumType(topicBStatus.getName());
        statusB.setMandatory(true);
        topicBGebaeude.addAttribute(statusB);
        AttributeMetadata owner = new AttributeMetadata("owner");
        owner.setForeignKey(true);
        owner.setReferencedClass(topicAGebaeude.getName());
        owner.setMandatory(false);
        topicBGebaeude.addAttribute(owner);

        RelationshipMetadata relationship = new RelationshipMetadata("TopicB_Gebaeude_owner");
        relationship.setSourceClass(topicBGebaeude.getName());
        relationship.setTargetClass(topicAGebaeude.getName());
        relationship.setSourceAttribute("owner");
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        metadata.addRelationship(relationship);

        metadata.addClass(topicAGebaeude);
        metadata.addClass(topicBGebaeude);

        return metadata;
    }
}

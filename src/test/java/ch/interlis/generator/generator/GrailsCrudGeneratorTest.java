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
    void generatesDomainsControllersAndViews() throws IOException {
        ModelMetadata metadata = sampleMetadata();
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example")
            .build();

        GrailsCrudGenerator generator = new GrailsCrudGenerator();
        generator.generate(metadata, config);

        Path domainDir = tempDir.resolve("grails-app/domain/com/example");
        Path controllerDir = tempDir.resolve("grails-app/controllers/com/example");
        Path enumDir = tempDir.resolve("src/main/groovy/com/example/enums");
        Path viewDir = tempDir.resolve("grails-app/views/address");

        assertThat(domainDir.resolve("Address.groovy")).exists();
        assertThat(controllerDir.resolve("AddressController.groovy")).exists();
        assertThat(enumDir.resolve("Status.groovy")).exists();
        assertThat(viewDir.resolve("create.gsp")).exists();
        assertThat(viewDir.resolve("edit.gsp")).exists();
        assertThat(viewDir.resolve("list.gsp")).exists();
        assertThat(viewDir.resolve("show.gsp")).exists();

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
}

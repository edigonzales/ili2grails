package ch.interlis.generator.generator;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsDomainGeneratorTest {

    @Test
    void rendersForeignKeyColumnAndBelongsTo(@TempDir Path tempDir) throws Exception {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata address = new ClassMetadata("TestModel.Address");
        address.setTableName("address");
        address.addAttribute(primaryKeyAttribute());
        metadata.addClass(address);

        ClassMetadata person = new ClassMetadata("TestModel.Person");
        person.setTableName("person");
        person.addAttribute(primaryKeyAttribute());
        AttributeMetadata addressAttribute = new AttributeMetadata("address");
        addressAttribute.setSqlName("address");
        addressAttribute.setColumnName("address");
        addressAttribute.setForeignKey(true);
        addressAttribute.setReferencedClass(address.getName());
        person.addAttribute(addressAttribute);
        metadata.addClass(person);

        RelationshipMetadata relationship = new RelationshipMetadata("person_address");
        relationship.setSourceClass(person.getName());
        relationship.setTargetClass(address.getName());
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        person.addRelationship(relationship);

        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example").build();
        new GrailsDomainGenerator().generate(metadata, config);

        Path personDomain = tempDir.resolve("grails-app/domain/com/example/Person.groovy");
        String content = Files.readString(personDomain);

        assertThat(content).contains("Address address");
        assertThat(content).contains("static belongsTo = [address: Address]");
        assertThat(content).contains("address column: 'address'");
        assertThat(content).doesNotContain("address_id");
    }

    private AttributeMetadata primaryKeyAttribute() {
        AttributeMetadata attribute = new AttributeMetadata("t_id");
        attribute.setSqlName("t_id");
        attribute.setColumnName("t_id");
        attribute.setPrimaryKey(true);
        return attribute;
    }
}

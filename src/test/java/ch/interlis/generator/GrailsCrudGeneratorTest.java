package ch.interlis.generator;

import ch.interlis.generator.grails.GenerationConfig;
import ch.interlis.generator.grails.GrailsCrudGenerator;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsCrudGeneratorTest {

    @Test
    void generatesGrailsProjectStructure(@TempDir Path tempDir) throws Exception {
        ModelMetadata metadata = buildSampleMetadata();
        Path outputDir = tempDir.resolve("generated-grails-app");
        GenerationConfig config = GenerationConfig.builder(outputDir, "com.example")
            .domainPackage("com.example.domain")
            .controllerPackage("com.example.controller")
            .enumPackage("com.example.enums")
            .build();

        new GrailsCrudGenerator().generate(metadata, config);

        Path domainFile = outputDir.resolve("grails-app/domain/com/example/domain/Person.groovy");
        Path enumFile = outputDir.resolve("src/main/groovy/com/example/enums/Status.groovy");

        assertThat(domainFile).exists();
        assertThat(enumFile).exists();

        String domainContent = Files.readString(domainFile);
        assertThat(domainContent).contains("package com.example.domain");
        assertThat(domainContent).contains("class Person");
        assertThat(domainContent).contains("table 'person_tbl'");
        assertThat(domainContent).contains("id column: 't_id'");
        assertThat(domainContent).contains("firstName column: 'first_name'");
        assertThat(domainContent).contains("firstName maxSize: 40");
        assertThat(domainContent).contains("status nullable: true");
        assertThat(domainContent).contains("static hasMany = [personAddresses: PersonAddress]");

        String enumContent = Files.readString(enumFile);
        assertThat(enumContent).contains("package com.example.enums");
        assertThat(enumContent).contains("enum Status");
        assertThat(enumContent).contains("active, inactive");
    }

    @Test
    void rendersGeometryMetaWhenGeometryFieldIsPresent(@TempDir Path tempDir) throws Exception {
        ModelMetadata metadata = new ModelMetadata("SampleModel");

        ClassMetadata address = new ClassMetadata("SampleModel.Address");
        address.setTableName("address_tbl");
        address.addAttribute(primaryKeyAttribute("id", "t_id"));

        AttributeMetadata geometry = new AttributeMetadata("position");
        geometry.setColumnName("position");
        geometry.setGeometry(true);
        geometry.setGeometryKind("POINT");
        geometry.setGeometrySrid(2056);
        geometry.setJavaType("org.locationtech.jts.geom.Geometry");
        address.addAttribute(geometry);
        metadata.addClass(address);

        Path outputDir = tempDir.resolve("generated-grails-app");
        GenerationConfig config = GenerationConfig.builder(outputDir, "com.example")
            .domainPackage("com.example.domain")
            .controllerPackage("com.example.controller")
            .enumPackage("com.example.enums")
            .geometryEnabled(true)
            .build();

        new GrailsCrudGenerator().generate(metadata, config);

        Path domainFile = outputDir.resolve("grails-app/domain/com/example/domain/Address.groovy");
        String domainContent = Files.readString(domainFile);
        assertThat(domainContent).contains("Geometry position");
        assertThat(domainContent).contains("static final Map<String, Map<String, Object>> geometryMeta");
        assertThat(domainContent).contains("position: [srid: 2056, kind: 'POINT']");
    }

    @Test
    void setsExplicitTIdMappingWhenNoIdAttributeExists(@TempDir Path tempDir) throws Exception {
        ModelMetadata metadata = new ModelMetadata("SampleModel");
        ClassMetadata log = new ClassMetadata("SampleModel.LogEntry");
        log.setTableName("log_entry");

        AttributeMetadata tId = new AttributeMetadata("tId");
        tId.setColumnName("t_id");
        tId.setJavaType("Long");
        log.addAttribute(tId);
        log.addAttribute(textAttribute("message", "message", 255, true));
        metadata.addClass(log);

        Path outputDir = tempDir.resolve("generated-grails-app");
        GenerationConfig config = GenerationConfig.builder(outputDir, "com.example")
            .domainPackage("com.example.domain")
            .controllerPackage("com.example.controller")
            .enumPackage("com.example.enums")
            .build();

        new GrailsCrudGenerator().generate(metadata, config);

        Path domainFile = outputDir.resolve("grails-app/domain/com/example/domain/LogEntry.groovy");
        String domainContent = Files.readString(domainFile);
        assertThat(domainContent).contains("id column: 't_id'");
    }

    private ModelMetadata buildSampleMetadata() {
        ModelMetadata metadata = new ModelMetadata("SampleModel");

        EnumMetadata statusEnum = new EnumMetadata("SampleModel.Status");
        statusEnum.addValue(new EnumMetadata.EnumValue("active", 0));
        statusEnum.addValue(new EnumMetadata.EnumValue("inactive", 1));
        metadata.addEnum(statusEnum);

        ClassMetadata person = new ClassMetadata("SampleModel.Person");
        person.setTableName("person_tbl");
        person.addAttribute(primaryKeyAttribute("id", "t_id"));
        person.addAttribute(textAttribute("firstName", "first_name", 40, true));
        person.addAttribute(enumAttribute("status", statusEnum.getName(), false));
        person.addAttribute(dateAttribute("birthDate"));
        metadata.addClass(person);

        ClassMetadata personAddress = new ClassMetadata("SampleModel.PersonAddress");
        personAddress.setTableName("person_address");
        personAddress.addAttribute(primaryKeyAttribute("id", "t_id"));
        AttributeMetadata personFk = new AttributeMetadata("person");
        personFk.setColumnName("person_id");
        personFk.setForeignKey(true);
        personFk.setReferencedClass(person.getName());
        personFk.setMandatory(true);
        personAddress.addAttribute(personFk);

        RelationshipMetadata rel = new RelationshipMetadata("PersonAddressToPerson");
        rel.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        rel.setSourceClass(personAddress.getName());
        rel.setTargetClass(person.getName());
        personAddress.addRelationship(rel);
        metadata.addClass(personAddress);

        return metadata;
    }

    private AttributeMetadata primaryKeyAttribute(String name, String columnName) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setPrimaryKey(true);
        attribute.setColumnName(columnName);
        attribute.setJavaType("Long");
        return attribute;
    }

    private AttributeMetadata textAttribute(String name, String columnName, int maxLength, boolean mandatory) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setColumnName(columnName);
        attribute.setJavaType("String");
        attribute.setMaxLength(maxLength);
        attribute.setMandatory(mandatory);
        return attribute;
    }

    private AttributeMetadata enumAttribute(String name, String enumType, boolean mandatory) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setEnumType(enumType);
        attribute.setJavaType("String");
        attribute.setMandatory(mandatory);
        return attribute;
    }

    private AttributeMetadata dateAttribute(String name) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setIliType("INTERLIS.XMLDate");
        return attribute;
    }
}

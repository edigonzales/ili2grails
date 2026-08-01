package ch.interlis.generator;

import ch.interlis.generator.grails.GenerationConfig;
import ch.interlis.generator.grails.GrailsCrudGenerator;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
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
        Path uiRegistryFile = outputDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisUiRegistry.groovy");

        assertThat(domainFile).exists();
        assertThat(enumFile).exists();
        assertThat(uiRegistryFile).exists();

        String domainContent = Files.readString(domainFile);
        assertThat(domainContent).contains("package com.example.domain");
        assertThat(domainContent).contains("class Person");
        assertThat(domainContent).contains("table 'person_tbl'");
        assertThat(domainContent).contains("id column: 't_id'");
        assertThat(domainContent).contains("firstName column: 'first_name'");
        assertThat(domainContent).contains("firstName maxSize: 40");
        assertThat(domainContent).contains("status nullable: true");
        assertThat(domainContent).doesNotContain("static hasMany");
        assertThat(domainContent).contains(
            "static final Map<String, Map<String, Object>> interlisInverseRelationshipMeta");
        assertThat(domainContent).contains("personAddresses:");

        String enumContent = Files.readString(enumFile);
        assertThat(enumContent).contains("package com.example.enums");
        assertThat(enumContent).contains("enum Status");
        assertThat(enumContent).contains("active, inactive");
        assertThat(Files.readString(uiRegistryFile)).contains("'SampleModel.Person'");
        assertThat(Files.readString(uiRegistryFile)).contains("implements DomainRegistry");
    }

    @Test
    void rendersGeometryMetaWhenGeometryFieldIsPresent(@TempDir Path tempDir) throws Exception {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("SampleModel");

        modelBuilder.classBuilder("SampleModel.Address")
            .tableName("address_tbl")
            .attribute(primaryKeyAttribute("id", "t_id"))
            .attribute(new AttributeMetadataBuilder("position")
                .columnName("position")
                .geometry(true)
                .geometryKind("POINT")
                .geometrySrid(2056)
                .javaType("org.locationtech.jts.geom.Geometry"));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

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
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("SampleModel");
        modelBuilder.classBuilder("SampleModel.LogEntry")
            .tableName("log_entry")
            .attribute(new AttributeMetadataBuilder("tId")
                .columnName("t_id")
                .javaType("Long"))
            .attribute(textAttribute("message", "message", 255, true));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

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
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("SampleModel");

        modelBuilder.enumBuilder("SampleModel.Status")
            .value("active", 0)
            .value("inactive", 1);

        ClassMetadataBuilder person = modelBuilder.classBuilder("SampleModel.Person")
            .tableName("person_tbl")
            .attribute(primaryKeyAttribute("id", "t_id"))
            .attribute(textAttribute("firstName", "first_name", 40, true))
            .attribute(enumAttribute("status", "SampleModel.Status", false))
            .attribute(dateAttribute("birthDate"));

        modelBuilder.classBuilder("SampleModel.PersonAddress")
            .tableName("person_address")
            .attribute(primaryKeyAttribute("id", "t_id"))
            .attribute(new AttributeMetadataBuilder("person")
                .columnName("person_id")
                .foreignKey(true)
                .referencedClass(person.name())
                .mandatory(true));

        modelBuilder.relationship(RelationshipMetadata.builder("PersonAddressToPerson")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .sourceClass("SampleModel.PersonAddress")
            .targetClass(person.name())
            .sourceAttribute("person_id")
            .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK));

        return new ModelMetadataFactory().buildValidated(modelBuilder);
    }

    private AttributeMetadataBuilder primaryKeyAttribute(String name, String columnName) {
        return new AttributeMetadataBuilder(name)
            .primaryKey(true)
            .columnName(columnName)
            .javaType("Long");
    }

    private AttributeMetadataBuilder textAttribute(String name, String columnName, int maxLength, boolean mandatory) {
        return new AttributeMetadataBuilder(name)
            .columnName(columnName)
            .javaType("String")
            .maxLength(maxLength)
            .mandatory(mandatory);
    }

    private AttributeMetadataBuilder enumAttribute(String name, String enumType, boolean mandatory) {
        return new AttributeMetadataBuilder(name)
            .enumType(enumType)
            .javaType("String")
            .mandatory(mandatory);
    }

    private AttributeMetadataBuilder dateAttribute(String name) {
        return new AttributeMetadataBuilder(name)
            .iliType("INTERLIS.XMLDate");
    }
}

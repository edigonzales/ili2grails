package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedGrailsCompileSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void compilesGeneratedDomainsAndEnumsForCollisionModel() throws Exception {
        ModelMetadata metadata = collisionMetadata();
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .geometryEnabled(true)
            .build();

        new GrailsCrudGenerator().generate(metadata, config);

        Path domainDir = tempDir.resolve("grails-app/domain/com/example/domain");
        String workerDomain = Files.readString(domainDir.resolve("Worker.groovy"));
        assertThat(workerDomain).contains("Long baseRef");
        assertThat(workerDomain).doesNotContain("AbstractBase baseRef");
        assertThat(workerDomain).doesNotContain("belongsTo = [baseRef");

        String topicADomain = Files.readString(domainDir.resolve("TopicAGebaeude.groovy"));
        assertThat(topicADomain).doesNotContain("static hasMany");
        assertThat(topicADomain).contains("interlisInverseRelationshipMeta");

        String associationDomain = Files.readString(domainDir.resolve("GebaeudeLink.groovy"));
        assertThat(associationDomain).contains("TopicAGebaeude source");
        assertThat(associationDomain).contains("TopicBGebaeude target");

        Path registryFile = tempDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisAssociationRegistry.groovy");
        assertThat(registryFile).exists();
        String registry = Files.readString(registryFile);
        assertThat(registry).contains("package ch.interlis.generator.grails.generated");
        assertThat(registry).contains("final class InterlisAssociationRegistry");

        Path uiRegistryFile = tempDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisUiRegistry.groovy");
        assertThat(uiRegistryFile).exists();
        String uiRegistry = Files.readString(uiRegistryFile);
        assertThat(uiRegistry).contains("final class InterlisUiRegistry");
        assertThat(uiRegistry).contains("TestModel.TopicA.Gebaeude");

        GeneratedGroovyCompiler.compileGeneratedSources(tempDir);
    }

    private ModelMetadata collisionMetadata() {
        ch.interlis.generator.model.builder.ModelMetadataBuilder builder =
            ch.interlis.generator.model.builder.ModelMetadataBuilder.model("TestModel");

        ch.interlis.generator.model.builder.EnumMetadataBuilder topicAStatus =
            builder.enumBuilder("TestModel.TopicA.Status");
        topicAStatus.value("ACTIVE", 0)
            .value("in.Betrieb", 1)
            .value("class", 2)
            .value("a.b", 3)
            .value("a_b", 4);

        ch.interlis.generator.model.builder.EnumMetadataBuilder topicBStatus =
            builder.enumBuilder("TestModel.TopicB.Status");
        topicBStatus.value("ACTIVE", 0);

        builder.classBuilder("TestModel.TopicA.Gebaeude")
            .tableName("gebaeude_a")
            .attribute(enumAttribute("status", topicAStatus.name()))
            .attribute(geometryAttribute("position"))
            .attribute(textAttribute("display-name", "name"))
            .attribute(textAttribute("primary_name", "name"));

        ch.interlis.generator.model.builder.ClassMetadataBuilder topicBGebaeude =
            builder.classBuilder("TestModel.TopicB.Gebaeude");
        topicBGebaeude.tableName("gebaeude_b");
        topicBGebaeude.attribute(enumAttribute("status", topicBStatus.name()));
        ch.interlis.generator.model.builder.AttributeMetadataBuilder owner =
            new ch.interlis.generator.model.builder.AttributeMetadataBuilder("owner");
        owner.foreignKey(true);
        owner.referencedClass("TestModel.TopicA.Gebaeude");
        owner.columnName("owner");
        owner.sqlName("owner");
        owner.javaType("Long");
        owner.mandatory(false);
        topicBGebaeude.attribute(owner);

        builder.relationshipBuilder("TopicB_Gebaeude_owner")
            .sourceClass(topicBGebaeude.name())
            .targetClass("TestModel.TopicA.Gebaeude")
            .sourceAttribute("owner")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);

        builder.classBuilder("TestModel.TopicA.Component")
            .kind(ClassMetadata.ClassKind.STRUCTURE)
            .attribute(textAttribute("label", "label"));

        builder.relationshipBuilder("TopicA_Gebaeude_components")
            .sourceClass("TestModel.TopicA.Gebaeude")
            .targetClass("TestModel.TopicA.Component")
            .sourceAttribute("Components")
            .type(RelationshipMetadata.RelationType.ONE_TO_MANY)
            .semanticKind(RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE)
            .composition(true)
            .cardinality(ch.interlis.generator.model.Cardinality.of(1, 1, 0, -1));

        builder.classBuilder("TestModel.TopicA.GebaeudeLink")
            .kind(ClassMetadata.ClassKind.ASSOCIATION)
            .tableName("gebaeude_link");

        builder.relationshipBuilder("GebaeudeLink_Source")
            .sourceClass("TestModel.TopicA.GebaeudeLink")
            .targetClass("TestModel.TopicA.Gebaeude")
            .targetRoleName("Source")
            .type(RelationshipMetadata.RelationType.ASSOCIATION)
            .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .mandatory(true);

        builder.relationshipBuilder("GebaeudeLink_Target")
            .sourceClass("TestModel.TopicA.GebaeudeLink")
            .targetClass("TestModel.TopicB.Gebaeude")
            .targetRoleName("Target")
            .type(RelationshipMetadata.RelationType.ASSOCIATION)
            .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .mandatory(true);

        builder.classBuilder("TestModel.TopicA.AbstractBase")
            .abstractClass(true);

        ch.interlis.generator.model.builder.ClassMetadataBuilder worker =
            builder.classBuilder("TestModel.TopicA.Worker");
        ch.interlis.generator.model.builder.AttributeMetadataBuilder baseRef =
            new ch.interlis.generator.model.builder.AttributeMetadataBuilder("baseRef");
        baseRef.foreignKey(true);
        baseRef.referencedClass("TestModel.TopicA.AbstractBase");
        baseRef.javaType("Long");
        baseRef.mandatory(false);
        worker.attribute(baseRef);

        builder.relationshipBuilder("Worker_AbstractBase")
            .sourceClass(worker.name())
            .targetClass("TestModel.TopicA.AbstractBase")
            .sourceAttribute("baseRef")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);

        return new ch.interlis.generator.model.ModelMetadataFactory()
            .buildValidated(builder);
    }

    private ch.interlis.generator.model.builder.AttributeMetadataBuilder enumAttribute(String name, String enumType) {
        return new ch.interlis.generator.model.builder.AttributeMetadataBuilder(name)
            .enumType(enumType)
            .mandatory(true);
    }

    private ch.interlis.generator.model.builder.AttributeMetadataBuilder geometryAttribute(String name) {
        return new ch.interlis.generator.model.builder.AttributeMetadataBuilder(name)
            .geometry(true)
            .geometryKind("POINT")
            .geometrySrid(2056)
            .javaType("org.locationtech.jts.geom.Geometry")
            .mandatory(false);
    }

    private ch.interlis.generator.model.builder.AttributeMetadataBuilder textAttribute(String name, String sqlName) {
        return new ch.interlis.generator.model.builder.AttributeMetadataBuilder(name)
            .sqlName(sqlName)
            .columnName(sqlName)
            .javaType("String")
            .mandatory(false);
    }
}

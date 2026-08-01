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
        ModelMetadata metadata = new ModelMetadata("TestModel");

        EnumMetadata topicAStatus = new EnumMetadata("TestModel.TopicA.Status");
        topicAStatus.setValues(List.of(
            new EnumMetadata.EnumValue("ACTIVE", 0),
            new EnumMetadata.EnumValue("in.Betrieb", 1),
            new EnumMetadata.EnumValue("class", 2),
            new EnumMetadata.EnumValue("a.b", 3),
            new EnumMetadata.EnumValue("a_b", 4)
        ));
        metadata.addEnum(topicAStatus);

        EnumMetadata topicBStatus = new EnumMetadata("TestModel.TopicB.Status");
        topicBStatus.setValues(List.of(new EnumMetadata.EnumValue("ACTIVE", 0)));
        metadata.addEnum(topicBStatus);

        ClassMetadata topicAGebaeude = new ClassMetadata("TestModel.TopicA.Gebaeude");
        topicAGebaeude.setTableName("gebaeude_a");
        topicAGebaeude.addAttribute(enumAttribute("status", topicAStatus.getName()));
        topicAGebaeude.addAttribute(geometryAttribute("position"));
        topicAGebaeude.addAttribute(textAttribute("display-name", "name"));
        topicAGebaeude.addAttribute(textAttribute("primary_name", "name"));
        metadata.addClass(topicAGebaeude);

        ClassMetadata topicBGebaeude = new ClassMetadata("TestModel.TopicB.Gebaeude");
        topicBGebaeude.setTableName("gebaeude_b");
        topicBGebaeude.addAttribute(enumAttribute("status", topicBStatus.getName()));
        AttributeMetadata owner = new AttributeMetadata("owner");
        owner.setForeignKey(true);
        owner.setReferencedClass(topicAGebaeude.getName());
        owner.setColumnName("owner");
        owner.setSqlName("owner");
        owner.setJavaType("Long");
        owner.setMandatory(false);
        topicBGebaeude.addAttribute(owner);
        metadata.addClass(topicBGebaeude);

        RelationshipMetadata generatedRelationship = new RelationshipMetadata("TopicB_Gebaeude_owner");
        generatedRelationship.setSourceClass(topicBGebaeude.getName());
        generatedRelationship.setTargetClass(topicAGebaeude.getName());
        generatedRelationship.setSourceAttribute("owner");
        generatedRelationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        generatedRelationship.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        metadata.addRelationship(generatedRelationship);

        ClassMetadata component = new ClassMetadata("TestModel.TopicA.Component");
        component.setKind(ClassMetadata.ClassKind.STRUCTURE);
        component.addAttribute(textAttribute("label", "label"));
        metadata.addClass(component);

        RelationshipMetadata composition = new RelationshipMetadata("TopicA_Gebaeude_components");
        composition.setSourceClass(topicAGebaeude.getName());
        composition.setTargetClass(component.getName());
        composition.setSourceAttribute("Components");
        composition.setType(RelationshipMetadata.RelationType.ONE_TO_MANY);
        composition.setSemanticKind(RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE);
        composition.setComposition(true);
        composition.setCardinality(new RelationshipMetadata.Cardinality(1, 1, 0, -1));
        metadata.addRelationship(composition);

        ClassMetadata gebaeudeLink = new ClassMetadata("TestModel.TopicA.GebaeudeLink");
        gebaeudeLink.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        gebaeudeLink.setTableName("gebaeude_link");
        metadata.addClass(gebaeudeLink);

        RelationshipMetadata sourceRole = new RelationshipMetadata("GebaeudeLink_Source");
        sourceRole.setSourceClass(gebaeudeLink.getName());
        sourceRole.setTargetClass(topicAGebaeude.getName());
        sourceRole.setTargetRoleName("Source");
        sourceRole.setType(RelationshipMetadata.RelationType.ASSOCIATION);
        sourceRole.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
        sourceRole.setMandatory(true);
        metadata.addRelationship(sourceRole);

        RelationshipMetadata targetRole = new RelationshipMetadata("GebaeudeLink_Target");
        targetRole.setSourceClass(gebaeudeLink.getName());
        targetRole.setTargetClass(topicBGebaeude.getName());
        targetRole.setTargetRoleName("Target");
        targetRole.setType(RelationshipMetadata.RelationType.ASSOCIATION);
        targetRole.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
        targetRole.setMandatory(true);
        metadata.addRelationship(targetRole);

        ClassMetadata abstractBase = new ClassMetadata("TestModel.TopicA.AbstractBase");
        abstractBase.setAbstract(true);
        metadata.addClass(abstractBase);

        ClassMetadata worker = new ClassMetadata("TestModel.TopicA.Worker");
        AttributeMetadata baseRef = new AttributeMetadata("baseRef");
        baseRef.setForeignKey(true);
        baseRef.setReferencedClass(abstractBase.getName());
        baseRef.setJavaType("Long");
        baseRef.setMandatory(false);
        worker.addAttribute(baseRef);
        metadata.addClass(worker);

        RelationshipMetadata abstractRelationship = new RelationshipMetadata("Worker_AbstractBase");
        abstractRelationship.setSourceClass(worker.getName());
        abstractRelationship.setTargetClass(abstractBase.getName());
        abstractRelationship.setSourceAttribute("baseRef");
        abstractRelationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        abstractRelationship.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        metadata.addRelationship(abstractRelationship);

        return metadata;
    }

    private AttributeMetadata enumAttribute(String name, String enumType) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setEnumType(enumType);
        attribute.setMandatory(true);
        return attribute;
    }

    private AttributeMetadata geometryAttribute(String name) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setGeometry(true);
        attribute.setGeometryKind("POINT");
        attribute.setGeometrySrid(2056);
        attribute.setJavaType("org.locationtech.jts.geom.Geometry");
        attribute.setMandatory(false);
        return attribute;
    }

    private AttributeMetadata textAttribute(String name, String sqlName) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setSqlName(sqlName);
        attribute.setColumnName(sqlName);
        attribute.setJavaType("String");
        attribute.setMandatory(false);
        return attribute;
    }
}

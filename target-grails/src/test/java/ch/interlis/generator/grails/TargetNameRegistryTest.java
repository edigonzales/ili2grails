package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.EnumValueBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TargetNameRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void keepsUniqueClassNamesButPrefixesCollisionsWithTopic() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadata topicAGebaeude = modelBuilder.classBuilder("TestModel.TopicA.Gebaeude").buildUnchecked();
        ClassMetadata topicBGebaeude = modelBuilder.classBuilder("TestModel.TopicB.Gebaeude").buildUnchecked();
        ClassMetadata person = modelBuilder.classBuilder("TestModel.TopicA.Person").buildUnchecked();
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        TargetNameRegistry registry = registry(metadata);

        assertThat(registry.className(topicAGebaeude)).isEqualTo("TopicAGebaeude");
        assertThat(registry.className(topicBGebaeude)).isEqualTo("TopicBGebaeude");
        assertThat(registry.className(person)).isEqualTo("Person");
    }

    @Test
    void prefixesEnumNameCollisionsWithTopic() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        EnumMetadata topicAStatus = modelBuilder.enumBuilder("TestModel.TopicA.Status")
            .value("ACTIVE", 0)
            .buildUnchecked();
        EnumMetadata topicBStatus = modelBuilder.enumBuilder("TestModel.TopicB.Status")
            .value("ACTIVE", 0)
            .buildUnchecked();
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        TargetNameRegistry registry = registry(metadata);

        assertThat(registry.enumName(topicAStatus)).isEqualTo("TopicAStatus");
        assertThat(registry.enumName(topicBStatus)).isEqualTo("TopicBStatus");
    }

    @Test
    void normalizesReservedWordsAndInvalidIdentifiers() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadata keywordClass = modelBuilder.classBuilder("TestModel.Topic.class").buildUnchecked();
        ClassMetadata invalidClass = modelBuilder.classBuilder("TestModel.Topic.123-name").buildUnchecked();
        ClassMetadataBuilder ownerBuilder = modelBuilder.classBuilder("TestModel.Topic.Owner");
        AttributeMetadata keywordAttribute = AttributeMetadata.builder("class").buildUnchecked();
        AttributeMetadata invalidAttribute = AttributeMetadata.builder("foo-bar").buildUnchecked();
        ownerBuilder.attribute(keywordAttribute);
        ownerBuilder.attribute(invalidAttribute);
        ClassMetadata owner = ownerBuilder.buildUnchecked();
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        TargetNameRegistry registry = registry(metadata);

        assertThat(registry.className(keywordClass)).isEqualTo("ClassType");
        assertThat(registry.className(invalidClass)).isEqualTo("Type123Name");
        assertThat(registry.propertyName(owner, keywordAttribute)).isEqualTo("classType");
        assertThat(registry.propertyName(owner, invalidAttribute)).isEqualTo("fooBar");
    }

    @Test
    void resolvesPropertyCollisionsDeterministicallyWithinClass() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder ownerBuilder = modelBuilder.classBuilder("TestModel.Topic.Owner");
        AttributeMetadata displayName = AttributeMetadata.builder("display-name")
            .qualifiedName("TestModel.Topic.Owner.display-name")
            .sqlName("name")
            .buildUnchecked();
        AttributeMetadata primaryName = AttributeMetadata.builder("primary_name")
            .qualifiedName("TestModel.Topic.Owner.primary_name")
            .sqlName("name")
            .buildUnchecked();
        ownerBuilder.attribute(displayName);
        ownerBuilder.attribute(primaryName);
        ClassMetadata owner = ownerBuilder.buildUnchecked();
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        TargetNameRegistry registry = registry(metadata);

        List<String> names = owner.getAllAttributes().stream()
            .map(attribute -> registry.propertyName(owner, attribute))
            .toList();
        assertThat(names).containsExactlyInAnyOrder("displayNameName", "primaryNameName");
        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    void resolvesEnumConstantsToStableGroovyIdentifiers() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        EnumValueBuilder activeBuilder = EnumMetadata.EnumValue.builder("ACTIVE", 0);
        EnumValueBuilder hierarchyBuilder = EnumMetadata.EnumValue.builder("in.Betrieb", 1);
        EnumValueBuilder keywordBuilder = EnumMetadata.EnumValue.builder("class", 2);
        EnumValueBuilder invalidStartBuilder = EnumMetadata.EnumValue.builder("1-start", 3);
        EnumValueBuilder dottedBuilder = EnumMetadata.EnumValue.builder("a.b", 4);
        EnumValueBuilder underscoredBuilder = EnumMetadata.EnumValue.builder("a_b", 5);
        EnumMetadata enumMetadata = modelBuilder.enumBuilder("TestModel.Topic.Status")
            .value(activeBuilder)
            .value(hierarchyBuilder)
            .value(keywordBuilder)
            .value(invalidStartBuilder)
            .value(dottedBuilder)
            .value(underscoredBuilder)
            .buildUnchecked();
        EnumMetadata.EnumValue active = activeBuilder.buildUnchecked();
        EnumMetadata.EnumValue hierarchy = hierarchyBuilder.buildUnchecked();
        EnumMetadata.EnumValue keyword = keywordBuilder.buildUnchecked();
        EnumMetadata.EnumValue invalidStart = invalidStartBuilder.buildUnchecked();
        EnumMetadata.EnumValue dotted = dottedBuilder.buildUnchecked();
        EnumMetadata.EnumValue underscored = underscoredBuilder.buildUnchecked();
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        TargetNameRegistry registry = registry(metadata);

        assertThat(registry.enumConstantName(enumMetadata, active)).isEqualTo("ACTIVE");
        assertThat(registry.enumConstantName(enumMetadata, hierarchy)).isEqualTo("in_Betrieb");
        assertThat(registry.enumConstantName(enumMetadata, keyword)).isEqualTo("classType");
        assertThat(registry.enumConstantName(enumMetadata, invalidStart)).isEqualTo("VALUE_1_start");
        assertThat(registry.enumConstantName(enumMetadata, dotted)).isEqualTo("a_b");
        assertThat(registry.enumConstantName(enumMetadata, underscored)).isEqualTo("VALUE_5");
    }

    private TargetNameRegistry registry(ModelMetadata metadata) {
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example").build();
        return TargetNameRegistry.forMetadata(metadata, config);
    }
}

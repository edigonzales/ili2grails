package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
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
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata topicAGebaeude = new ClassMetadata("TestModel.TopicA.Gebaeude");
        ClassMetadata topicBGebaeude = new ClassMetadata("TestModel.TopicB.Gebaeude");
        ClassMetadata person = new ClassMetadata("TestModel.TopicA.Person");
        metadata.addClass(topicAGebaeude);
        metadata.addClass(topicBGebaeude);
        metadata.addClass(person);

        TargetNameRegistry registry = registry(metadata);

        assertThat(registry.className(topicAGebaeude)).isEqualTo("TopicAGebaeude");
        assertThat(registry.className(topicBGebaeude)).isEqualTo("TopicBGebaeude");
        assertThat(registry.className(person)).isEqualTo("Person");
    }

    @Test
    void prefixesEnumNameCollisionsWithTopic() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        EnumMetadata topicAStatus = enumMetadata("TestModel.TopicA.Status");
        EnumMetadata topicBStatus = enumMetadata("TestModel.TopicB.Status");
        metadata.addEnum(topicAStatus);
        metadata.addEnum(topicBStatus);

        TargetNameRegistry registry = registry(metadata);

        assertThat(registry.enumName(topicAStatus)).isEqualTo("TopicAStatus");
        assertThat(registry.enumName(topicBStatus)).isEqualTo("TopicBStatus");
    }

    @Test
    void normalizesReservedWordsAndInvalidIdentifiers() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata keywordClass = new ClassMetadata("TestModel.Topic.class");
        ClassMetadata invalidClass = new ClassMetadata("TestModel.Topic.123-name");
        ClassMetadata owner = new ClassMetadata("TestModel.Topic.Owner");
        AttributeMetadata keywordAttribute = new AttributeMetadata("class");
        AttributeMetadata invalidAttribute = new AttributeMetadata("foo-bar");
        owner.addAttribute(keywordAttribute);
        owner.addAttribute(invalidAttribute);
        metadata.addClass(keywordClass);
        metadata.addClass(invalidClass);
        metadata.addClass(owner);

        TargetNameRegistry registry = registry(metadata);

        assertThat(registry.className(keywordClass)).isEqualTo("ClassType");
        assertThat(registry.className(invalidClass)).isEqualTo("Type123Name");
        assertThat(registry.propertyName(owner, keywordAttribute)).isEqualTo("classType");
        assertThat(registry.propertyName(owner, invalidAttribute)).isEqualTo("fooBar");
    }

    @Test
    void resolvesPropertyCollisionsDeterministicallyWithinClass() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata owner = new ClassMetadata("TestModel.Topic.Owner");
        AttributeMetadata displayName = new AttributeMetadata("display-name");
        displayName.setQualifiedName("TestModel.Topic.Owner.display-name");
        displayName.setSqlName("name");
        AttributeMetadata primaryName = new AttributeMetadata("primary_name");
        primaryName.setQualifiedName("TestModel.Topic.Owner.primary_name");
        primaryName.setSqlName("name");
        owner.addAttribute(displayName);
        owner.addAttribute(primaryName);
        metadata.addClass(owner);

        TargetNameRegistry registry = registry(metadata);

        List<String> names = owner.getAllAttributes().stream()
            .map(attribute -> registry.propertyName(owner, attribute))
            .toList();
        assertThat(names).containsExactlyInAnyOrder("displayNameName", "primaryNameName");
        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    void resolvesEnumConstantsToStableGroovyIdentifiers() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        EnumMetadata enumMetadata = new EnumMetadata("TestModel.Topic.Status");
        EnumMetadata.EnumValue active = new EnumMetadata.EnumValue("ACTIVE", 0);
        EnumMetadata.EnumValue hierarchy = new EnumMetadata.EnumValue("in.Betrieb", 1);
        EnumMetadata.EnumValue keyword = new EnumMetadata.EnumValue("class", 2);
        EnumMetadata.EnumValue invalidStart = new EnumMetadata.EnumValue("1-start", 3);
        EnumMetadata.EnumValue dotted = new EnumMetadata.EnumValue("a.b", 4);
        EnumMetadata.EnumValue underscored = new EnumMetadata.EnumValue("a_b", 5);
        enumMetadata.setValues(List.of(active, hierarchy, keyword, invalidStart, dotted, underscored));
        metadata.addEnum(enumMetadata);

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

    private EnumMetadata enumMetadata(String name) {
        EnumMetadata enumMetadata = new EnumMetadata(name);
        enumMetadata.addValue(new EnumMetadata.EnumValue("ACTIVE", 0));
        return enumMetadata;
    }
}

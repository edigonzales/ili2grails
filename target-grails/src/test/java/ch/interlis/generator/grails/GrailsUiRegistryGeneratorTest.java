package ch.interlis.generator.grails;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsUiRegistryGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void rendersDeterministicModelTopicAndDomainEntries() throws Exception {
        ModelMetadata metadata = new ModelMetadata("UiModel");
        ClassMetadata second = new ClassMetadata("UiModel.Second.Topic");
        second.addLabel("en", "Topic");
        metadata.addClass(second);

        ClassMetadata first = new ClassMetadata("UiModel.First.Topic");
        first.addLabel("de", "Thema");
        first.addLabel("de-CH", "Thema CH");
        metadata.addClass(first);

        ClassMetadata fallback = new ClassMetadata("UiModel.Fallback.Topic");
        fallback.addLabel("fr", "Sujet");
        metadata.addClass(fallback);

        GenerationConfig config = config();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);
        GrailsUiRegistryGenerator generator = new GrailsUiRegistryGenerator();

        String firstRender = generator.renderRegistry(metadata, config, registry, mapper, planner);
        String secondRender = generator.renderRegistry(metadata, config, registry, mapper, planner);

        assertThat(firstRender).isEqualTo(secondRender);
        assertThat(firstRender).contains("final class InterlisUiRegistry {");
        assertThat(firstRender).contains("domainClassName: 'com.example.ui.FirstTopic'");
        assertThat(firstRender).contains("domainClassName: 'com.example.ui.SecondTopic'");
        assertThat(firstRender).contains("controller: 'firstTopic'");
        assertThat(firstRender).contains("controller: 'secondTopic'");
        assertThat(firstRender).contains("label: 'Topic'").doesNotContain("label: 'Sujet'");
        assertThat(firstRender).contains("modelName: 'UiModel'");
        assertThat(firstRender).contains("topicName: 'First'");
        assertThat(firstRender).contains("label: 'Thema CH'");
        assertThat(firstRender.indexOf("UiModel.First.Topic"))
            .isLessThan(firstRender.indexOf("UiModel.Second.Topic"));
        assertThat(firstRender).contains("static List<Map<String, Object>> domains()");
        assertThat(firstRender).contains("static List<Map<String, Object>> domainsForModel");
        assertGroovyCompiles(firstRender);
    }

    @Test
    void reusesAssociationPlannerNavigationSemantics() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        GenerationConfig config = GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage("ch.example.association.domain")
            .enumPackage("ch.example.association.enums")
            .build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);

        String rendered = new GrailsUiRegistryGenerator()
            .renderRegistry(metadata, config, registry, mapper, planner);

        assertThat(rendered).contains(
            "iliName: 'AssociationCases.Base.AssociationWithAttribute',");
        assertThat(rendered).contains("associationDomain: true");
        assertThat(rendered).contains("navigationVisible: false");
        assertThat(rendered).contains(
            "iliName: 'AssociationCases.Base.Person',");
        assertThat(rendered).contains("associationDomain: false");
    }

    @Test
    void generatesAndCompilesRegistryForEmptyModel() throws Exception {
        ModelMetadata metadata = new ModelMetadata("Empty");
        GenerationConfig config = config();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);

        new GrailsUiRegistryGenerator().generate(metadata, config, registry, mapper, planner);

        Path generated = tempDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisUiRegistry.groovy");
        assertThat(generated).exists();
        assertThat(Files.readString(generated)).contains("DOMAINS = []");
        assertGroovyCompiles(Files.readString(generated));
    }

    private GenerationConfig config() {
        return GenerationConfig.builder(tempDir, "com.example")
            .domainPackage("com.example.ui")
            .enumPackage("com.example.enums")
            .build();
    }

    private void assertGroovyCompiles(String source) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setClasspath(System.getProperty("java.class.path"));
        CompilationUnit compilationUnit = new CompilationUnit(configuration);
        compilationUnit.addSource("InterlisUiRegistry.groovy", source);
        compilationUnit.compile();
    }
}

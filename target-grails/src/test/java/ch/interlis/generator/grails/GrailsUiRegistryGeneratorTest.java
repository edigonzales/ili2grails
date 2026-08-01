package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind;
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
    void rendersDeterministicTypedDomainEntries() throws Exception {
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
        RuntimeDescriptorPlan plan = plan(metadata, config);
        GrailsUiRegistryGenerator generator = new GrailsUiRegistryGenerator();

        String firstRender = generator.renderRegistry(plan);
        String secondRender = generator.renderRegistry(plan);

        assertThat(firstRender).isEqualTo(secondRender);
        assertThat(firstRender).contains("final class InterlisUiRegistry implements DomainRegistry {");
        assertThat(firstRender).contains("'com.example.ui.FirstTopic'");
        assertThat(firstRender).contains("'com.example.ui.SecondTopic'");
        assertThat(firstRender).contains("'firstTopic'");
        assertThat(firstRender).contains("'secondTopic'");
        assertThat(firstRender).contains("'Thema CH'");
        assertThat(firstRender).contains("DomainKind.CLASS");
        assertThat(firstRender).contains("implements DomainRegistry");
        assertThat(firstRender).doesNotContain("label: 'Sujet'");
        assertThat(firstRender.indexOf("'UiModel.First.Topic'"))
            .isLessThan(firstRender.indexOf("'UiModel.Second.Topic'"));
        assertThat(firstRender).contains("Collection<DomainDescriptor> domains() { DOMAINS }");
        assertThat(firstRender).contains("List<DomainDescriptor> byModel(String modelName)");
        assertGroovyCompiles(firstRender);
    }

    @Test
    void plansCarryDeterministicMetadata() throws Exception {
        ModelMetadata metadata = new ModelMetadata("UiModel");
        ClassMetadata topic = new ClassMetadata("UiModel.Topic.Address");
        topic.addLabel("de-CH", "Adresse");
        metadata.addClass(topic);

        RuntimeDescriptorPlan plan = plan(metadata, config());
        assertThat(plan.domains()).hasSize(1);
        DomainDescriptor descriptor = plan.domains().get(0);
        assertThat(descriptor.iliName()).isEqualTo("UiModel.Topic.Address");
        assertThat(descriptor.modelName()).isEqualTo("UiModel");
        assertThat(descriptor.topicName()).isEqualTo("Topic");
        assertThat(descriptor.domainClassName()).isEqualTo("com.example.ui.Address");
        assertThat(descriptor.controllerName()).isEqualTo("address");
        assertThat(descriptor.label()).isEqualTo("Adresse");
        assertThat(descriptor.kind()).isEqualTo(DomainKind.CLASS);
    }

    @Test
    void reusesAssociationPlannerNavigationSemantics() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        GenerationConfig config = GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage("ch.example.association.domain")
            .enumPackage("ch.example.association.enums")
            .build();
        RuntimeDescriptorPlan plan = plan(metadata, config);

        DomainDescriptor association = plan.domains().stream()
            .filter(domain -> domain.iliName().equals("AssociationCases.Base.AssociationWithAttribute"))
            .findFirst()
            .orElseThrow();
        assertThat(association.kind()).isEqualTo(DomainKind.ASSOCIATION);
        assertThat(association.navigationVisible()).isFalse();

        DomainDescriptor person = plan.domains().stream()
            .filter(domain -> domain.iliName().equals("AssociationCases.Base.Person"))
            .findFirst()
            .orElseThrow();
        assertThat(person.kind()).isEqualTo(DomainKind.CLASS);
    }

    @Test
    void generatesAndCompilesRegistryForEmptyModel() throws Exception {
        ModelMetadata metadata = new ModelMetadata("Empty");
        GenerationConfig config = config();
        RuntimeDescriptorPlan plan = plan(metadata, config);

        new GrailsUiRegistryGenerator().generate(plan, config, TargetNameRegistry.forMetadata(metadata, config));

        Path generated = tempDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisUiRegistry.groovy");
        assertThat(generated).exists();
        assertThat(Files.readString(generated)).contains("DOMAINS = [].asImmutable()");
        assertGroovyCompiles(Files.readString(generated));
    }

    static RuntimeDescriptorPlan plan(ModelMetadata metadata, GenerationConfig config) {
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);
        GrailsInverseRelationshipPlanner inverses =
            GrailsInverseRelationshipPlanner.forMetadata(metadata, config, registry, mapper);
        return new RuntimeDescriptorPlanner(registry, mapper, planner, inverses).plan(metadata, config);
    }

    private GenerationConfig config() {
        return GenerationConfig.builder(tempDir, "com.example")
            .domainPackage("com.example.ui")
            .enumPackage("com.example.enums")
            .build();
    }

    static void assertGroovyCompiles(String source) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setClasspath(System.getProperty("java.class.path"));
        CompilationUnit compilationUnit = new CompilationUnit(configuration);
        compilationUnit.addSource("InterlisUiRegistry.groovy", source);
        compilationUnit.compile();
    }
}

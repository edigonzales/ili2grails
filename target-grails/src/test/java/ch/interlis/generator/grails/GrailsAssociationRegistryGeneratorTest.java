package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationCreateMode;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind;
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

class GrailsAssociationRegistryGeneratorTest {

    private static final String DOMAIN_PACKAGE = "ch.example.association.domain";

    @TempDir
    Path tempDir;

    @Test
    void rendersDeterministicTypedRegistry() throws Exception {
        RuntimeDescriptorPlan plan = mergedPlan(config());
        GrailsAssociationRegistryGenerator generator = new GrailsAssociationRegistryGenerator();

        String first = generator.renderRegistry(plan);
        String second = generator.renderRegistry(plan);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("package ch.interlis.generator.grails.generated\n");
        assertThat(first).contains("final class InterlisAssociationRegistry implements AssociationRegistry {");
        assertThat(first).contains("static final Map<String, AssociationDescriptor> ASSOCIATIONS = [");
        assertThat(first).contains("static final Map<String, AssociationContextDescriptor> CONTEXTS = [");
        assertThat(first).contains("static final Map<String, List<String>> CONTEXT_IDS_BY_PARTICIPANT = [");
        assertThat(first)
            .doesNotContain("LegacyDescriptorMapAdapter", "EntityDescriptor", "legacyAssociation");
        // Associations are emitted in stable, sorted order.
        int withAttribute = first.indexOf("AssociationCases.Base.AssociationWithAttribute");
        int emptyAssociation = first.indexOf("AssociationCases.Base.EmptyAssociation");
        assertThat(withAttribute).isGreaterThanOrEqualTo(0);
        assertThat(emptyAssociation).isGreaterThan(withAttribute);
    }

    @Test
    void escapesQuotesBackslashesAndNewlines() throws Exception {
        AssociationDescriptor original = mergedPlan(config()).associations().stream()
            .filter(association -> association.associationName()
                .equals("AssociationCases.Base.AssociationWithAttribute"))
            .findFirst()
            .orElseThrow();
        AssociationDescriptor tricky = new AssociationDescriptor(
            original.associationName(),
            original.iliClassName(),
            original.domainClassName(),
            original.controllerName(),
            original.viewPath(),
            original.physicalTable(),
            original.physicalSqlName(),
            original.storageKind(),
            original.writable(),
            original.showInNavigation(),
            original.roles(),
            List.of(new ch.interlis.generator.grails.runtime.api.descriptor.AssociationAttributeDescriptor(
                "attr", "attr", "String",
                ch.interlis.generator.grails.runtime.api.descriptor.RuntimeCoreType.TEXT,
                "Line1\nLine2 with 'quote' and \\ backslash and $var",
                false, null, null, null, false
            )),
            original.diagnostics()
        );
        RuntimeDescriptorPlan plan = new RuntimeDescriptorPlan(
            List.of(), List.of(tricky), List.of(), List.of());

        String rendered = new GrailsAssociationRegistryGenerator().renderRegistry(plan);

        assertThat(rendered).contains("\\n");
        assertThat(rendered).contains("\\'quote\\'");
        assertThat(rendered).contains("\\\\ backslash");
        assertThat(rendered).contains("\\$var");
        assertThat(rendered).doesNotContain("Line1\nLine2");
        assertGroovyCompiles(rendered);
    }

    @Test
    void emitsContextsByParticipant() throws Exception {
        String rendered = new GrailsAssociationRegistryGenerator()
            .renderRegistry(mergedPlan(config()));

        // The self-association exposes two participant contexts for the same domain class.
        assertThat(rendered).contains(DOMAIN_PACKAGE + ".Person");
        assertThat(rendered).contains("AssociationCases.Base.SameTargetAssociation::PrimaryPerson");
        assertThat(rendered).contains("AssociationCases.Base.SameTargetAssociation::SecondaryPerson");
        assertThat(rendered).contains("'PrimaryPerson'");
        assertThat(rendered).contains("'primaryPersonId'");
    }

    @Test
    void emitsEntityNavigationMetadata() throws Exception {
        RuntimeDescriptorPlan plan = mergedPlan(config());
        AssociationDescriptor association = plan.associations().stream()
            .filter(candidate -> candidate.associationName()
                .equals("AssociationCases.Base.AssociationWithAttribute"))
            .findFirst()
            .orElseThrow();
        assertThat(association.showInNavigation()).isFalse();
    }

    @Test
    void navigationShowModeForcesVisibleAssociationEntities() throws Exception {
        GenerationConfig config = GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage(DOMAIN_PACKAGE)
            .enumPackage("ch.example.association.enums")
            .associationNavigation(GenerationConfig.ASSOCIATION_NAVIGATION_SHOW)
            .build();
        RuntimeDescriptorPlan plan = mergedPlan(config);
        assertThat(plan.associations().stream().anyMatch(AssociationDescriptor::showInNavigation))
            .isTrue();
        assertThat(plan.associations().stream().allMatch(AssociationDescriptor::showInNavigation))
            .isTrue();
    }

    @Test
    void navigationHideModeMarksAssociationEntitiesHidden() throws Exception {
        GenerationConfig config = GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage(DOMAIN_PACKAGE)
            .enumPackage("ch.example.association.enums")
            .associationNavigation(GenerationConfig.ASSOCIATION_NAVIGATION_HIDE)
            .build();
        RuntimeDescriptorPlan plan = mergedPlan(config);
        assertThat(plan.associations().stream().anyMatch(AssociationDescriptor::showInNavigation))
            .isFalse();
    }

    @Test
    void generatedRegistryCompilesWithGroovyCompiler() throws Exception {
        GrailsAssociationRegistryGenerator generator = new GrailsAssociationRegistryGenerator();
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        GenerationConfig config = config();
        RuntimeDescriptorPlan plan = mergedPlan(config);

        String generated = new String(generator.plan(plan, config).content(),
            java.nio.charset.StandardCharsets.UTF_8);

        assertGroovyCompiles(generated);
    }

    @Test
    void emptyAssociationSetProducesValidRegistry() throws Exception {
        ModelMetadata metadata = new ch.interlis.generator.model.ModelMetadataFactory()
            .buildValidated(ch.interlis.generator.model.builder.ModelMetadataBuilder.model("Empty"));
        GenerationConfig config = config();
        RuntimeDescriptorPlan plan = GrailsUiRegistryGeneratorTest.plan(metadata, config);

        String rendered = new GrailsAssociationRegistryGenerator().renderRegistry(plan);

        assertThat(rendered).contains("ASSOCIATIONS = [:]");
        assertThat(rendered).contains("CONTEXTS = [:]");
        assertThat(rendered).contains("CONTEXT_IDS_BY_PARTICIPANT = [:]");
        assertThat(rendered).doesNotContain("ENTITIES", "legacy");
        assertGroovyCompiles(rendered);
    }

    @Test
    void readOnlyModeDisablesWritesInRegistry() throws Exception {
        GenerationConfig config = GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage(DOMAIN_PACKAGE)
            .enumPackage("ch.example.association.enums")
            .associationUiMode(GenerationConfig.ASSOCIATION_UI_READ_ONLY)
            .build();
        RuntimeDescriptorPlan plan = mergedPlan(config);

        assertThat(plan.contexts()).isNotEmpty();
        assertThat(plan.contexts().stream()
            .allMatch(context -> context.createMode() == AssociationCreateMode.NONE)).isTrue();
        assertThat(plan.contexts().stream().noneMatch(AssociationContextDescriptor::writable)).isTrue();
        assertThat(plan.contexts().stream().noneMatch(AssociationContextDescriptor::removable)).isTrue();
    }

    @Test
    void offModeDisablesWritesInRegistry() throws Exception {
        GenerationConfig config = GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage(DOMAIN_PACKAGE)
            .enumPackage("ch.example.association.enums")
            .associationUiMode(GenerationConfig.ASSOCIATION_UI_OFF)
            .build();
        RuntimeDescriptorPlan plan = mergedPlan(config);

        assertThat(plan.associations().stream().noneMatch(AssociationDescriptor::writable)).isTrue();
        assertThat(plan.contexts().stream()
            .allMatch(context -> context.createMode() == AssociationCreateMode.NONE)).isTrue();
    }

    @Test
    void autoModeKeepsQuickCreateMode() throws Exception {
        RuntimeDescriptorPlan plan = mergedPlan(config());

        assertThat(plan.contexts().stream()
            .anyMatch(context -> context.createMode() == AssociationCreateMode.QUICK)).isTrue();
        assertThat(plan.contexts().stream().anyMatch(AssociationContextDescriptor::writable)).isTrue();
    }

    // ------------------------------------------------------------------

    private RuntimeDescriptorPlan mergedPlan(GenerationConfig config) throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        return GrailsUiRegistryGeneratorTest.plan(metadata, config);
    }

    private GenerationConfig config() {
        return GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage(DOMAIN_PACKAGE)
            .enumPackage("ch.example.association.enums")
            .build();
    }

    private void assertGroovyCompiles(String source) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setClasspath(System.getProperty("java.class.path"));
        CompilationUnit compilationUnit = new CompilationUnit(configuration);
        compilationUnit.addSource("InterlisAssociationRegistry.groovy", source);
        compilationUnit.compile();
    }
}

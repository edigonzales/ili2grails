package ch.interlis.generator.grails;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsAssociationRegistryGeneratorTest {

    private static final String DOMAIN_PACKAGE = "ch.example.association.domain";

    @TempDir
    Path tempDir;

    @Test
    void rendersDeterministicGroovyRegistry() throws Exception {
        GrailsAssociationPlanner planner = mergedPlanner();
        GenerationConfig config = config();
        GrailsAssociationRegistryGenerator generator = new GrailsAssociationRegistryGenerator();

        String first = generator.renderRegistry(planner.plans(), config);
        String second = generator.renderRegistry(planner.plans(), config);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("package ch.interlis.generator.grails.generated\n");
        assertThat(first).contains("final class InterlisAssociationRegistry {");
        assertThat(first).contains("static final Map<String, Map<String, Object>> ASSOCIATIONS = [");
        assertThat(first).contains("static final Map<String, Map<String, Object>> CONTEXTS = [");
        assertThat(first).contains("static final Map<String, List<String>> CONTEXT_IDS_BY_PARTICIPANT = [");
        assertThat(first).contains("static final Map<String, Map<String, Object>> ENTITIES = [");
        // Associations are emitted in stable, sorted order.
        int withAttribute = first.indexOf("AssociationCases.Base.AssociationWithAttribute");
        int emptyAssociation = first.indexOf("AssociationCases.Base.EmptyAssociation");
        assertThat(withAttribute).isGreaterThanOrEqualTo(0);
        assertThat(emptyAssociation).isGreaterThan(withAttribute);
    }

    @Test
    void escapesQuotesBackslashesAndNewlines() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        GenerationConfig config = config();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);

        GrailsAssociationPlan original = planner.findPlan("AssociationCases.Base.AssociationWithAttribute")
            .orElseThrow();
        GrailsAssociationAttributePlan attribute = original.attributes().get(0);
        GrailsAssociationAttributePlan tricky = new GrailsAssociationAttributePlan(
            attribute.iliName(),
            attribute.domainPropertyName(),
            attribute.javaType(),
            attribute.coreType(),
            "Line1\nLine2 with 'quote' and \\ backslash and $var",
            attribute.documentation(),
            attribute.unit(),
            attribute.mandatory(),
            attribute.maxLength(),
            attribute.minInclusive(),
            attribute.maxInclusive(),
            attribute.precision(),
            attribute.scale(),
            attribute.geometry(),
            attribute.geometryKind(),
            attribute.geometrySrid(),
            attribute.enumType()
        );
        GrailsAssociationPlan tweaked = new GrailsAssociationPlan(
            original.associationName(),
            original.associationIliClassName(),
            original.associationDomainClassName(),
            original.associationDomainQualifiedName(),
            original.associationControllerName(),
            original.associationViewPath(),
            original.physicalTable(),
            original.physicalSqlName(),
            original.storageKind(),
            original.physicalMappingPresent(),
            original.writable(),
            original.showInNavigation(),
            original.roles(),
            java.util.List.of(tricky),
            original.contexts(),
            original.diagnostics()
        );

        String rendered = new GrailsAssociationRegistryGenerator()
            .renderRegistry(java.util.List.of(tweaked), config);

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
            .renderRegistry(mergedPlanner().plans(), config());

        // The self-association exposes two participant contexts for the same domain class.
        assertThat(rendered).contains(DOMAIN_PACKAGE + ".Person");
        assertThat(rendered).contains("AssociationCases.Base.SameTargetAssociation::PrimaryPerson");
        assertThat(rendered).contains("AssociationCases.Base.SameTargetAssociation::SecondaryPerson");
        assertThat(rendered).contains("fixedRole: 'PrimaryPerson'");
        assertThat(rendered).contains("fixedProperty: 'primaryPersonId'");
    }

    @Test
    void emitsEntityNavigationMetadata() throws Exception {
        String rendered = new GrailsAssociationRegistryGenerator()
            .renderRegistry(mergedPlanner().plans(), config());

        assertThat(rendered).contains("kind: 'ASSOCIATION'");
        assertThat(entitiesBlock(rendered)).contains("showInNavigation: false");
    }

    @Test
    void navigationShowModeForcesVisibleAssociationEntities() throws Exception {
        GenerationConfig config = GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage(DOMAIN_PACKAGE)
            .enumPackage("ch.example.association.enums")
            .associationNavigation(GenerationConfig.ASSOCIATION_NAVIGATION_SHOW)
            .build();
        String rendered = new GrailsAssociationRegistryGenerator()
            .renderRegistry(mergedPlanner().plans(), config);

        assertThat(entitiesBlock(rendered)).contains("kind: 'ASSOCIATION'");
        assertThat(entitiesBlock(rendered)).doesNotContain("showInNavigation: false");
    }

    @Test
    void navigationHideModeMarksAssociationEntitiesHidden() throws Exception {
        GenerationConfig config = GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage(DOMAIN_PACKAGE)
            .enumPackage("ch.example.association.enums")
            .associationNavigation(GenerationConfig.ASSOCIATION_NAVIGATION_HIDE)
            .build();
        String rendered = new GrailsAssociationRegistryGenerator()
            .renderRegistry(mergedPlanner().plans(), config);

        assertThat(entitiesBlock(rendered)).contains("showInNavigation: false");
        assertThat(entitiesBlock(rendered)).doesNotContain("showInNavigation: true");
    }

    @Test
    void generatedRegistryCompilesWithGroovyCompiler() throws Exception {
        GrailsAssociationRegistryGenerator generator = new GrailsAssociationRegistryGenerator();
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        GenerationConfig config = config();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);

        generator.generate(metadata, config, registry, planner);

        Path expected = tempDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisAssociationRegistry.groovy");
        assertThat(expected).exists();
        assertGroovyCompiles(Files.readString(expected));
    }

    @Test
    void emptyAssociationSetProducesValidRegistry() {
        ModelMetadata metadata = new ModelMetadata("Empty");
        GenerationConfig config = config();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);

        String rendered = new GrailsAssociationRegistryGenerator().renderRegistry(planner.plans(), config);

        assertThat(rendered).contains("ASSOCIATIONS = [:]");
        assertThat(rendered).contains("CONTEXTS = [:]");
        assertThat(rendered).contains("CONTEXT_IDS_BY_PARTICIPANT = [:]");
        assertThat(rendered).contains("ENTITIES = [:]");
        assertGroovyCompiles(rendered);
    }

    @Test
    void readOnlyModeDisablesWritesInRegistry() throws Exception {
        GenerationConfig config = GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage(DOMAIN_PACKAGE)
            .enumPackage("ch.example.association.enums")
            .associationUiMode(GenerationConfig.ASSOCIATION_UI_READ_ONLY)
            .build();
        String rendered = new GrailsAssociationRegistryGenerator()
            .renderRegistry(mergedPlanner().plans(), config);

        assertThat(rendered).contains("createMode: 'NONE'");
        assertThat(rendered).doesNotContain("createMode: 'QUICK'");
        assertThat(rendered).doesNotContain("createMode: 'CONTEXTUAL_FORM'");
        assertThat(rendered).doesNotContain("writable: true");
        assertThat(rendered).doesNotContain("removable: true");
        assertGroovyCompiles(rendered);
    }

    @Test
    void offModeDisablesWritesInRegistry() throws Exception {
        GenerationConfig config = GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage(DOMAIN_PACKAGE)
            .enumPackage("ch.example.association.enums")
            .associationUiMode(GenerationConfig.ASSOCIATION_UI_OFF)
            .build();
        String rendered = new GrailsAssociationRegistryGenerator()
            .renderRegistry(mergedPlanner().plans(), config);

        assertThat(rendered).doesNotContain("createMode: 'QUICK'");
        assertThat(rendered).doesNotContain("writable: true");
        assertGroovyCompiles(rendered);
    }

    @Test
    void autoModeKeepsQuickCreateMode() throws Exception {
        String rendered = new GrailsAssociationRegistryGenerator()
            .renderRegistry(mergedPlanner().plans(), config());

        assertThat(rendered).contains("createMode: 'QUICK'");
        assertThat(rendered).contains("writable: true");
    }

    // ------------------------------------------------------------------

    private GrailsAssociationPlanner mergedPlanner() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        GenerationConfig config = config();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        return GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);
    }

    private GenerationConfig config() {
        return GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage(DOMAIN_PACKAGE)
            .enumPackage("ch.example.association.enums")
            .build();
    }

    private String entitiesBlock(String rendered) {
        int start = rendered.indexOf("ENTITIES = [");
        int end = rendered.indexOf("static Map<String, Object> association(");
        return rendered.substring(start, end);
    }

    private void assertGroovyCompiles(String source) {
        CompilerConfiguration configuration = new CompilerConfiguration();        configuration.setClasspath(System.getProperty("java.class.path"));
        CompilationUnit compilationUnit = new CompilationUnit(configuration);
        compilationUnit.addSource("InterlisAssociationRegistry.groovy", source);
        compilationUnit.compile();
    }
}

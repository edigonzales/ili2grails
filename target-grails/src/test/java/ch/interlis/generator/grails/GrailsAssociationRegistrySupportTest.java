package ch.interlis.generator.grails;

import ch.interlis.generator.testsupport.MetadataTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrailsAssociationRegistrySupportTest {

    @TempDir
    Path tempDir;

    @Test
    void registryContainsAllSixAssociations() throws Exception {

        GenerationConfig config = config();
        GrailsAssociationPlanner planner = buildPlanner(config);

        List<GrailsAssociationPlan> plans = planner.plans();
        assertThat(plans).hasSize(7);
    }

    @Test
    void registryContextsByParticipantReturnsCorrectNumberOfContexts() throws Exception {
        GenerationConfig config = config();
        GrailsAssociationPlanner planner = buildPlanner(config);

        List<GrailsAssociationContextPlan> personContexts =
            planner.contextsForParticipant("AssociationCases.Base.Person");
        assertThat(personContexts).hasSize(8);

        List<GrailsAssociationContextPlan> documentContexts =
            planner.contextsForParticipant("AssociationCases.Base.Document");
        assertThat(documentContexts).hasSize(2);
    }

    @Test
    void selfAssociationHasTwoDistinctContextsForSameTarget() throws Exception {
        GrailsAssociationPlanner planner = buildPlanner(config());

        GrailsAssociationPlan plan = planner.findPlan("AssociationCases.Base.SameTargetAssociation")
            .orElseThrow();
        List<GrailsAssociationContextPlan> contexts = plan.contexts();
        assertThat(contexts).hasSize(2);

        String ctx1 = contexts.get(0).contextId();
        String ctx2 = contexts.get(1).contextId();
        assertThat(ctx1).isNotEqualTo(ctx2);
        assertThat(ctx1).contains("::");
        assertThat(ctx2).contains("::");

        assertThat(contexts.get(0).fixedRoleName()).isIn("PrimaryPerson", "SecondaryPerson");
        assertThat(contexts.get(1).fixedRoleName()).isIn("PrimaryPerson", "SecondaryPerson");
        assertThat(contexts.get(0).fixedRoleName())
            .isNotEqualTo(contexts.get(1).fixedRoleName());
    }

    @Test
    void physicalMismatchContextHasCorrectFixedProperties() throws Exception {
        GrailsAssociationPlanner planner = buildPlanner(config());

        GrailsAssociationPlan plan = planner.findPlan("AssociationCases.Base.PhysicalMismatchAssociation")
            .orElseThrow();
        List<GrailsAssociationContextPlan> contexts = plan.contexts();
        assertThat(contexts).hasSize(2);

        GrailsAssociationContextPlan ownedParcelCtx = contexts.stream()
            .filter(ctx -> "OwnedParcel".equals(ctx.fixedRoleName()))
            .findFirst()
            .orElse(null);
        assertThat(ownedParcelCtx).isNotNull();
        assertThat(ownedParcelCtx.fixedRolePropertyName()).isEqualTo("parcelFk");

        GrailsAssociationContextPlan ownerCtx = contexts.stream()
            .filter(ctx -> "SemanticOwner".equals(ctx.fixedRoleName()))
            .findFirst()
            .orElse(null);
        assertThat(ownerCtx).isNotNull();
        assertThat(ownerCtx.fixedRolePropertyName()).isEqualTo("ownerFk");
    }

    @Test
    void contextHasAllRequiredFields() throws Exception {
        GrailsAssociationPlanner planner = buildPlanner(config());

        GrailsAssociationPlan plan = planner.findPlan("AssociationCases.Base.AssociationWithAttribute")
            .orElseThrow();
        List<GrailsAssociationContextPlan> contexts = plan.contexts();
        assertThat(contexts).isNotEmpty();

        for (GrailsAssociationContextPlan ctx : contexts) {
            assertThat(ctx.contextId()).isNotBlank();
            assertThat(ctx.participantDomainQualifiedName()).isNotBlank();
            assertThat(ctx.fixedRoleName()).isNotBlank();
            assertThat(ctx.fixedRolePropertyName()).isNotBlank();
            assertThat(ctx.presentationKind()).isNotNull();
            assertThat(ctx.createMode()).isNotNull();
            assertThat(ctx.writable()).isNotNull();
            assertThat(ctx.removable()).isNotNull();
        }
    }

    @Test
    void associationPlanContainsCorrectStructure() throws Exception {
        GrailsAssociationPlanner planner = buildPlanner(config());

        GrailsAssociationPlan assoc = planner.findPlan("AssociationCases.Base.AssociationWithAttribute")
            .orElseThrow();

        assertThat(assoc).isNotNull();
        assertThat(assoc.associationName()).isEqualTo("AssociationCases.Base.AssociationWithAttribute");
        assertThat(assoc.storageKind()).isEqualTo(AssociationStorageKind.LINK_ENTITY);
        assertThat(assoc.roles()).hasSize(2);
        assertThat(assoc.hasOwnAttributes()).isTrue();
        assertThat(assoc.isBinary()).isTrue();
        assertThat(assoc.isNary()).isFalse();

        GrailsAssociationRolePlan role = assoc.roles().get(0);
        assertThat(role.roleName()).isNotBlank();
        assertThat(role.domainPropertyName()).isNotBlank();
        assertThat(role.targetIliClassName()).isNotBlank();
        assertThat(role.targetDomainQualifiedName()).isNotBlank();
    }

    @Test
    void associationWithoutAttributesIsQuickLink() throws Exception {
        GrailsAssociationPlanner planner = buildPlanner(config());

        GrailsAssociationPlan plan = planner.findPlan("AssociationCases.Base.EmptyAssociation")
            .orElseThrow();

        assertThat(plan.hasOwnAttributes()).isFalse();
        assertThat(plan.isBinary()).isTrue();

        for (GrailsAssociationContextPlan ctx : plan.contexts()) {
            assertThat(ctx.createMode()).isEqualTo(AssociationCreateMode.QUICK);
        }
    }

    @Test
    void isAssociationDomainReturnsCorrectly() throws Exception {
        GrailsAssociationPlanner planner = buildPlanner(config());

        assertThat(planner.isAssociationDomain("AssociationCases.Base.AssociationWithAttribute")).isTrue();
        assertThat(planner.isAssociationDomain("AssociationCases.Base.EmptyAssociation")).isTrue();
        assertThat(planner.isAssociationDomain("AssociationCases.Base.Person")).isFalse();
        assertThat(planner.isAssociationDomain("AssociationCases.Base.Document")).isFalse();
        assertThat(planner.isAssociationDomain("AssociationCases.Base.Nonexistent")).isFalse();
    }

    @Test
    void showInNavigationHidesAssociationWhenContextualAccessExists() throws Exception {
        GrailsAssociationPlanner planner = buildPlanner(config());

        assertThat(planner.showDomainInNavigation("AssociationCases.Base.AssociationWithAttribute")).isFalse();
        assertThat(planner.showDomainInNavigation("AssociationCases.Base.EmptyAssociation")).isFalse();
        assertThat(planner.showDomainInNavigation("AssociationCases.Base.Person")).isTrue();
    }

    @Test
    void generatedRegistryCompilesAndContainsAssociationEntities() throws Exception {
        GenerationConfig config = config();
        GrailsAssociationPlanner planner = buildPlanner(config);
        GrailsAssociationRegistryGenerator generator = new GrailsAssociationRegistryGenerator();

        generator.generate(
            MetadataTestFixtures.readMergedAssociationCasesMetadata(),
            config,
            TargetNameRegistry.forMetadata(
                MetadataTestFixtures.readMergedAssociationCasesMetadata(), config),
            planner
        );

        Path registryFile = tempDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisAssociationRegistry.groovy");
        assertThat(registryFile).exists();

        String registryContent = Files.readString(registryFile, StandardCharsets.UTF_8);
        assertThat(registryContent).contains("ASSOCIATIONS = [");
        assertThat(registryContent).contains("CONTEXTS = [");
        assertThat(registryContent).contains("CONTEXT_IDS_BY_PARTICIPANT");
        assertThat(registryContent).contains("ENTITIES = [");
        assertThat(registryContent).contains("contextsForParticipant");
        assertThat(registryContent).contains("showInNavigation");

        GeneratedGroovyCompiler.compileGeneratedSources(tempDir);
    }

    private GenerationConfig config() {
        return GenerationConfig.builder(tempDir, "ch.example.association")
            .domainPackage("ch.example.association.domain")
            .controllerPackage("ch.example.association.controller")
            .build();
    }

    private GrailsAssociationPlanner buildPlanner(GenerationConfig config) throws Exception {
        return GrailsAssociationPlanner.forMetadata(
            MetadataTestFixtures.readMergedAssociationCasesMetadata(),
            config,
            TargetNameRegistry.forMetadata(
                MetadataTestFixtures.readMergedAssociationCasesMetadata(), config),
            GrailsRelationshipMapper.forMetadata(
                MetadataTestFixtures.readMergedAssociationCasesMetadata(), config,
                TargetNameRegistry.forMetadata(
                    MetadataTestFixtures.readMergedAssociationCasesMetadata(), config))
        );
    }
}

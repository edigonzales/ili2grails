package ch.interlis.generator.grails;

import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.builder.AssociationMetadataBuilder;
import ch.interlis.generator.model.builder.AssociationRoleMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsAssociationPlannerTest {

    private static final String DOMAIN_PACKAGE = "ch.example.association.domain";

    @TempDir
    Path tempDir;

    // ---------------------------------------------------------------------
    // Merged real-fixture cases
    // ---------------------------------------------------------------------

    @Test
    void binaryAssociationWithoutAttributesBecomesQuickLink() throws Exception {
        GrailsAssociationPlanner planner = mergedPlanner();
        GrailsAssociationPlan plan = plan(planner, "AssociationCases.Base.EmptyAssociation");

        assertThat(plan.isBinary()).isTrue();
        assertThat(plan.hasOwnAttributes()).isFalse();
        assertThat(plan.storageKind()).isEqualTo(AssociationStorageKind.LINK_ENTITY);
        assertThat(plan.writable()).isTrue();
        assertThat(plan.contexts())
            .isNotEmpty()
            .allSatisfy(context -> assertThat(context.createMode()).isEqualTo(AssociationCreateMode.QUICK));
    }

    @Test
    void associationWithAttributeUsesContextualForm() throws Exception {
        GrailsAssociationPlanner planner = mergedPlanner();
        GrailsAssociationPlan plan = plan(planner, "AssociationCases.Base.AssociationWithAttribute");

        assertThat(plan.hasOwnAttributes()).isTrue();
        assertThat(plan.attributes())
            .extracting(GrailsAssociationAttributePlan::iliName)
            .containsExactly("RoleNote");
        assertThat(plan.attributes())
            .extracting(GrailsAssociationAttributePlan::domainPropertyName)
            .containsExactly("roleNote");
        assertThat(plan.contexts())
            .isNotEmpty()
            .allSatisfy(context -> {
                assertThat(context.createMode()).isEqualTo(AssociationCreateMode.CONTEXTUAL_FORM);
                assertThat(context.presentationKind()).isEqualTo(AssociationPresentationKind.CONTEXTUAL_FORM);
            });
    }

    @Test
    void sameTargetRolesProduceDistinctContexts() throws Exception {
        GrailsAssociationPlanner planner = mergedPlanner();
        GrailsAssociationPlan plan = plan(planner, "AssociationCases.Base.SameTargetAssociation");

        assertThat(plan.contexts()).hasSize(2);
        assertThat(plan.contexts())
            .extracting(GrailsAssociationContextPlan::fixedRoleName)
            .containsExactly("PrimaryPerson", "SecondaryPerson");
        assertThat(plan.contexts())
            .extracting(GrailsAssociationContextPlan::fixedRolePropertyName)
            .containsExactly("primaryPersonId", "secondaryPersonId");
        assertThat(plan.contexts())
            .extracting(GrailsAssociationContextPlan::contextId)
            .doesNotHaveDuplicates();
    }

    @Test
    void physicalRoleNameMapsToGeneratedDomainProperty() throws Exception {
        GrailsAssociationPlanner planner = mergedPlanner();
        GrailsAssociationPlan plan = plan(planner, "AssociationCases.Base.PhysicalMismatchAssociation");

        assertThat(plan.role("SemanticOwner")).hasValueSatisfying(role -> {
            assertThat(role.semanticName()).isNotBlank();
            assertThat(role.domainPropertyName()).isEqualTo("ownerFk");
        });
        assertThat(plan.role("OwnedParcel")).hasValueSatisfying(role ->
            assertThat(role.domainPropertyName()).isEqualTo("parcelFk"));
    }

    @Test
    void externalAssociationIsNotQuickLinkByDefault() throws Exception {
        GrailsAssociationPlanner planner = mergedPlanner();
        GrailsAssociationPlan plan = plan(planner, "AssociationCases.Base.ExternalCompositeAssociation");

        assertThat(plan.role("Owner")).hasValueSatisfying(role -> assertThat(role.external()).isTrue());
        assertThat(plan.contexts())
            .noneSatisfy(context -> assertThat(context.createMode()).isEqualTo(AssociationCreateMode.QUICK));
    }

    @Test
    void compositionAssociationIsNotQuickLink() throws Exception {
        GrailsAssociationPlanner planner = mergedPlanner();
        GrailsAssociationPlan plan = plan(planner, "AssociationCases.Base.ExternalCompositeAssociation");

        assertThat(plan.role("Owner")).hasValueSatisfying(role -> assertThat(role.composition()).isTrue());
        assertThat(plan.contexts())
            .noneSatisfy(context -> assertThat(context.createMode()).isEqualTo(AssociationCreateMode.QUICK));
    }

    @Test
    void participantPerspectiveUsesOppositeRoleCardinality() throws Exception {
        GrailsAssociationPlanner planner = mergedPlanner();
        GrailsAssociationPlan plan = plan(planner, "AssociationCases.Base.EmptyAssociation");

        GrailsAssociationContextPlan personContext = context(plan, "PersonRole");
        GrailsAssociationContextPlan parcelContext = context(plan, "ParcelRole");

        // PersonRole {0..*}, ParcelRole {0..1}: the counterpart count for the
        // PersonRole perspective is bounded by the opposite ParcelRole.
        assertThat(personContext.perspectiveMinCardinality()).isEqualTo(0);
        assertThat(personContext.perspectiveMaxCardinality()).isEqualTo(1);
        // The ParcelRole perspective is bounded by the opposite unbounded PersonRole.
        assertThat(parcelContext.perspectiveMinCardinality()).isEqualTo(0);
        assertThat(parcelContext.perspectiveMaxCardinality()).isEqualTo(-1);
    }

    @Test
    void contextsAreDeterministicallySorted() throws Exception {
        GrailsAssociationPlanner planner = mergedPlanner();
        GrailsAssociationPlan plan = plan(planner, "AssociationCases.Base.SameTargetAssociation");

        List<String> contextIds = plan.contexts().stream()
            .map(GrailsAssociationContextPlan::contextId)
            .toList();
        assertThat(contextIds).isSorted();
        assertThat(contextIds)
            .containsExactly(
                "AssociationCases.Base.SameTargetAssociation::PrimaryPerson",
                "AssociationCases.Base.SameTargetAssociation::SecondaryPerson"
            );
    }

    @Test
    void associationControllerIsHiddenOnlyWhenContextualAccessExists() throws Exception {
        GrailsAssociationPlanner planner = mergedPlanner();

        assertThat(planner.showDomainInNavigation("AssociationCases.Base.EmptyAssociation")).isFalse();
        assertThat(planner.isAssociationDomain("AssociationCases.Base.EmptyAssociation")).isTrue();
        assertThat(planner.showDomainInNavigation("AssociationCases.Base.Person")).isTrue();
        assertThat(planner.isAssociationDomain("AssociationCases.Base.Person")).isFalse();
    }

    @Test
    void rolePropertiesMatchGeneratedAssociationDomainProperties() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        GenerationConfig config = config();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);

        GrailsAssociationPlan plan = plan(planner, "AssociationCases.Base.AssociationWithAttribute");
        ClassMetadata associationClass = metadata.getClass("AssociationCases.Base.AssociationWithAttribute");
        List<String> mappedProperties = mapper.map(associationClass).properties().stream()
            .map(GrailsRelationshipMapper.DomainProperty::name)
            .toList();

        assertThat(plan.roles())
            .extracting(GrailsAssociationRolePlan::domainPropertyName)
            .allSatisfy(property -> assertThat(mappedProperties).contains(property));
        assertThat(plan.roles())
            .extracting(GrailsAssociationRolePlan::domainPropertyName)
            .containsExactlyInAnyOrder("personRoleId", "documentRoleId");
    }

    @Test
    void planUsesTargetNameRegistryForQualifiedDomainNames() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        GenerationConfig config = config();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);

        GrailsAssociationPlan plan = plan(planner, "AssociationCases.Base.AssociationWithAttribute");
        ClassMetadata associationClass = metadata.getClass("AssociationCases.Base.AssociationWithAttribute");

        assertThat(plan.associationDomainClassName()).isEqualTo(registry.className(associationClass));
        assertThat(plan.associationDomainQualifiedName())
            .isEqualTo(DOMAIN_PACKAGE + "." + registry.className(associationClass));
        assertThat(plan.role("PersonRole")).hasValueSatisfying(role ->
            assertThat(role.targetDomainQualifiedName())
                .isEqualTo(DOMAIN_PACKAGE + "." + registry.className("AssociationCases.Base.Person")));
    }

    @Test
    void planDoesNotMutateCoreMetadata() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        int associationCount = metadata.getAllAssociations().size();
        int relationshipCount = metadata.getAllRelationships().size();
        int classCount = metadata.getAllClasses().size();
        AssociationMetadata empty = metadata.getAssociation("AssociationCases.Base.EmptyAssociation");
        int emptyRoleCount = empty.getRoles().size();

        GenerationConfig config = config();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);

        assertThat(metadata.getAllAssociations()).hasSize(associationCount);
        assertThat(metadata.getAllRelationships()).hasSize(relationshipCount);
        assertThat(metadata.getAllClasses()).hasSize(classCount);
        assertThat(empty.getRoles()).hasSize(emptyRoleCount);
    }

    // ---------------------------------------------------------------------
    // Synthetic cases (n-ary, ordered, unmapped, ambiguous)
    // ---------------------------------------------------------------------

    @Test
    void naryAssociationUsesNaryContextualForm() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("Ternary");
        ClassMetadata person = persistentClass("Ternary.Person", "person");
        ClassMetadata parcel = persistentClass("Ternary.Parcel", "parcel");
        ClassMetadata document = persistentClass("Ternary.Document", "document");
        ClassMetadata link = associationClass("Ternary.TernaryAssociation", "ternaryassociation");
        modelBuilder.addClassFrom(person);
        modelBuilder.addClassFrom(parcel);
        modelBuilder.addClassFrom(document);
        modelBuilder.addClassFrom(link);

        AssociationMetadataBuilder association = modelBuilder.associationBuilder("Ternary.TernaryAssociation");
        association.associationClass(link.getName());
        association.physicalTable("ternaryassociation");
        association.role(AssociationRoleMetadataBuilder.from(role("PersonRole", person.getName(), 1, 1)));
        association.role(AssociationRoleMetadataBuilder.from(role("ParcelRole", parcel.getName(), 1, 1)));
        association.role(AssociationRoleMetadataBuilder.from(role("DocumentRole", document.getName(), 0, 1)));
        ;

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);
        GrailsAssociationPlanner planner = planner(metadata);
        GrailsAssociationPlan plan = plan(planner, "Ternary.TernaryAssociation");

        assertThat(plan.isNary()).isTrue();
        assertThat(plan.contexts()).hasSize(3);
        assertThat(plan.contexts()).allSatisfy(context -> {
            assertThat(context.presentationKind()).isEqualTo(AssociationPresentationKind.NARY_CONTEXTUAL_FORM);
            assertThat(context.createMode()).isNotEqualTo(AssociationCreateMode.QUICK);
            assertThat(context.perspectiveMaxCardinality()).isNull();
            assertThat(context.perspectiveMinCardinality()).isNull();
            assertThat(context.editableRoleNames()).hasSize(2);
        });
    }

    @Test
    void orderedAssociationIsNotQuickLinkWithoutOrderMapping() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("Ordered");
        ClassMetadata person = persistentClass("Ordered.Person", "person");
        ClassMetadata document = persistentClass("Ordered.Document", "document");
        ClassMetadata link = associationClass("Ordered.OrderedAssociation", "orderedassociation");
        modelBuilder.addClassFrom(person);
        modelBuilder.addClassFrom(document);
        modelBuilder.addClassFrom(link);

        AssociationMetadataBuilder association = modelBuilder.associationBuilder("Ordered.OrderedAssociation");
        association.associationClass(link.getName());
        association.physicalTable("orderedassociation");
        association.role(AssociationRoleMetadataBuilder.from(role("Owner", person.getName(), 1, 1)));
        AssociationRoleMetadataBuilder documents = AssociationRoleMetadataBuilder.from(role("Documents", document.getName(), 0, -1));
        documents.ordered(true);
        association.role(documents);
        ;

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);
        GrailsAssociationPlanner planner = planner(metadata);
        GrailsAssociationPlan plan = plan(planner, "Ordered.OrderedAssociation");

        assertThat(plan.role("Documents")).hasValueSatisfying(role -> assertThat(role.ordered()).isTrue());
        assertThat(plan.contexts())
            .noneSatisfy(context -> assertThat(context.createMode()).isEqualTo(AssociationCreateMode.QUICK));
        assertThat(plan.contexts())
            .allSatisfy(context -> assertThat(context.createMode()).isEqualTo(AssociationCreateMode.CONTEXTUAL_FORM));
    }

    @Test
    void associationWithoutPhysicalClassIsReadOnly() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("Unmapped");
        ClassMetadata person = persistentClass("Unmapped.Person", "person");
        ClassMetadata parcel = persistentClass("Unmapped.Parcel", "parcel");
        // Association class without a physical table -> embedded FK.
        ClassMetadata link = associationClass("Unmapped.UnmappedAssociation", null);
        modelBuilder.addClassFrom(person);
        modelBuilder.addClassFrom(parcel);
        modelBuilder.addClassFrom(link);

        AssociationMetadataBuilder association = modelBuilder.associationBuilder("Unmapped.UnmappedAssociation");
        association.associationClass(link.getName());
        association.role(AssociationRoleMetadataBuilder.from(role("PersonRole", person.getName(), 0, -1)));
        association.role(AssociationRoleMetadataBuilder.from(role("ParcelRole", parcel.getName(), 0, 1)));
        ;

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);
        GrailsAssociationPlanner planner = planner(metadata);
        GrailsAssociationPlan plan = plan(planner, "Unmapped.UnmappedAssociation");

        assertThat(plan.storageKind()).isEqualTo(AssociationStorageKind.EMBEDDED_FOREIGN_KEY);
        assertThat(plan.physicalMappingPresent()).isTrue();
        assertThat(plan.writable()).isFalse();
        assertThat(plan.diagnostics())
            .contains(GrailsAssociationPlanner.DIAGNOSTIC_EMBEDDED_FK_ASSOCIATION);
        assertThat(plan.contexts()).allSatisfy(context -> {
            assertThat(context.presentationKind()).isEqualTo(AssociationPresentationKind.READ_ONLY);
            assertThat(context.createMode()).isEqualTo(AssociationCreateMode.NONE);
            assertThat(context.writable()).isFalse();
        });
        assertThat(planner.showDomainInNavigation("Unmapped.UnmappedAssociation")).isFalse();
    }

    @Test
    void standaloneExternalAssociationIsNotQuickLink() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("ExternalOnly");
        ClassMetadata person = persistentClass("ExternalOnly.Person", "person");
        ClassMetadata building = persistentClass("ExternalOnly.Building", "building");
        ClassMetadata link = associationClass("ExternalOnly.ExternalOnlyAssociation", "externalonlyassociation");
        modelBuilder.addClassFrom(person);
        modelBuilder.addClassFrom(building);
        modelBuilder.addClassFrom(link);

        AssociationMetadataBuilder association = modelBuilder.associationBuilder("ExternalOnly.ExternalOnlyAssociation");
        association.associationClass(link.getName());
        association.physicalTable("externalonlyassociation");
        association.role(AssociationRoleMetadataBuilder.from(role("PersonRole", person.getName(), 1, 1)));
        AssociationRoleMetadataBuilder externalRole = AssociationRoleMetadataBuilder.from(role("ExternalRole", building.getName(), 0, -1));
        externalRole.external(true);
        association.role(externalRole);
        ;

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);
        GrailsAssociationPlanner planner = planner(metadata);
        GrailsAssociationPlan plan = plan(planner, "ExternalOnly.ExternalOnlyAssociation");

        assertThat(plan.role("ExternalRole")).hasValueSatisfying(role -> {
            assertThat(role.external()).isTrue();
            assertThat(role.composition()).isFalse();
        });
        assertThat(plan.contexts())
            .noneSatisfy(context -> assertThat(context.createMode()).isEqualTo(AssociationCreateMode.QUICK));
        assertThat(plan.contexts())
            .allSatisfy(context -> assertThat(context.createMode()).isEqualTo(AssociationCreateMode.CONTEXTUAL_FORM));
    }

    @Test
    void externalOnlyContextIsClassifiedAsContextualForm() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("ExternalOnly2");
        ClassMetadata person = persistentClass("ExternalOnly2.Person", "person");
        ClassMetadata building = persistentClass("ExternalOnly2.Building", "building");
        ClassMetadata link = associationClass("ExternalOnly2.ExternalOnly2Assoc", "externalonly2_assoc");
        modelBuilder.addClassFrom(person);
        modelBuilder.addClassFrom(building);
        modelBuilder.addClassFrom(link);

        AssociationMetadataBuilder association = modelBuilder.associationBuilder("ExternalOnly2.ExternalOnly2Assoc");
        association.associationClass(link.getName());
        association.physicalTable("externalonly2_assoc");
        association.role(AssociationRoleMetadataBuilder.from(role("PersonRole", person.getName(), 1, 1)));
        AssociationRoleMetadataBuilder externalRole = AssociationRoleMetadataBuilder.from(role("ExternalRole", building.getName(), 0, -1));
        externalRole.external(true);
        association.role(externalRole);
        ;

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);
        GrailsAssociationPlanner planner = planner(metadata);
        GrailsAssociationPlan plan = plan(planner, "ExternalOnly2.ExternalOnly2Assoc");

        assertThat(plan.contexts())
            .allSatisfy(context ->
                assertThat(context.presentationKind()).isNotEqualTo(AssociationPresentationKind.READ_ONLY));
        assertThat(plan.contexts())
            .allSatisfy(context -> assertThat(context.writable()).isTrue());
    }

    @Test
    void ambiguousRolePropertyCreatesDiagnosticAndReadOnlyContext() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("Ambiguous");
        ClassMetadata person = persistentClass("Ambiguous.Person", "person");
        ClassMetadata link = associationClass("Ambiguous.AmbiguousAssociation", "ambiguousassociation");
        modelBuilder.addClassFrom(person);
        modelBuilder.addClassFrom(link);

        AssociationMetadataBuilder association = modelBuilder.associationBuilder("Ambiguous.AmbiguousAssociation");
        association.associationClass(link.getName());
        association.physicalTable("ambiguousassociation");
        // Two distinct roles that share the same role name -> resolution is ambiguous.
        AssociationRoleMetadataBuilder first = AssociationRoleMetadataBuilder.from(role("PersonRole", person.getName(), 0, 1));
        first.sourceAttribute("a_fk");
        AssociationRoleMetadataBuilder second = AssociationRoleMetadataBuilder.from(role("PersonRole", person.getName(), 0, 1));
        second.sourceAttribute("b_fk");
        association.replaceRoles(List.of(first, second));
        ;

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);
        GrailsAssociationPlanner planner = planner(metadata);
        GrailsAssociationPlan plan = plan(planner, "Ambiguous.AmbiguousAssociation");

        assertThat(plan.diagnostics())
            .anyMatch(diagnostic -> diagnostic.startsWith(GrailsAssociationPlanner.DIAGNOSTIC_AMBIGUOUS_ROLE_PROPERTY));
        assertThat(plan.role("PersonRole")).hasValueSatisfying(role ->
            assertThat(role.domainPropertyName()).isNull());
        assertThat(plan.contexts()).allSatisfy(context -> {
            assertThat(context.writable()).isFalse();
            assertThat(context.presentationKind()).isEqualTo(AssociationPresentationKind.READ_ONLY);
        });
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private GrailsAssociationPlanner mergedPlanner() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        return planner(metadata);
    }

    private GrailsAssociationPlanner planner(ModelMetadata metadata) {
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

    private GrailsAssociationPlan plan(GrailsAssociationPlanner planner, String associationName) {
        return planner.findPlan(associationName)
            .orElseThrow(() -> new AssertionError("Missing plan for " + associationName));
    }

    private GrailsAssociationContextPlan context(GrailsAssociationPlan plan, String fixedRoleName) {
        return plan.contexts().stream()
            .filter(context -> fixedRoleName.equals(context.fixedRoleName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing context for role " + fixedRoleName));
    }

    private ClassMetadata persistentClass(String name, String tableName) {
        return ClassMetadata.builder(name)
            .kind(ClassMetadata.ClassKind.CLASS)
            .tableName(tableName)
            .buildUnchecked();
    }

    private ClassMetadata associationClass(String name, String tableName) {
        return ClassMetadata.builder(name)
            .kind(ClassMetadata.ClassKind.ASSOCIATION)
            .tableName(tableName)
            .buildUnchecked();
    }

    private AssociationRoleMetadata role(String name, String targetClass, int min, int max) {
        return AssociationRoleMetadata.builder(name)
            .targetClass(targetClass)
            .cardinality(ch.interlis.generator.model.Cardinality.of(1, 1, min, max))
            .mandatory(min > 0)
            .buildUnchecked();
    }
}

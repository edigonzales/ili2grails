package ch.interlis.generator.grails;

import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
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
        ModelMetadata metadata = new ModelMetadata("Ternary");
        ClassMetadata person = persistentClass("Ternary.Person", "person");
        ClassMetadata parcel = persistentClass("Ternary.Parcel", "parcel");
        ClassMetadata document = persistentClass("Ternary.Document", "document");
        ClassMetadata link = associationClass("Ternary.TernaryAssociation", "ternaryassociation");
        metadata.addClass(person);
        metadata.addClass(parcel);
        metadata.addClass(document);
        metadata.addClass(link);

        AssociationMetadata association = new AssociationMetadata("Ternary.TernaryAssociation");
        association.setAssociationClass(link.getName());
        association.setPhysicalTable("ternaryassociation");
        association.addRole(role("PersonRole", person.getName(), 1, 1));
        association.addRole(role("ParcelRole", parcel.getName(), 1, 1));
        association.addRole(role("DocumentRole", document.getName(), 0, 1));
        metadata.addAssociation(association);

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
        ModelMetadata metadata = new ModelMetadata("Ordered");
        ClassMetadata person = persistentClass("Ordered.Person", "person");
        ClassMetadata document = persistentClass("Ordered.Document", "document");
        ClassMetadata link = associationClass("Ordered.OrderedAssociation", "orderedassociation");
        metadata.addClass(person);
        metadata.addClass(document);
        metadata.addClass(link);

        AssociationMetadata association = new AssociationMetadata("Ordered.OrderedAssociation");
        association.setAssociationClass(link.getName());
        association.setPhysicalTable("orderedassociation");
        association.addRole(role("Owner", person.getName(), 1, 1));
        AssociationRoleMetadata documents = role("Documents", document.getName(), 0, -1);
        documents.setOrdered(true);
        association.addRole(documents);
        metadata.addAssociation(association);

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
        ModelMetadata metadata = new ModelMetadata("Unmapped");
        ClassMetadata person = persistentClass("Unmapped.Person", "person");
        ClassMetadata parcel = persistentClass("Unmapped.Parcel", "parcel");
        // Association class without a physical table -> embedded FK.
        ClassMetadata link = new ClassMetadata("Unmapped.UnmappedAssociation");
        link.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        metadata.addClass(person);
        metadata.addClass(parcel);
        metadata.addClass(link);

        AssociationMetadata association = new AssociationMetadata("Unmapped.UnmappedAssociation");
        association.setAssociationClass(link.getName());
        association.addRole(role("PersonRole", person.getName(), 0, -1));
        association.addRole(role("ParcelRole", parcel.getName(), 0, 1));
        metadata.addAssociation(association);

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
        ModelMetadata metadata = new ModelMetadata("ExternalOnly");
        ClassMetadata person = persistentClass("ExternalOnly.Person", "person");
        ClassMetadata building = persistentClass("ExternalOnly.Building", "building");
        ClassMetadata link = associationClass("ExternalOnly.ExternalOnlyAssociation", "externalonlyassociation");
        metadata.addClass(person);
        metadata.addClass(building);
        metadata.addClass(link);

        AssociationMetadata association = new AssociationMetadata("ExternalOnly.ExternalOnlyAssociation");
        association.setAssociationClass(link.getName());
        association.setPhysicalTable("externalonlyassociation");
        association.addRole(role("PersonRole", person.getName(), 1, 1));
        AssociationRoleMetadata externalRole = role("ExternalRole", building.getName(), 0, -1);
        externalRole.setExternal(true);
        association.addRole(externalRole);
        metadata.addAssociation(association);

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
        ModelMetadata metadata = new ModelMetadata("ExternalOnly2");
        ClassMetadata person = persistentClass("ExternalOnly2.Person", "person");
        ClassMetadata building = persistentClass("ExternalOnly2.Building", "building");
        ClassMetadata link = associationClass("ExternalOnly2.ExternalOnly2Assoc", "externalonly2_assoc");
        metadata.addClass(person);
        metadata.addClass(building);
        metadata.addClass(link);

        AssociationMetadata association = new AssociationMetadata("ExternalOnly2.ExternalOnly2Assoc");
        association.setAssociationClass(link.getName());
        association.setPhysicalTable("externalonly2_assoc");
        association.addRole(role("PersonRole", person.getName(), 1, 1));
        AssociationRoleMetadata externalRole = role("ExternalRole", building.getName(), 0, -1);
        externalRole.setExternal(true);
        association.addRole(externalRole);
        metadata.addAssociation(association);

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
        ModelMetadata metadata = new ModelMetadata("Ambiguous");
        ClassMetadata person = persistentClass("Ambiguous.Person", "person");
        ClassMetadata link = associationClass("Ambiguous.AmbiguousAssociation", "ambiguousassociation");
        metadata.addClass(person);
        metadata.addClass(link);

        AssociationMetadata association = new AssociationMetadata("Ambiguous.AmbiguousAssociation");
        association.setAssociationClass(link.getName());
        association.setPhysicalTable("ambiguousassociation");
        // Two distinct roles that share the same role name -> resolution is ambiguous.
        AssociationRoleMetadata first = role("PersonRole", person.getName(), 0, 1);
        first.setSourceAttribute("a_fk");
        AssociationRoleMetadata second = role("PersonRole", person.getName(), 0, 1);
        second.setSourceAttribute("b_fk");
        association.addRole(first);
        association.addRole(second);
        metadata.addAssociation(association);

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
        ClassMetadata classMetadata = new ClassMetadata(name);
        classMetadata.setKind(ClassMetadata.ClassKind.CLASS);
        classMetadata.setTableName(tableName);
        return classMetadata;
    }

    private ClassMetadata associationClass(String name, String tableName) {
        ClassMetadata classMetadata = new ClassMetadata(name);
        classMetadata.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        classMetadata.setTableName(tableName);
        return classMetadata;
    }

    private AssociationRoleMetadata role(String name, String targetClass, int min, int max) {
        AssociationRoleMetadata role = new AssociationRoleMetadata(name);
        role.setTargetClass(targetClass);
        role.setCardinality(new RelationshipMetadata.Cardinality(1, 1, min, max));
        role.setMandatory(min > 0);
        return role;
    }
}

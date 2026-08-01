package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeDescriptorSeverity;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runtime-Descriptor-Diagnostics (Spezifikation §64).
 */
class RuntimeDescriptorPlannerTest {

    @TempDir
    Path tempDir;

    @Test
    void unresolvedWritableRelationshipIsBlocking() throws Exception {
        ModelMetadata metadata = metadataWithRelationshipTargeting("TestModel.Topic.Other", false);
        RuntimeDescriptorPlan plan = plan(metadata);
        assertThat(plan.hasBlockingDiagnostics()).isTrue();
        assertThat(plan.diagnostics())
            .filteredOn(diagnostic -> diagnostic.code() == RuntimeDescriptorDiagnosticCode.UNRESOLVED_TARGET_CLASS)
            .allSatisfy(diagnostic -> {
                assertThat(diagnostic.severity()).isEqualTo(RuntimeDescriptorSeverity.ERROR);
                assertThat(diagnostic.details()).containsEntry("writable", "true");
            });
        assertThatThrownBy(plan::throwIfBlocking)
            .isInstanceOf(RuntimeDescriptorPlanningException.class);
    }

    @Test
    void unresolvedReadOnlyExternalRelationshipIsWarning() throws Exception {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("TestModel");
        builder.classBuilder("TestModel.Topic.Owner")
            .tableName("owner")
            .attribute(new AttributeMetadataBuilder("extRef")
                .foreignKey(true)
                .referencedClass("ExternalModel.ExternalTopic.ExternalClass")
                .javaType("Long")
                .mandatory(false));
        builder.relationship(RelationshipMetadata.builder("Owner_External")
            .sourceClass("TestModel.Topic.Owner")
            .targetClass("ExternalModel.ExternalTopic.ExternalClass")
            .sourceAttribute("extRef")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .external(true));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(builder);

        RuntimeDescriptorPlan plan = plan(metadata);
        assertThat(plan.hasBlockingDiagnostics()).isFalse();
        assertThat(plan.diagnostics())
            .filteredOn(diagnostic -> diagnostic.code() == RuntimeDescriptorDiagnosticCode.UNRESOLVED_TARGET_CLASS)
            .allSatisfy(diagnostic ->
                assertThat(diagnostic.severity()).isEqualTo(RuntimeDescriptorSeverity.WARNING));
    }

    @Test
    void inverseWithoutRelatedClassIsBlockingWhenWritable() throws Exception {
        ModelMetadata metadata = simpleMetadata();
        GenerationConfig config = config();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);
        GrailsInverseRelationshipPlanner inverses =
            GrailsInverseRelationshipPlanner.forMetadata(metadata, config, registry, mapper);
        RuntimeDescriptorPlanner descriptorPlanner =
            new RuntimeDescriptorPlanner(registry, mapper, planner, inverses);

        // Related class ist nicht generiert -> writable inverse Beziehung blockiert.
        GrailsInverseRelationshipPlan missingRelated = new GrailsInverseRelationshipPlan(
            "TestModel.Topic.Department", "employees", "TestModel.Topic.NotGenerated",
            "com.example.domain.NotGenerated", "department", "Department_Employees",
            "Employees", "Employees", true, true, true, true);
        List<RuntimeDescriptorDiagnostic> diagnostics = new ArrayList<>();
        descriptorPlanner.planInverse(missingRelated, diagnostics);
        assertThat(diagnostics)
            .filteredOn(RuntimeDescriptorDiagnostic::blocking)
            .filteredOn(diagnostic ->
                diagnostic.code() == RuntimeDescriptorDiagnosticCode.UNRESOLVED_RELATED_CLASS)
            .hasSize(1);

        // Read-only inverse Beziehung ist nur eine WARNING.
        GrailsInverseRelationshipPlan readOnly = new GrailsInverseRelationshipPlan(
            "TestModel.Topic.Department", "employees", "TestModel.Topic.NotGenerated",
            "com.example.domain.NotGenerated", "department", "Department_Employees",
            "Employees", "Employees", false, true, false, true);
        List<RuntimeDescriptorDiagnostic> readOnlyDiagnostics = new ArrayList<>();
        descriptorPlanner.planInverse(readOnly, readOnlyDiagnostics);
        assertThat(readOnlyDiagnostics)
            .filteredOn(diagnostic ->
                diagnostic.code() == RuntimeDescriptorDiagnosticCode.UNRESOLVED_RELATED_CLASS)
            .allSatisfy(diagnostic -> assertThat(diagnostic.blocking()).isFalse());
    }

    @Test
    void duplicateContextIdIsBlocking() {
        List<RuntimeDescriptorDiagnostic> diagnostics = new ArrayList<>();
        RuntimeDescriptorPlanner.detectDuplicateContexts(
            List.of(duplicateContext("same-id"), duplicateContext("same-id")),
            diagnostics);
        assertThat(diagnostics)
            .filteredOn(RuntimeDescriptorDiagnostic::blocking)
            .filteredOn(diagnostic ->
                diagnostic.code() == RuntimeDescriptorDiagnosticCode.DUPLICATE_CONTEXT_DESCRIPTOR)
            .hasSize(1);
        RuntimeDescriptorPlan manual = new RuntimeDescriptorPlan(
            List.of(), List.of(), List.of(duplicateContext("same-id"), duplicateContext("same-id")),
            diagnostics);
        assertThat(manual.hasBlockingDiagnostics()).isTrue();
        assertThatThrownBy(manual::throwIfBlocking)
            .isInstanceOf(RuntimeDescriptorPlanningException.class);
    }

    @Test
    void duplicateDomainAndAssociationNamesAreBlocking() {
        List<RuntimeDescriptorDiagnostic> domainDiagnostics = new ArrayList<>();
        RuntimeDescriptorPlanner.detectDuplicateDomains(
            List.of(duplicateDomain("com.example.X"), duplicateDomain("com.example.X")),
            domainDiagnostics);
        assertThat(domainDiagnostics)
            .filteredOn(RuntimeDescriptorDiagnostic::blocking)
            .filteredOn(diagnostic ->
                diagnostic.code() == RuntimeDescriptorDiagnosticCode.DUPLICATE_DOMAIN_DESCRIPTOR)
            .hasSize(1);

        List<RuntimeDescriptorDiagnostic> associationDiagnostics = new ArrayList<>();
        RuntimeDescriptorPlanner.detectDuplicateAssociations(
            List.of(duplicateAssociation("same"), duplicateAssociation("same")),
            associationDiagnostics);
        assertThat(associationDiagnostics)
            .filteredOn(RuntimeDescriptorDiagnostic::blocking)
            .filteredOn(diagnostic ->
                diagnostic.code() == RuntimeDescriptorDiagnosticCode.DUPLICATE_ASSOCIATION_DESCRIPTOR)
            .hasSize(1);
    }

    @Test
    void planDiagnosticsAreSortedDeterministically() throws Exception {
        ModelMetadata metadata = metadataWithRelationshipTargeting("TestModel.Topic.Other", false);
        RuntimeDescriptorPlan first = plan(metadata);
        RuntimeDescriptorPlan second = plan(metadata);
        assertThat(second.diagnostics()).containsExactlyElementsOf(first.diagnostics());
        List<RuntimeDescriptorDiagnostic> diagnostics = first.diagnostics();
        for (int i = 1; i < diagnostics.size(); i++) {
            String previous = diagnostics.get(i - 1).code().name()
                + diagnostics.get(i - 1).subject();
            String current = diagnostics.get(i).code().name() + diagnostics.get(i).subject();
            assertThat(previous.compareTo(current)).isLessThanOrEqualTo(0);
        }
    }

    @Test
    void noFilesArePlannedWhenDescriptorPlanIsBlocking() throws Exception {
        ModelMetadata metadata = metadataWithRelationshipTargeting("TestModel.Topic.Other", false);
        RuntimeDescriptorPlan plan = plan(metadata);
        assertThat(plan.hasBlockingDiagnostics()).isTrue();

        Path outputDir = tempDir.resolve("blocked-project");
        GenerationConfig config = GenerationConfig.builder(outputDir, "com.example").build();
        assertThatThrownBy(() -> new GrailsCrudGenerator().generate(metadata, config))
            .isInstanceOf(RuntimeDescriptorPlanningException.class);
        assertThat(outputDir.toFile().list()).isNullOrEmpty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ModelMetadata simpleMetadata() throws Exception {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("TestModel");
        builder.classBuilder("TestModel.Topic.Department")
            .tableName("department")
            .attribute(new AttributeMetadataBuilder("name").javaType("String"));
        builder.classBuilder("TestModel.Topic.Employee")
            .tableName("employee")
            .attribute(new AttributeMetadataBuilder("department")
                .foreignKey(true)
                .referencedClass("TestModel.Topic.Department")
                .javaType("Long")
                .mandatory(false));
        builder.relationship(RelationshipMetadata.builder("TestModel.Topic.Employee_Department")
            .sourceClass("TestModel.Topic.Employee")
            .targetClass("TestModel.Topic.Department")
            .sourceAttribute("department")
            .targetRoleName("Department")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE));
        return new ModelMetadataFactory().buildValidated(builder);
    }

    private GenerationConfig config() {
        return GenerationConfig.builder(tempDir, "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .build();
    }

    private ModelMetadata metadataWithRelationshipTargeting(String targetIliClass,
                                                            boolean external) throws Exception {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("TestModel");
        builder.classBuilder("TestModel.Topic.Owner")
            .tableName("owner")
            .attribute(new AttributeMetadataBuilder("ref")
                .foreignKey(true)
                .referencedClass(targetIliClass)
                .javaType("Long")
                .mandatory(false));
        builder.relationship(RelationshipMetadata.builder("Owner_Ref")
            .sourceClass("TestModel.Topic.Owner")
            .targetClass(targetIliClass)
            .sourceAttribute("ref")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .external(external));
        return new ModelMetadataFactory().buildValidated(builder);
    }

    private RuntimeDescriptorPlan plan(ModelMetadata metadata) {
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);
        GrailsInverseRelationshipPlanner inverses =
            GrailsInverseRelationshipPlanner.forMetadata(metadata, config, registry, mapper);
        return new RuntimeDescriptorPlanner(registry, mapper, planner, inverses).plan(metadata, config);
    }

    private ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor duplicateContext(
        String id) {
        return new ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor(
            id, "assoc", "com.example.domain.Person", "role", "prop",
            List.of(), List.of(), "label", "msg", "LINK",
            ch.interlis.generator.grails.runtime.api.descriptor.AssociationCreateMode.NONE,
            false, false, false, 0, -1, List.of());
    }

    private ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor duplicateDomain(
        String domainClassName) {
        return new ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor(
            "TestModel.Topic.X", "TestModel", "Topic", domainClassName, "topic/x", "X",
            "X", ch.interlis.generator.grails.runtime.api.descriptor.DomainKind.CLASS,
            true, new ch.interlis.generator.grails.runtime.api.descriptor.DisplayDescriptor(
                null, List.of(), List.of()),
            new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>(),
            new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>());
    }

    private ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor duplicateAssociation(
        String associationName) {
        return new ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor(
            associationName, "TestModel.Topic.Link", "com.example.domain.Link",
            "link", "topic/link", "link", "link",
            ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind.LINK_ENTITY,
            true, true, List.of(), List.of(), List.of());
    }
}

package ch.interlis.generator.grails;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.model.builder.RelationshipMetadataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsInverseRelationshipPlannerTest {

    @TempDir
    Path tempDir;

    @Test
    void mapsGeneratedCollectionBackToExactRelatedProperty() throws Exception {
        Fixture fixture = fixture("departmentReference", "department");
        fixture.relatedAttribute().mandatory(true);
        fixture.relationship().mandatory(true);

        GrailsInverseRelationshipPlanner planner = planner(
            fixture.metadata(),
            GenerationConfig.builder(tempDir, "com.example")
                .domainPackage("com.example.domain")
                .build()
        );

        assertThat(planner.plansForOwner(fixture.owner().name()))
            .singleElement()
            .satisfies(plan -> {
                assertThat(plan.collectionPropertyName()).isEqualTo("employees");
                assertThat(plan.relatedPropertyName()).isEqualTo("department");
                assertThat(plan.relatedDomainQualifiedName()).isEqualTo("com.example.domain.Employee");
                assertThat(plan.mandatory()).isTrue();
                assertThat(plan.visible()).isTrue();
                assertThat(plan.writable()).isTrue();
            });
    }

    @Test
    void excludesUnsafeInverseRelationships() {
        Fixture composition = fixture("department", "department");
        composition.relationship().composition(true);
        assertThat(planner(composition.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture external = fixture("department", "department");
        external.relationship().external(true);
        assertThat(planner(external.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture ordered = fixture("department", "department");
        ordered.relationship().ordered(true);
        assertThat(planner(ordered.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture nonPhysical = fixture("department", null);
        assertThat(planner(nonPhysical.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture incompleteSource = fixture("department", "department");
        incompleteSource.related().abstractClass(true);
        assertThat(planner(incompleteSource.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture incompleteOwner = fixture("department", "department");
        incompleteOwner.owner().abstractClass(true);
        assertThat(planner(incompleteOwner.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture ambiguous = fixture("department", "department");
        AttributeMetadataBuilder duplicateReference = new AttributeMetadataBuilder("departmentAlias")
            .javaType("Long")
            .foreignKey(true)
            .referencedClass(ambiguous.owner().name())
            .sqlName("department")
            .columnName("department");
        ambiguous.related().attribute(duplicateReference);
        assertThat(planner(ambiguous.metadata(), defaultConfig()).plans()).isEmpty();
    }

    @Test
    void generationModeIsAnUpperBoundForVisibilityAndWritability() {
        Fixture fixture = fixture("department", "department");

        GenerationConfig readOnly = GenerationConfig.builder(tempDir.resolve("read-only"), "com.example")
            .associationUiMode(GenerationConfig.ASSOCIATION_UI_READ_ONLY)
            .build();
        assertThat(planner(fixture.metadata(), readOnly).plans())
            .singleElement()
            .satisfies(plan -> {
                assertThat(plan.visible()).isTrue();
                assertThat(plan.writable()).isFalse();
            });

        GenerationConfig off = GenerationConfig.builder(tempDir.resolve("off"), "com.example")
            .associationUiMode(GenerationConfig.ASSOCIATION_UI_OFF)
            .build();
        assertThat(planner(fixture.metadata(), off).plans())
            .singleElement()
            .satisfies(plan -> {
                assertThat(plan.visible()).isFalse();
                assertThat(plan.writable()).isFalse();
            });
    }

    @Test
    void domainGeneratorEmitsInverseMetadataWithoutChangingForeignKeyMapping() throws Exception {
        Fixture fixture = fixture("departmentReference", "department");
        fixture.relatedAttribute().mandatory(true);
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example")
            .domainPackage("com.example.domain")
            .build();

        TargetNameRegistry registry = TargetNameRegistry.forMetadata(fixture.metadata(), config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(
            fixture.metadata(), config, registry);
        GrailsInverseRelationshipPlanner inversePlanner =
            GrailsInverseRelationshipPlanner.forMetadata(fixture.metadata(), config, registry, mapper);
        List<ch.interlis.generator.grails.project.plan.PlannedProjectFile> domains =
            new GrailsDomainGenerator().plan(fixture.metadata(), config, registry, mapper, inversePlanner);
        String department = plannedContent(domains, "Department.groovy");
        String employee = plannedContent(domains, "Employee.groovy");
        assertThat(department)
            .contains("static final Map<String, Map<String, Object>> interlisInverseRelationshipMeta")
            .contains("employees: [relatedDomainClass: 'com.example.domain.Employee'")
            .contains("relatedProperty: 'department'")
            .contains("mandatory: true")
            .contains("writable: true");
        assertThat(employee)
            .contains("Department department")
            .contains("department column: 'department'");
    }

    private String plannedContent(
        List<ch.interlis.generator.grails.project.plan.PlannedProjectFile> files,
        String fileName
    ) {
        return files.stream()
            .filter(file -> file.relativePath().getFileName().toString().equals(fileName))
            .findFirst()
            .map(file -> new String(file.content(), java.nio.charset.StandardCharsets.UTF_8))
            .orElseThrow();
    }

    @Test
    void twoFksToSameTargetProduceSeparatePlans() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder station = persistentClass(modelBuilder, "TestModel.Transport.Station", "station");
        ClassMetadataBuilder journey = persistentClass(modelBuilder, "TestModel.Transport.Journey", "journey");

        journey.attribute(new AttributeMetadataBuilder("departureStation")
            .javaType("Long")
            .foreignKey(true)
            .referencedClass(station.name())
            .sqlName("departure_station_id")
            .columnName("departure_station_id"));
        journey.attribute(new AttributeMetadataBuilder("arrivalStation")
            .javaType("Long")
            .foreignKey(true)
            .referencedClass(station.name())
            .sqlName("arrival_station_id")
            .columnName("arrival_station_id"));

        modelBuilder.relationship(RelationshipMetadata.builder("TestModel.Transport.Journey_Departure")
            .sourceClass(journey.name())
            .targetClass(station.name())
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK)
            .sourceAttribute("departure_station_id")
            .targetRoleName("DepartureStation"));
        modelBuilder.relationship(RelationshipMetadata.builder("TestModel.Transport.Journey_Arrival")
            .sourceClass(journey.name())
            .targetClass(station.name())
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK)
            .sourceAttribute("arrival_station_id")
            .targetRoleName("ArrivalStation"));

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        List<GrailsInverseRelationshipPlan> plans =
            planner(metadata, defaultConfig()).plansForOwner(station.name());

        assertThat(plans).hasSize(2);
        assertThat(plans)
            .extracting(GrailsInverseRelationshipPlan::collectionPropertyName)
            .containsExactlyInAnyOrder("departureStations", "arrivalStations");
        assertThat(plans)
            .extracting(GrailsInverseRelationshipPlan::relatedPropertyName)
            .containsExactlyInAnyOrder("departureStationId", "arrivalStationId");
        assertThat(plans)
            .extracting(GrailsInverseRelationshipPlan::persistentCollectionBacked)
            .containsOnly(false);
    }

    @Test
    void normalInversePlansAreNotPersistentCollectionBacked() {
        Fixture fixture = fixture("departmentReference", "department");

        assertThat(planner(fixture.metadata(), defaultConfig()).plans())
            .singleElement()
            .satisfies(plan -> {
                assertThat(plan.persistentCollectionBacked()).isFalse();
                assertThat(plan.relatedPropertyName()).isEqualTo("department");
            });
    }

    private GrailsInverseRelationshipPlanner planner(ModelMetadata metadata, GenerationConfig config) {
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        return GrailsInverseRelationshipPlanner.forMetadata(metadata, config, registry, mapper);
    }

    private GenerationConfig defaultConfig() {
        return GenerationConfig.builder(tempDir.resolve("default"), "com.example").build();
    }

    private Fixture fixture(String propertyName, String columnName) {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder department = persistentClass(modelBuilder, "TestModel.Organization.Department", "department");
        department.label("en", "Department");
        ClassMetadataBuilder employee = persistentClass(modelBuilder, "TestModel.Organization.Employee", "employee");
        employee.label("en", "Employee");
        AttributeMetadataBuilder departmentReference = new AttributeMetadataBuilder(propertyName)
            .javaType("Long")
            .foreignKey(true)
            .referencedClass(department.name())
            .sqlName(columnName)
            .columnName(columnName);
        employee.attribute(departmentReference);

        RelationshipMetadataBuilder relationship = RelationshipMetadata.builder(
            "TestModel.Organization.Employee_Department"
        )
            .sourceClass(employee.name())
            .targetClass(department.name())
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK)
            .sourceAttribute(propertyName)
            .targetRoleName("Department")
            .physicalName(columnName);
        modelBuilder.relationship(relationship);
        return new Fixture(modelBuilder, department, employee, departmentReference, relationship);
    }

    private ClassMetadataBuilder persistentClass(ModelMetadataBuilder modelBuilder, String name, String tableName) {
        return modelBuilder.classBuilder(name)
            .kind(ClassMetadata.ClassKind.CLASS)
            .tableName(tableName);
    }

    private record Fixture(
        ModelMetadataBuilder modelBuilder,
        ClassMetadataBuilder owner,
        ClassMetadataBuilder related,
        AttributeMetadataBuilder relatedAttribute,
        RelationshipMetadataBuilder relationship
    ) {
        ModelMetadata metadata() {
            return new ModelMetadataFactory().buildValidated(modelBuilder);
        }
    }
}

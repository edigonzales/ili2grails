package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsInverseRelationshipPlannerTest {

    @TempDir
    Path tempDir;

    @Test
    void mapsGeneratedCollectionBackToExactRelatedProperty() throws Exception {
        Fixture fixture = fixture("departmentReference", "department");
        fixture.relatedAttribute().setMandatory(true);
        fixture.relationship().setMandatory(true);

        GrailsInverseRelationshipPlanner planner = planner(
            fixture.metadata(),
            GenerationConfig.builder(tempDir, "com.example")
                .domainPackage("com.example.domain")
                .build()
        );

        assertThat(planner.plansForOwner(fixture.owner().getName()))
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
        composition.relationship().setComposition(true);
        assertThat(planner(composition.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture external = fixture("department", "department");
        external.relationship().setExternal(true);
        assertThat(planner(external.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture ordered = fixture("department", "department");
        ordered.relationship().setOrdered(true);
        assertThat(planner(ordered.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture nonPhysical = fixture("department", null);
        assertThat(planner(nonPhysical.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture incompleteSource = fixture("department", "department");
        incompleteSource.related().setAbstract(true);
        assertThat(planner(incompleteSource.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture incompleteOwner = fixture("department", "department");
        incompleteOwner.owner().setAbstract(true);
        assertThat(planner(incompleteOwner.metadata(), defaultConfig()).plans()).isEmpty();

        Fixture ambiguous = fixture("department", "department");
        AttributeMetadata duplicateReference = new AttributeMetadata("departmentAlias");
        duplicateReference.setJavaType("Long");
        duplicateReference.setForeignKey(true);
        duplicateReference.setReferencedClass(ambiguous.owner().getName());
        duplicateReference.setSqlName("department");
        duplicateReference.setColumnName("department");
        ambiguous.related().addAttribute(duplicateReference);
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
        fixture.relatedAttribute().setMandatory(true);
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example")
            .domainPackage("com.example.domain")
            .build();

        new GrailsDomainGenerator().generate(fixture.metadata(), config);

        String department = Files.readString(
            tempDir.resolve("grails-app/domain/com/example/domain/Department.groovy")
        );
        String employee = Files.readString(
            tempDir.resolve("grails-app/domain/com/example/domain/Employee.groovy")
        );
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

    private GrailsInverseRelationshipPlanner planner(ModelMetadata metadata, GenerationConfig config) {
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        return GrailsInverseRelationshipPlanner.forMetadata(metadata, config, registry, mapper);
    }

    private GenerationConfig defaultConfig() {
        return GenerationConfig.builder(tempDir.resolve("default"), "com.example").build();
    }

    private Fixture fixture(String propertyName, String columnName) {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata department = persistentClass("TestModel.Organization.Department", "department");
        department.addLabel("en", "Department");
        ClassMetadata employee = persistentClass("TestModel.Organization.Employee", "employee");
        employee.addLabel("en", "Employee");
        AttributeMetadata departmentReference = new AttributeMetadata(propertyName);
        departmentReference.setJavaType("Long");
        departmentReference.setForeignKey(true);
        departmentReference.setReferencedClass(department.getName());
        departmentReference.setSqlName(columnName);
        departmentReference.setColumnName(columnName);
        employee.addAttribute(departmentReference);
        metadata.addClass(department);
        metadata.addClass(employee);

        RelationshipMetadata relationship = new RelationshipMetadata(
            "TestModel.Organization.Employee_Department"
        );
        relationship.setSourceClass(employee.getName());
        relationship.setTargetClass(department.getName());
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        relationship.setSourceAttribute(propertyName);
        relationship.setTargetRoleName("Department");
        relationship.setPhysicalName(columnName);
        metadata.addRelationship(relationship);
        return new Fixture(metadata, department, employee, departmentReference, relationship);
    }

    private ClassMetadata persistentClass(String name, String tableName) {
        ClassMetadata classMetadata = new ClassMetadata(name);
        classMetadata.setKind(ClassMetadata.ClassKind.CLASS);
        classMetadata.setTableName(tableName);
        return classMetadata;
    }

    private record Fixture(
        ModelMetadata metadata,
        ClassMetadata owner,
        ClassMetadata related,
        AttributeMetadata relatedAttribute,
        RelationshipMetadata relationship
    ) {
    }
}

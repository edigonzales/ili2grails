package ch.interlis.generator.grails;

import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.Cardinality;
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

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsRelationshipMapperTest {

    @TempDir
    Path tempDir;

    @Test
    void normalReferenceDoesNotCreateInversePersistentCollection() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder person = persistentClass(modelBuilder, "TestModel.Person", "person");
        ClassMetadataBuilder address = persistentClass(modelBuilder, "TestModel.Address", "address");
        address.attribute(foreignKey("person", person.name(), false)
            .columnName("person")
            .sqlName("person"));
        modelBuilder.relationship(relationship(
            "Address_Person",
            address.name(),
            person.name(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE
        ).sourceAttribute("person"));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper mapper = mapper(metadata);
        GrailsRelationshipMapper.DomainMapping addressMapping = mapper.map(metadata.getClass("TestModel.Address"));
        GrailsRelationshipMapper.DomainMapping personMapping = mapper.map(metadata.getClass("TestModel.Person"));

        assertThat(addressMapping.properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::name)
            .contains("person");
        assertThat(addressMapping.properties())
            .filteredOn(property -> property.name().equals("person"))
            .extracting(GrailsRelationshipMapper.DomainProperty::type)
            .containsExactly("Person");
        assertThat(addressMapping.belongsTo()).isEmpty();
        assertThat(personMapping.collections()).isEmpty();
        assertThat(personMapping.diagnostics()).isEmpty();
    }

    @Test
    void twoReferencesToSameTargetDoNotCreateHasMany() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder station = persistentClass(modelBuilder, "TestModel.Station", "station");
        ClassMetadataBuilder journey = persistentClass(modelBuilder, "TestModel.Journey", "journey");
        journey.attribute(foreignKey("departureStation", station.name(), false)
            .columnName("departure_station_id")
            .sqlName("departure_station_id"));
        journey.attribute(foreignKey("arrivalStation", station.name(), false)
            .columnName("arrival_station_id")
            .sqlName("arrival_station_id"));

        modelBuilder.relationship(relationship(
            "Journey_Departure",
            journey.name(),
            station.name(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        )
            .sourceAttribute("departure_station_id")
            .targetRoleName("DepartureStation"));
        modelBuilder.relationship(relationship(
            "Journey_Arrival",
            journey.name(),
            station.name(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        )
            .sourceAttribute("arrival_station_id")
            .targetRoleName("ArrivalStation"));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper mapper = mapper(metadata);
        GrailsRelationshipMapper.DomainMapping stationMapping = mapper.map(metadata.getClass("TestModel.Station"));

        assertThat(stationMapping.collections()).isEmpty();
        assertThat(mapper.map(metadata.getClass("TestModel.Journey")).properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::name)
            .containsExactlyInAnyOrder("departureStationId", "arrivalStationId");
    }

    @Test
    void normalReferenceStillCreatesTypedChildProperty() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder owner = persistentClass(modelBuilder, "TestModel.Owner", "owner");
        ClassMetadataBuilder child = persistentClass(modelBuilder, "TestModel.Child", "child");
        child.attribute(foreignKey("owner", owner.name(), true)
            .columnName("owner_id")
            .sqlName("owner_id"));

        modelBuilder.relationship(relationship(
            "Child_Owner",
            child.name(),
            owner.name(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        ).sourceAttribute("owner_id"));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper.DomainMapping childMapping = mapper(metadata).map(metadata.getClass("TestModel.Child"));

        assertThat(childMapping.properties())
            .filteredOn(property -> property.name().equals("ownerId"))
            .singleElement()
            .satisfies(property -> {
                assertThat(property.type()).isEqualTo("Owner");
                assertThat(property.columnName()).isEqualTo("owner_id");
                assertThat(property.nullable()).isFalse();
            });
    }

    @Test
    void compositionManyCreatesPersistentCollectionAndResolvesMappedBy() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder building = persistentClass(modelBuilder, "TestModel.Building", "building");
        ClassMetadataBuilder component = persistentClass(modelBuilder, "TestModel.Component", "component");
        component.attribute(foreignKey("owner", building.name(), true)
            .columnName("owner_id")
            .sqlName("owner_id"));

        modelBuilder.relationship(relationship(
            "Building_Components",
            building.name(),
            component.name(),
            RelationshipMetadata.RelationType.ONE_TO_MANY,
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
        )
            .sourceAttribute("Components")
            .composition(true)
            .cardinality(Cardinality.of(1, 1, 0, -1)));

        modelBuilder.relationship(relationship(
            "Component_Owner",
            component.name(),
            building.name(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        )
            .sourceAttribute("owner_id")
            .composition(true));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper mapper = mapper(metadata);
        GrailsRelationshipMapper.DomainMapping buildingMapping = mapper.map(metadata.getClass("TestModel.Building"));

        assertThat(buildingMapping.properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::name)
            .doesNotContain("components");
        assertThat(buildingMapping.collections())
            .singleElement()
            .satisfies(collection -> {
                assertThat(collection.name()).isEqualTo("components");
                assertThat(collection.elementType()).isEqualTo("Component");
                assertThat(collection.mappedByProperty()).isEqualTo("ownerId");
                assertThat(collection.kind())
                    .isEqualTo(GrailsRelationshipMapper.CollectionKind.COMPOSITION);
            });
        assertThat(buildingMapping.diagnostics()).isEmpty();
    }

    @Test
    void ambiguousCompositionMappedByProducesDiagnostic() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder building = persistentClass(modelBuilder, "TestModel.Building", "building");
        ClassMetadataBuilder component = persistentClass(modelBuilder, "TestModel.Component", "component");
        component.attribute(foreignKey("primaryOwner", building.name(), false)
            .columnName("primary_owner_id")
            .sqlName("primary_owner_id"));
        component.attribute(foreignKey("secondaryOwner", building.name(), false)
            .columnName("secondary_owner_id")
            .sqlName("secondary_owner_id"));

        modelBuilder.relationship(relationship(
            "Building_Components",
            building.name(),
            component.name(),
            RelationshipMetadata.RelationType.ONE_TO_MANY,
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
        )
            .sourceAttribute("Components")
            .composition(true)
            .cardinality(Cardinality.of(1, 1, 0, -1)));

        modelBuilder.relationship(relationship(
            "Component_PrimaryOwner",
            component.name(),
            building.name(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        ).sourceAttribute("primary_owner_id"));
        modelBuilder.relationship(relationship(
            "Component_SecondaryOwner",
            component.name(),
            building.name(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        ).sourceAttribute("secondary_owner_id"));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper.DomainMapping buildingMapping = mapper(metadata).map(metadata.getClass("TestModel.Building"));

        assertThat(buildingMapping.collections()).isEmpty();
        assertThat(buildingMapping.diagnostics())
            .anySatisfy(diagnostic -> {
                assertThat(diagnostic.code())
                    .isEqualTo(PersistenceDiagnostic.Code.COMPOSITION_MAPPED_BY_AMBIGUOUS);
                assertThat(diagnostic.severity())
                    .isEqualTo(PersistenceDiagnostic.Severity.ERROR);
            });
    }

    @Test
    void compositionWithoutPhysicalChildFkIsNotPersisted() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder building = persistentClass(modelBuilder, "TestModel.Building", "building");
        structure(modelBuilder, "TestModel.Component");

        modelBuilder.relationship(relationship(
            "Building_Components",
            building.name(),
            "TestModel.Component",
            RelationshipMetadata.RelationType.ONE_TO_MANY,
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
        )
            .sourceAttribute("Components")
            .composition(true)
            .cardinality(Cardinality.of(1, 1, 0, -1)));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper mapper = mapper(metadata);
        GrailsRelationshipMapper.DomainMapping buildingMapping = mapper.map(metadata.getClass("TestModel.Building"));

        assertThat(buildingMapping.collections()).isEmpty();
        assertThat(buildingMapping.diagnostics())
            .anySatisfy(diagnostic -> {
                assertThat(diagnostic.code())
                    .isEqualTo(PersistenceDiagnostic.Code.COMPOSITION_COLLECTION_UNRESOLVED);
                assertThat(diagnostic.severity())
                    .isEqualTo(PersistenceDiagnostic.Severity.WARNING);
            });
    }

    @Test
    void associationRoleDoesNotCreateInverseGormCollection() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder person = persistentClass(modelBuilder, "TestModel.Person", "person");
        ClassMetadataBuilder personAddress = persistentClass(modelBuilder, "TestModel.PersonAddress", "person_address")
            .kind(ClassMetadata.ClassKind.ASSOCIATION);

        modelBuilder.relationship(relationship(
            "PersonAddress_Person",
            personAddress.name(),
            person.name(),
            RelationshipMetadata.RelationType.ASSOCIATION,
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
        ).targetRoleName("Person"));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper mapper = mapper(metadata);
        assertThat(mapper.map(metadata.getClass("TestModel.PersonAddress")).properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::name)
            .containsExactly("person");
        assertThat(mapper.map(metadata.getClass("TestModel.Person")).collections()).isEmpty();
    }

    @Test
    void physicalNameCanMatchRelationshipWhenSemanticRoleDiffersFromColumn() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder owner = persistentClass(modelBuilder, "TestModel.Owner", "owner");
        ClassMetadataBuilder parcel = persistentClass(modelBuilder, "TestModel.Parcel", "parcel");
        parcel.attribute(foreignKey("ownerReference", owner.name(), false)
            .columnName("owner_fk")
            .sqlName("owner_fk"));

        modelBuilder.relationship(relationship(
            "Parcel_Owner",
            parcel.name(),
            owner.name(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
        )
            .sourceAttribute("SemanticOwner")
            .targetRoleName("OwnerRole")
            .physicalName("owner_fk")
            .mandatory(true));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);
        RelationshipMetadata relationship = metadata.getAllRelationships().get(0);

        GrailsRelationshipMapper.DomainMapping mapping = mapper(metadata).map(metadata.getClass("TestModel.Parcel"));

        assertThat(mapping.properties())
            .filteredOn(property -> property.name().equals("ownerFk"))
            .singleElement()
            .satisfies(property -> {
                assertThat(property.type()).isEqualTo("Owner");
                assertThat(property.nullable()).isFalse();
                assertThat(property.relationship()).isSameAs(relationship);
            });
    }

    @Test
    void compositionToOneBecomesSimpleProperty() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder building = persistentClass(modelBuilder, "TestModel.Building", "building");
        structure(modelBuilder, "TestModel.AddressStructure");

        modelBuilder.relationship(relationship(
            "Building_Address",
            building.name(),
            "TestModel.AddressStructure",
            RelationshipMetadata.RelationType.ONE_TO_ONE,
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
        )
            .sourceAttribute("Address")
            .composition(true)
            .mandatory(true)
            .cardinality(Cardinality.of(1, 1, 1, 1)));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper.DomainMapping mapping = mapper(metadata).map(metadata.getClass("TestModel.Building"));

        assertThat(mapping.collections()).isEmpty();
        assertThat(mapping.properties())
            .filteredOn(property -> property.name().equals("address"))
            .extracting(GrailsRelationshipMapper.DomainProperty::type)
            .containsExactly("AddressStructure");
        assertThat(mapping.properties())
            .filteredOn(property -> property.name().equals("address"))
            .extracting(GrailsRelationshipMapper.DomainProperty::nullable)
            .containsExactly(false);
    }

    @Test
    void physicalCompositionForeignKeyGetsBelongsTo() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder building = persistentClass(modelBuilder, "TestModel.Building", "building");
        ClassMetadataBuilder component = persistentClass(modelBuilder, "TestModel.Component", "component");
        component.attribute(foreignKey("owner", building.name(), true));

        modelBuilder.relationship(relationship(
            "Component_Building",
            component.name(),
            building.name(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        )
            .sourceAttribute("owner")
            .composition(true));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper.DomainMapping componentMapping = mapper(metadata).map(metadata.getClass("TestModel.Component"));

        assertThat(componentMapping.properties())
            .filteredOn(property -> property.name().equals("owner"))
            .extracting(GrailsRelationshipMapper.DomainProperty::type)
            .containsExactly("Building");
        assertThat(componentMapping.belongsTo())
            .extracting(GrailsRelationshipMapper.DomainOwnership::name)
            .containsExactly("owner");
    }

    @Test
    void associationClassGetsRoleProperties() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder person = persistentClass(modelBuilder, "TestModel.Person", "person");
        ClassMetadataBuilder address = persistentClass(modelBuilder, "TestModel.Address", "address");
        ClassMetadataBuilder personAddress = persistentClass(modelBuilder, "TestModel.PersonAddress", "person_address")
            .kind(ClassMetadata.ClassKind.ASSOCIATION);

        modelBuilder.relationship(relationship(
            "PersonAddress_Person",
            personAddress.name(),
            person.name(),
            RelationshipMetadata.RelationType.ASSOCIATION,
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
        )
            .targetRoleName("Person")
            .mandatory(true));

        modelBuilder.relationship(relationship(
            "PersonAddress_Address",
            personAddress.name(),
            address.name(),
            RelationshipMetadata.RelationType.ASSOCIATION,
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
        ).targetRoleName("Address"));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper.DomainMapping mapping = mapper(metadata).map(metadata.getClass("TestModel.PersonAddress"));

        assertThat(mapping.properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::name)
            .containsExactlyInAnyOrder("person", "address");
        assertThat(mapping.properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::type)
            .containsExactlyInAnyOrder("Person", "Address");
        assertThat(mapper(metadata).map(metadata.getClass("TestModel.Person")).collections()).isEmpty();
    }

    @Test
    void associationMetadataCanDriveAssociationRolePropertiesWithoutRelationships() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder person = persistentClass(modelBuilder, "TestModel.Person", "person");
        ClassMetadataBuilder address = persistentClass(modelBuilder, "TestModel.Address", "address");
        ClassMetadataBuilder personAddress = persistentClass(modelBuilder, "TestModel.PersonAddress", "person_address")
            .kind(ClassMetadata.ClassKind.ASSOCIATION);

        modelBuilder.associationBuilder(personAddress.name())
            .associationClass(personAddress.name())
            .role(AssociationRoleMetadata.builder("Person")
                .targetClass(person.name())
                .mandatory(true))
            .role(AssociationRoleMetadata.builder("Address")
                .targetClass(address.name()));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper.DomainMapping mapping = mapper(metadata).map(metadata.getClass("TestModel.PersonAddress"));

        assertThat(mapping.properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::name)
            .containsExactlyInAnyOrder("person", "address");
        assertThat(mapping.properties())
            .filteredOn(property -> property.name().equals("person"))
            .extracting(GrailsRelationshipMapper.DomainProperty::nullable)
            .containsExactly(false);
        assertThat(mapper(metadata).map(metadata.getClass("TestModel.Person")).collections()).isEmpty();
    }

    @Test
    void unusedNonPersistentStructureIsNotGenerated() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder owner = persistentClass(modelBuilder, "TestModel.Owner", "owner");
        structure(modelBuilder, "TestModel.UnusedStructure");
        structure(modelBuilder, "TestModel.UsedStructure");

        modelBuilder.relationship(relationship(
            "Owner_Used",
            owner.name(),
            "TestModel.UsedStructure",
            RelationshipMetadata.RelationType.ONE_TO_ONE,
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
        ).sourceAttribute("Used"));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GrailsRelationshipMapper mapper = mapper(metadata);

        assertThat(mapper.shouldGenerate(metadata.getClass("TestModel.Owner"))).isTrue();
        assertThat(mapper.shouldGenerate(metadata.getClass("TestModel.UsedStructure"))).isTrue();
        assertThat(mapper.shouldGenerate(metadata.getClass("TestModel.UnusedStructure"))).isFalse();
        assertThat(mapper.generatedClasses())
            .extracting(ClassMetadata::getName)
            .containsExactly(metadata.getClass("TestModel.Owner").getName(),
                metadata.getClass("TestModel.UsedStructure").getName());
    }

    private GrailsRelationshipMapper mapper(ModelMetadata metadata) {
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example").build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        return GrailsRelationshipMapper.forMetadata(metadata, config, registry);
    }

    private ClassMetadataBuilder persistentClass(ModelMetadataBuilder modelBuilder, String name, String tableName) {
        return modelBuilder.classBuilder(name)
            .kind(ClassMetadata.ClassKind.CLASS)
            .tableName(tableName);
    }

    private ClassMetadataBuilder structure(ModelMetadataBuilder modelBuilder, String name) {
        return modelBuilder.classBuilder(name)
            .kind(ClassMetadata.ClassKind.STRUCTURE);
    }

    private AttributeMetadataBuilder foreignKey(String name, String referencedClass, boolean mandatory) {
        return new AttributeMetadataBuilder(name)
            .foreignKey(true)
            .referencedClass(referencedClass)
            .javaType("Long")
            .mandatory(mandatory);
    }

    private RelationshipMetadataBuilder relationship(String name,
                                                     String sourceClass,
                                                     String targetClass,
                                                     RelationshipMetadata.RelationType type,
                                                     RelationshipMetadata.SemanticKind semanticKind) {
        return RelationshipMetadata.builder(name)
            .sourceClass(sourceClass)
            .targetClass(targetClass)
            .type(type)
            .semanticKind(semanticKind);
    }
}

package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsRelationshipMapperTest {

    @TempDir
    Path tempDir;

    @Test
    void normalReferenceDoesNotCreateInversePersistentCollection() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata person = persistentClass("TestModel.Person", "person");
        ClassMetadata address = persistentClass("TestModel.Address", "address");
        AttributeMetadata personRef = foreignKey("person", person.getName(), false);
        personRef.setColumnName("person");
        personRef.setSqlName("person");
        address.addAttribute(personRef);
        metadata.addClass(person);
        metadata.addClass(address);

        RelationshipMetadata relationship = relationship(
            "Address_Person",
            address.getName(),
            person.getName(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE
        );
        relationship.setSourceAttribute("person");
        metadata.addRelationship(relationship);

        GrailsRelationshipMapper mapper = mapper(metadata);
        GrailsRelationshipMapper.DomainMapping addressMapping = mapper.map(address);
        GrailsRelationshipMapper.DomainMapping personMapping = mapper.map(person);

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
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata station = persistentClass("TestModel.Station", "station");
        ClassMetadata journey = persistentClass("TestModel.Journey", "journey");
        AttributeMetadata departure = foreignKey("departureStation", station.getName(), false);
        departure.setColumnName("departure_station_id");
        departure.setSqlName("departure_station_id");
        journey.addAttribute(departure);
        AttributeMetadata arrival = foreignKey("arrivalStation", station.getName(), false);
        arrival.setColumnName("arrival_station_id");
        arrival.setSqlName("arrival_station_id");
        journey.addAttribute(arrival);
        metadata.addClass(station);
        metadata.addClass(journey);

        RelationshipMetadata departureRelationship = relationship(
            "Journey_Departure",
            journey.getName(),
            station.getName(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        );
        departureRelationship.setSourceAttribute("departure_station_id");
        departureRelationship.setTargetRoleName("DepartureStation");
        metadata.addRelationship(departureRelationship);

        RelationshipMetadata arrivalRelationship = relationship(
            "Journey_Arrival",
            journey.getName(),
            station.getName(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        );
        arrivalRelationship.setSourceAttribute("arrival_station_id");
        arrivalRelationship.setTargetRoleName("ArrivalStation");
        metadata.addRelationship(arrivalRelationship);

        GrailsRelationshipMapper mapper = mapper(metadata);
        GrailsRelationshipMapper.DomainMapping stationMapping = mapper.map(station);

        assertThat(stationMapping.collections()).isEmpty();
        assertThat(mapper.map(journey).properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::name)
            .containsExactlyInAnyOrder("departureStationId", "arrivalStationId");
    }

    @Test
    void normalReferenceStillCreatesTypedChildProperty() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata owner = persistentClass("TestModel.Owner", "owner");
        ClassMetadata child = persistentClass("TestModel.Child", "child");
        AttributeMetadata ownerRef = foreignKey("owner", owner.getName(), true);
        ownerRef.setColumnName("owner_id");
        ownerRef.setSqlName("owner_id");
        child.addAttribute(ownerRef);
        metadata.addClass(owner);
        metadata.addClass(child);

        RelationshipMetadata relationship = relationship(
            "Child_Owner",
            child.getName(),
            owner.getName(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        );
        relationship.setSourceAttribute("owner_id");
        metadata.addRelationship(relationship);

        GrailsRelationshipMapper.DomainMapping childMapping = mapper(metadata).map(child);

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
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata building = persistentClass("TestModel.Building", "building");
        ClassMetadata component = persistentClass("TestModel.Component", "component");
        AttributeMetadata owner = foreignKey("owner", building.getName(), true);
        owner.setColumnName("owner_id");
        owner.setSqlName("owner_id");
        component.addAttribute(owner);
        metadata.addClass(building);
        metadata.addClass(component);

        RelationshipMetadata composition = relationship(
            "Building_Components",
            building.getName(),
            component.getName(),
            RelationshipMetadata.RelationType.ONE_TO_MANY,
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
        );
        composition.setSourceAttribute("Components");
        composition.setComposition(true);
        composition.setCardinality(new RelationshipMetadata.Cardinality(1, 1, 0, -1));
        metadata.addRelationship(composition);

        RelationshipMetadata ownerFk = relationship(
            "Component_Owner",
            component.getName(),
            building.getName(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        );
        ownerFk.setSourceAttribute("owner_id");
        ownerFk.setComposition(true);
        metadata.addRelationship(ownerFk);

        GrailsRelationshipMapper mapper = mapper(metadata);
        GrailsRelationshipMapper.DomainMapping buildingMapping = mapper.map(building);

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
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata building = persistentClass("TestModel.Building", "building");
        ClassMetadata component = persistentClass("TestModel.Component", "component");
        AttributeMetadata firstOwner = foreignKey("primaryOwner", building.getName(), false);
        firstOwner.setColumnName("primary_owner_id");
        firstOwner.setSqlName("primary_owner_id");
        component.addAttribute(firstOwner);
        AttributeMetadata secondOwner = foreignKey("secondaryOwner", building.getName(), false);
        secondOwner.setColumnName("secondary_owner_id");
        secondOwner.setSqlName("secondary_owner_id");
        component.addAttribute(secondOwner);
        metadata.addClass(building);
        metadata.addClass(component);

        RelationshipMetadata composition = relationship(
            "Building_Components",
            building.getName(),
            component.getName(),
            RelationshipMetadata.RelationType.ONE_TO_MANY,
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
        );
        composition.setSourceAttribute("Components");
        composition.setComposition(true);
        composition.setCardinality(new RelationshipMetadata.Cardinality(1, 1, 0, -1));
        metadata.addRelationship(composition);

        RelationshipMetadata firstFk = relationship(
            "Component_PrimaryOwner",
            component.getName(),
            building.getName(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        );
        firstFk.setSourceAttribute("primary_owner_id");
        metadata.addRelationship(firstFk);
        RelationshipMetadata secondFk = relationship(
            "Component_SecondaryOwner",
            component.getName(),
            building.getName(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        );
        secondFk.setSourceAttribute("secondary_owner_id");
        metadata.addRelationship(secondFk);

        GrailsRelationshipMapper.DomainMapping buildingMapping = mapper(metadata).map(building);

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
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata building = persistentClass("TestModel.Building", "building");
        ClassMetadata component = structure("TestModel.Component");
        metadata.addClass(building);
        metadata.addClass(component);

        RelationshipMetadata relationship = relationship(
            "Building_Components",
            building.getName(),
            component.getName(),
            RelationshipMetadata.RelationType.ONE_TO_MANY,
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
        );
        relationship.setSourceAttribute("Components");
        relationship.setComposition(true);
        relationship.setCardinality(new RelationshipMetadata.Cardinality(1, 1, 0, -1));
        metadata.addRelationship(relationship);

        GrailsRelationshipMapper mapper = mapper(metadata);
        GrailsRelationshipMapper.DomainMapping buildingMapping = mapper.map(building);

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
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata person = persistentClass("TestModel.Person", "person");
        ClassMetadata personAddress = persistentClass("TestModel.PersonAddress", "person_address");
        personAddress.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        metadata.addClass(person);
        metadata.addClass(personAddress);

        RelationshipMetadata personRole = relationship(
            "PersonAddress_Person",
            personAddress.getName(),
            person.getName(),
            RelationshipMetadata.RelationType.ASSOCIATION,
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
        );
        personRole.setTargetRoleName("Person");
        metadata.addRelationship(personRole);

        GrailsRelationshipMapper mapper = mapper(metadata);
        assertThat(mapper.map(personAddress).properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::name)
            .containsExactly("person");
        assertThat(mapper.map(person).collections()).isEmpty();
    }

    @Test
    void physicalNameCanMatchRelationshipWhenSemanticRoleDiffersFromColumn() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata owner = persistentClass("TestModel.Owner", "owner");
        ClassMetadata parcel = persistentClass("TestModel.Parcel", "parcel");
        AttributeMetadata ownerReference = foreignKey("ownerReference", owner.getName(), false);
        ownerReference.setColumnName("owner_fk");
        ownerReference.setSqlName("owner_fk");
        parcel.addAttribute(ownerReference);
        metadata.addClass(owner);
        metadata.addClass(parcel);

        RelationshipMetadata relationship = relationship(
            "Parcel_Owner",
            parcel.getName(),
            owner.getName(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
        );
        relationship.setSourceAttribute("SemanticOwner");
        relationship.setTargetRoleName("OwnerRole");
        relationship.setPhysicalName("owner_fk");
        relationship.setMandatory(true);
        metadata.addRelationship(relationship);

        GrailsRelationshipMapper.DomainMapping mapping = mapper(metadata).map(parcel);

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
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata building = persistentClass("TestModel.Building", "building");
        ClassMetadata address = structure("TestModel.AddressStructure");
        metadata.addClass(building);
        metadata.addClass(address);

        RelationshipMetadata relationship = relationship(
            "Building_Address",
            building.getName(),
            address.getName(),
            RelationshipMetadata.RelationType.ONE_TO_ONE,
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
        );
        relationship.setSourceAttribute("Address");
        relationship.setComposition(true);
        relationship.setMandatory(true);
        relationship.setCardinality(new RelationshipMetadata.Cardinality(1, 1, 1, 1));
        metadata.addRelationship(relationship);

        GrailsRelationshipMapper.DomainMapping mapping = mapper(metadata).map(building);

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
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata building = persistentClass("TestModel.Building", "building");
        ClassMetadata component = persistentClass("TestModel.Component", "component");
        AttributeMetadata owner = foreignKey("owner", building.getName(), true);
        component.addAttribute(owner);
        metadata.addClass(building);
        metadata.addClass(component);

        RelationshipMetadata relationship = relationship(
            "Component_Building",
            component.getName(),
            building.getName(),
            RelationshipMetadata.RelationType.MANY_TO_ONE,
            RelationshipMetadata.SemanticKind.ILI2DB_FK
        );
        relationship.setSourceAttribute("owner");
        relationship.setComposition(true);
        metadata.addRelationship(relationship);

        GrailsRelationshipMapper.DomainMapping componentMapping = mapper(metadata).map(component);

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
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata person = persistentClass("TestModel.Person", "person");
        ClassMetadata address = persistentClass("TestModel.Address", "address");
        ClassMetadata personAddress = persistentClass("TestModel.PersonAddress", "person_address");
        personAddress.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        metadata.addClass(person);
        metadata.addClass(address);
        metadata.addClass(personAddress);

        RelationshipMetadata personRole = relationship(
            "PersonAddress_Person",
            personAddress.getName(),
            person.getName(),
            RelationshipMetadata.RelationType.ASSOCIATION,
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
        );
        personRole.setTargetRoleName("Person");
        personRole.setMandatory(true);
        metadata.addRelationship(personRole);

        RelationshipMetadata addressRole = relationship(
            "PersonAddress_Address",
            personAddress.getName(),
            address.getName(),
            RelationshipMetadata.RelationType.ASSOCIATION,
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
        );
        addressRole.setTargetRoleName("Address");
        metadata.addRelationship(addressRole);

        GrailsRelationshipMapper.DomainMapping mapping = mapper(metadata).map(personAddress);

        assertThat(mapping.properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::name)
            .containsExactlyInAnyOrder("person", "address");
        assertThat(mapping.properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::type)
            .containsExactlyInAnyOrder("Person", "Address");
        assertThat(mapper(metadata).map(person).collections()).isEmpty();
    }

    @Test
    void associationMetadataCanDriveAssociationRolePropertiesWithoutRelationships() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata person = persistentClass("TestModel.Person", "person");
        ClassMetadata address = persistentClass("TestModel.Address", "address");
        ClassMetadata personAddress = persistentClass("TestModel.PersonAddress", "person_address");
        personAddress.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        metadata.addClass(person);
        metadata.addClass(address);
        metadata.addClass(personAddress);

        AssociationMetadata association = new AssociationMetadata(personAddress.getName());
        association.setAssociationClass(personAddress.getName());
        AssociationRoleMetadata personRole = new AssociationRoleMetadata("Person");
        personRole.setTargetClass(person.getName());
        personRole.setMandatory(true);
        association.addRole(personRole);
        AssociationRoleMetadata addressRole = new AssociationRoleMetadata("Address");
        addressRole.setTargetClass(address.getName());
        association.addRole(addressRole);
        metadata.addAssociation(association);

        GrailsRelationshipMapper.DomainMapping mapping = mapper(metadata).map(personAddress);

        assertThat(mapping.properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::name)
            .containsExactlyInAnyOrder("person", "address");
        assertThat(mapping.properties())
            .filteredOn(property -> property.name().equals("person"))
            .extracting(GrailsRelationshipMapper.DomainProperty::nullable)
            .containsExactly(false);
        assertThat(mapper(metadata).map(person).collections()).isEmpty();
    }

    @Test
    void unusedNonPersistentStructureIsNotGenerated() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata owner = persistentClass("TestModel.Owner", "owner");
        ClassMetadata unused = structure("TestModel.UnusedStructure");
        ClassMetadata used = structure("TestModel.UsedStructure");
        metadata.addClass(owner);
        metadata.addClass(unused);
        metadata.addClass(used);

        RelationshipMetadata composition = relationship(
            "Owner_Used",
            owner.getName(),
            used.getName(),
            RelationshipMetadata.RelationType.ONE_TO_ONE,
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE
        );
        composition.setSourceAttribute("Used");
        metadata.addRelationship(composition);

        GrailsRelationshipMapper mapper = mapper(metadata);

        assertThat(mapper.shouldGenerate(owner)).isTrue();
        assertThat(mapper.shouldGenerate(used)).isTrue();
        assertThat(mapper.shouldGenerate(unused)).isFalse();
        assertThat(mapper.generatedClasses())
            .extracting(ClassMetadata::getName)
            .containsExactly(owner.getName(), used.getName());
    }

    private GrailsRelationshipMapper mapper(ModelMetadata metadata) {
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example").build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        return GrailsRelationshipMapper.forMetadata(metadata, config, registry);
    }

    private ClassMetadata persistentClass(String name, String tableName) {
        ClassMetadata classMetadata = new ClassMetadata(name);
        classMetadata.setKind(ClassMetadata.ClassKind.CLASS);
        classMetadata.setTableName(tableName);
        return classMetadata;
    }

    private ClassMetadata structure(String name) {
        ClassMetadata classMetadata = new ClassMetadata(name);
        classMetadata.setKind(ClassMetadata.ClassKind.STRUCTURE);
        return classMetadata;
    }

    private AttributeMetadata foreignKey(String name, String referencedClass, boolean mandatory) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setForeignKey(true);
        attribute.setReferencedClass(referencedClass);
        attribute.setJavaType("Long");
        attribute.setMandatory(mandatory);
        return attribute;
    }

    private RelationshipMetadata relationship(String name,
                                              String sourceClass,
                                              String targetClass,
                                              RelationshipMetadata.RelationType type,
                                              RelationshipMetadata.SemanticKind semanticKind) {
        RelationshipMetadata relationship = new RelationshipMetadata(name);
        relationship.setSourceClass(sourceClass);
        relationship.setTargetClass(targetClass);
        relationship.setType(type);
        relationship.setSemanticKind(semanticKind);
        return relationship;
    }
}

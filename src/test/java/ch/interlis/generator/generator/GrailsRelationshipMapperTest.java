package ch.interlis.generator.generator;

import ch.interlis.generator.model.AttributeMetadata;
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
    void normalReferenceBecomesTypedPropertyWithoutBelongsTo() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata person = persistentClass("TestModel.Person", "person");
        ClassMetadata address = persistentClass("TestModel.Address", "address");
        AttributeMetadata personRef = foreignKey("person", person.getName(), false);
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
        assertThat(personMapping.collections())
            .extracting(GrailsRelationshipMapper.DomainCollection::name)
            .contains("addresses");
    }

    @Test
    void compositionManyUsesAttributeNameAsCollection() {
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

        assertThat(mapper.shouldGenerate(component)).isTrue();
        assertThat(buildingMapping.properties())
            .extracting(GrailsRelationshipMapper.DomainProperty::name)
            .doesNotContain("components");
        assertThat(buildingMapping.collections())
            .extracting(GrailsRelationshipMapper.DomainCollection::name)
            .containsExactly("components");
        assertThat(buildingMapping.collections())
            .extracting(GrailsRelationshipMapper.DomainCollection::type)
            .containsExactly("Component");
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

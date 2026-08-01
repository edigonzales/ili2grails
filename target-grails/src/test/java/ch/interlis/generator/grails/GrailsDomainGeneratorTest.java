package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsDomainGeneratorTest {

    @Test
    void rendersForeignKeyColumnWithoutBelongsToForNormalReference(@TempDir Path tempDir) throws Exception {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata address = new ClassMetadata("TestModel.Address");
        address.setTableName("address");
        address.addAttribute(primaryKeyAttribute());
        AttributeMetadata addressName = new AttributeMetadata("name");
        addressName.setCoreType(CoreType.TEXT);
        addressName.setJavaType("String");
        address.addAttribute(addressName);
        metadata.addClass(address);

        ClassMetadata person = new ClassMetadata("TestModel.Person");
        person.setTableName("person");
        person.addAttribute(primaryKeyAttribute());
        AttributeMetadata addressAttribute = new AttributeMetadata("address");
        addressAttribute.setSqlName("address");
        addressAttribute.setColumnName("address");
        addressAttribute.setForeignKey(true);
        addressAttribute.setReferencedClass(address.getName());
        person.addAttribute(addressAttribute);
        metadata.addClass(person);

        RelationshipMetadata relationship = new RelationshipMetadata("person_address");
        relationship.setSourceClass(person.getName());
        relationship.setTargetClass(address.getName());
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE);
        relationship.setSourceAttribute("address");
        relationship.setTargetRoleName("address");
        person.addRelationship(relationship);

        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example").build();
        new GrailsDomainGenerator().generate(metadata, config);

        Path personDomain = tempDir.resolve("grails-app/domain/com/example/Person.groovy");
        String content = Files.readString(personDomain);

        assertThat(content).contains("Address address");
        assertThat(content).doesNotContain("static belongsTo");
        assertThat(content).contains("address column: 'address'");
        assertThat(content).contains("static final Map<String, Map<String, Object>> interlisRelationshipMeta");
        assertThat(content).contains("address: [targetClass: 'Address', semanticKind: 'REFERENCE_ATTRIBUTE'");
        assertThat(content).doesNotContain("address_id");

        Path addressDomain = tempDir.resolve("grails-app/domain/com/example/Address.groovy");
        String addressContent = Files.readString(addressDomain);
        assertThat(addressContent).contains("static final Map<String, Object> interlisDisplayMeta");
        assertThat(addressContent).contains("displayFields: ['name']");
        assertThat(addressContent).contains("searchFields: ['name']");
    }

    @Test
    void rendersNumericConstraintsFromCoreContract(@TempDir Path tempDir) throws Exception {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata invoice = new ClassMetadata("TestModel.Invoice");
        invoice.setTableName("invoice");
        invoice.addAttribute(primaryKeyAttribute());
        AttributeMetadata amount = new AttributeMetadata("amount");
        amount.setCoreType(CoreType.NUMERIC);
        amount.setJavaType("java.math.BigDecimal");
        amount.setDocumentation("Invoice amount");
        amount.setUnit("CHF");
        amount.setMinValue("0.0");
        amount.setMaxValue("9999.999");
        amount.setPrecision(7);
        amount.setScale(3);
        invoice.addAttribute(amount);
        metadata.addClass(invoice);

        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example").build();
        new GrailsDomainGenerator().generate(metadata, config);

        Path invoiceDomain = tempDir.resolve("grails-app/domain/com/example/Invoice.groovy");
        String content = Files.readString(invoiceDomain);

        assertThat(content).contains("BigDecimal amount");
        assertThat(content).contains("amount nullable: true, min: 0.0, max: 9999.999, scale: 3");
        assertThat(content).contains("static final Map<String, Map<String, Object>> interlisFieldMeta");
        assertThat(content).contains("amount: [label: 'amount', documentation: 'Invoice amount', unit: 'CHF'");
    }

    @Test
    void twoFksToSameTargetProduceNoHasManyAndTwoInverseMetaEntries(@TempDir Path tempDir)
        throws Exception {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata station = new ClassMetadata("TestModel.Station");
        station.setTableName("station");
        station.addAttribute(primaryKeyAttribute());
        metadata.addClass(station);

        ClassMetadata journey = new ClassMetadata("TestModel.Journey");
        journey.setTableName("journey");
        journey.addAttribute(primaryKeyAttribute());
        journey.addAttribute(foreignKeyAttribute(
            "departureStation", "departure_station_id", station.getName()));
        journey.addAttribute(foreignKeyAttribute(
            "arrivalStation", "arrival_station_id", station.getName()));
        metadata.addClass(journey);

        metadata.addRelationship(manYToOne("journey_departure", journey, station,
            "departure_station_id", "DepartureStation"));
        metadata.addRelationship(manYToOne("journey_arrival", journey, station,
            "arrival_station_id", "ArrivalStation"));

        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example").build();
        new GrailsDomainGenerator().generate(metadata, config);

        Path stationDomain = tempDir.resolve("grails-app/domain/com/example/Station.groovy");
        String content = Files.readString(stationDomain);

        assertThat(content).doesNotContain("static hasMany");
        assertThat(content)
            .contains("static final Map<String, Map<String, Object>> interlisInverseRelationshipMeta");
        assertThat(content).contains("departureStations: [");
        assertThat(content).contains("arrivalStations: [");
        assertThat(content).contains("relatedProperty: 'departureStationId'");
        assertThat(content).contains("relatedProperty: 'arrivalStationId'");
        assertThat(content).doesNotContain("static mappedBy");
    }

    @Test
    void compositionWithUniqueMappedByRendersHasManyAndMappedBy(@TempDir Path tempDir)
        throws Exception {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata owner = new ClassMetadata("TestModel.Owner");
        owner.setTableName("owner");
        owner.addAttribute(primaryKeyAttribute());
        metadata.addClass(owner);

        ClassMetadata part = new ClassMetadata("TestModel.Part");
        part.setTableName("part");
        part.addAttribute(primaryKeyAttribute());
        part.addAttribute(foreignKeyAttribute("owner", "owner_id", owner.getName()));
        metadata.addClass(part);

        RelationshipMetadata composition = new RelationshipMetadata("owner_parts");
        composition.setSourceClass(owner.getName());
        composition.setTargetClass(part.getName());
        composition.setType(RelationshipMetadata.RelationType.ONE_TO_MANY);
        composition.setSemanticKind(RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE);
        composition.setSourceAttribute("parts");
        composition.setComposition(true);
        composition.setCardinality(new RelationshipMetadata.Cardinality(1, 1, 0, -1));
        metadata.addRelationship(composition);

        RelationshipMetadata ownerFk = new RelationshipMetadata("part_owner");
        ownerFk.setSourceClass(part.getName());
        ownerFk.setTargetClass(owner.getName());
        ownerFk.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        ownerFk.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        ownerFk.setSourceAttribute("owner_id");
        ownerFk.setComposition(true);
        metadata.addRelationship(ownerFk);

        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example").build();
        new GrailsDomainGenerator().generate(metadata, config);

        Path ownerDomain = tempDir.resolve("grails-app/domain/com/example/Owner.groovy");
        String content = Files.readString(ownerDomain);

        assertThat(content).contains("static hasMany = [parts: Part]");
        assertThat(content).contains("static mappedBy = [parts: 'ownerId']");

        Path partDomain = tempDir.resolve("grails-app/domain/com/example/Part.groovy");
        String partContent = Files.readString(partDomain);
        assertThat(partContent).contains("Owner ownerId");
        assertThat(partContent).contains("static belongsTo = [ownerId: Owner]");
    }

    private RelationshipMetadata manYToOne(String name,
                                           ClassMetadata source,
                                           ClassMetadata target,
                                           String sourceAttribute,
                                           String targetRoleName) {
        RelationshipMetadata relationship = new RelationshipMetadata(name);
        relationship.setSourceClass(source.getName());
        relationship.setTargetClass(target.getName());
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        relationship.setSourceAttribute(sourceAttribute);
        relationship.setTargetRoleName(targetRoleName);
        relationship.setPhysicalName(sourceAttribute);
        return relationship;
    }

    private AttributeMetadata foreignKeyAttribute(String name, String columnName, String referencedClass) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setSqlName(columnName);
        attribute.setColumnName(columnName);
        attribute.setForeignKey(true);
        attribute.setReferencedClass(referencedClass);
        return attribute;
    }

    @Test
    void rendersGeometryMetaWithTypedKindAndValidationFlags(@TempDir Path tempDir) throws Exception {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata parcel = new ClassMetadata("TestModel.Parcel");
        parcel.setTableName("parcel");
        parcel.addAttribute(primaryKeyAttribute());
        AttributeMetadata footprint = new AttributeMetadata("footprint");
        footprint.setGeometry(true);
        footprint.setGeometryKind("MULTIPOLYGON");
        footprint.setGeometrySrid(2056);
        footprint.setGeometryHasZ(false);
        footprint.setGeometryHasM(false);
        footprint.setAllowEmptyGeometry(false);
        footprint.setJavaType("org.locationtech.jts.geom.Geometry");
        parcel.addAttribute(footprint);
        metadata.addClass(parcel);

        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example").build();
        new GrailsDomainGenerator().generate(metadata, config);

        Path parcelDomain = tempDir.resolve("grails-app/domain/com/example/Parcel.groovy");
        String content = Files.readString(parcelDomain);

        assertThat(content).contains("static final Map<String, Map<String, Object>> geometryMeta");
        assertThat(content).contains("footprint: [srid: 2056, kind: 'MULTIPOLYGON', hasZ: false, hasM: false, allowEmpty: false]");
    }

    private AttributeMetadata primaryKeyAttribute() {
        AttributeMetadata attribute = new AttributeMetadata("t_id");
        attribute.setSqlName("t_id");
        attribute.setColumnName("t_id");
        attribute.setPrimaryKey(true);
        return attribute;
    }
}

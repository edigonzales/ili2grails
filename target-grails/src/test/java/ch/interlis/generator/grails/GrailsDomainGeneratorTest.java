package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
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

import static org.assertj.core.api.Assertions.assertThat;

class GrailsDomainGeneratorTest {

    @Test
    void rendersForeignKeyColumnWithoutBelongsToForNormalReference(@TempDir Path tempDir) throws Exception {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder address = modelBuilder.classBuilder("TestModel.Address")            .tableName("address");        address.attribute(primaryKeyAttribute());
        AttributeMetadataBuilder addressName = new AttributeMetadataBuilder("name");        addressName.coreType(CoreType.TEXT);        addressName.javaType("String");        address.attribute(addressName);

        ClassMetadataBuilder person = modelBuilder.classBuilder("TestModel.Person")            .tableName("person");        person.attribute(primaryKeyAttribute());
        AttributeMetadataBuilder addressAttribute = new AttributeMetadataBuilder("address");        addressAttribute.sqlName("address");        addressAttribute.columnName("address");        addressAttribute.foreignKey(true);        addressAttribute.referencedClass(address.name());        person.attribute(addressAttribute);

        modelBuilder.relationship(RelationshipMetadata.builder("person_address")
            .sourceClass(person.name())
            .targetClass(address.name())
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE)
            .sourceAttribute("address")
            .targetRoleName("address"));

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

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
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder invoice = modelBuilder.classBuilder("TestModel.Invoice")            .tableName("invoice");        invoice.attribute(primaryKeyAttribute());
        AttributeMetadataBuilder amount = new AttributeMetadataBuilder("amount");        amount.coreType(CoreType.NUMERIC);        amount.javaType("java.math.BigDecimal");        amount.documentation("Invoice amount");        amount.unit("CHF");        amount.minValue("0.0");        amount.maxValue("9999.999");        amount.precision(7);        amount.scale(3);        invoice.attribute(amount);

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

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
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder station = modelBuilder.classBuilder("TestModel.Station")            .tableName("station");        station.attribute(primaryKeyAttribute());

        ClassMetadataBuilder journey = modelBuilder.classBuilder("TestModel.Journey")            .tableName("journey");        journey.attribute(primaryKeyAttribute());
        journey.attribute(foreignKeyAttribute(
            "departureStation", "departure_station_id", station.name()));
        journey.attribute(foreignKeyAttribute(
            "arrivalStation", "arrival_station_id", station.name()));

        modelBuilder.relationship(RelationshipMetadataBuilder.from(manYToOne("journey_departure", journey, station,
            "departure_station_id", "DepartureStation")));
        modelBuilder.relationship(RelationshipMetadataBuilder.from(manYToOne("journey_arrival", journey, station,
            "arrival_station_id", "ArrivalStation")));

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

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
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder owner = modelBuilder.classBuilder("TestModel.Owner")            .tableName("owner");        owner.attribute(primaryKeyAttribute());

        ClassMetadataBuilder part = modelBuilder.classBuilder("TestModel.Part")            .tableName("part");        part.attribute(primaryKeyAttribute());
        part.attribute(foreignKeyAttribute("owner", "owner_id", owner.name()));

        RelationshipMetadataBuilder composition = RelationshipMetadataBuilder.from(RelationshipMetadata.builder("owner_parts")        .sourceClass(owner.name())        .targetClass(part.name())        .type(RelationshipMetadata.RelationType.ONE_TO_MANY)        .semanticKind(RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE)        .sourceAttribute("parts")        .composition(true)        .cardinality(ch.interlis.generator.model.Cardinality.of(1, 1, 0, -1))        .buildUnchecked());        modelBuilder.relationship(composition);

        RelationshipMetadataBuilder ownerFk = RelationshipMetadataBuilder.from(RelationshipMetadata.builder("part_owner")        .sourceClass(part.name())        .targetClass(owner.name())        .type(RelationshipMetadata.RelationType.MANY_TO_ONE)        .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK)        .sourceAttribute("owner_id")        .composition(true)        .buildUnchecked());        modelBuilder.relationship(ownerFk);

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

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
                                           ClassMetadataBuilder source,
                                           ClassMetadataBuilder target,
                                           String sourceAttribute,
                                           String targetRoleName) {
        return RelationshipMetadata.builder(name)
            .sourceClass(source.name())
            .targetClass(target.name())
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK)
            .sourceAttribute(sourceAttribute)
            .targetRoleName(targetRoleName)
            .physicalName(sourceAttribute)
            .buildUnchecked();
    }

    private AttributeMetadataBuilder foreignKeyAttribute(String name, String columnName, String referencedClass) {
        return new AttributeMetadataBuilder(name)
            .sqlName(columnName)
            .columnName(columnName)
            .foreignKey(true)
            .referencedClass(referencedClass);
    }

    @Test
    void rendersGeometryMetaWithTypedKindAndValidationFlags(@TempDir Path tempDir) throws Exception {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder parcel = modelBuilder.classBuilder("TestModel.Parcel")            .tableName("parcel");        parcel.attribute(primaryKeyAttribute());
        AttributeMetadataBuilder footprint = new AttributeMetadataBuilder("footprint");        footprint.geometry(true);        footprint.geometryKind("MULTIPOLYGON");        footprint.geometrySrid(2056);        footprint.geometryHasZ(false);        footprint.geometryHasM(false);        footprint.allowEmptyGeometry(false);        footprint.javaType("org.locationtech.jts.geom.Geometry");        parcel.attribute(footprint);

        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(modelBuilder);

        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example").build();
        new GrailsDomainGenerator().generate(metadata, config);

        Path parcelDomain = tempDir.resolve("grails-app/domain/com/example/Parcel.groovy");
        String content = Files.readString(parcelDomain);

        assertThat(content).contains("static final Map<String, Map<String, Object>> geometryMeta");
        assertThat(content).contains("footprint: [srid: 2056, kind: 'MULTIPOLYGON', hasZ: false, hasM: false, allowEmpty: false]");
    }

    private AttributeMetadataBuilder primaryKeyAttribute() {
        return new AttributeMetadataBuilder("t_id")
            .sqlName("t_id")
            .columnName("t_id")
            .primaryKey(true);
    }
}

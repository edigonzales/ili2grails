package ch.interlis.generator.model;

import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeMetadataCoreTypeTest {

    @Test
    void infersCoreTypesFromIliTypes() {
        assertThat(coreTypeOf(attributeWithIliType("TEXT"))).isEqualTo(CoreType.TEXT);
        assertThat(coreTypeOf(attributeWithIliType("MTEXT"))).isEqualTo(CoreType.MTEXT);
        assertThat(coreTypeOf(attributeWithIliType("NumericType"))).isEqualTo(CoreType.NUMERIC);
        assertThat(coreTypeOf(attributeWithIliType("BOOLEAN"))).isEqualTo(CoreType.BOOLEAN);
        assertThat(coreTypeOf(attributeWithIliType("INTERLIS.XMLDATE"))).isEqualTo(CoreType.DATE);
        assertThat(coreTypeOf(attributeWithIliType("INTERLIS.XMLDATETIME"))).isEqualTo(CoreType.DATETIME);
        assertThat(coreTypeOf(attributeWithIliType("INTERLIS.XMLTIME"))).isEqualTo(CoreType.TIME);
        assertThat(coreTypeOf(attributeWithIliType("EnumerationType"))).isEqualTo(CoreType.ENUM);
        assertThat(coreTypeOf(attributeWithIliType("CoordType"))).isEqualTo(CoreType.COORD);
        assertThat(coreTypeOf(attributeWithIliType("PolylineType"))).isEqualTo(CoreType.POLYLINE);
        assertThat(coreTypeOf(attributeWithIliType("SurfaceType"))).isEqualTo(CoreType.SURFACE);
        assertThat(coreTypeOf(attributeWithIliType("ReferenceType"))).isEqualTo(CoreType.REFERENCE);
        assertThat(coreTypeOf(attributeWithIliType("CompositionType"))).isEqualTo(CoreType.COMPOSITION);
        assertThat(coreTypeOf(attributeWithIliType("ObjectType"))).isEqualTo(CoreType.OBJECT);
    }

    @Test
    void infersCoreTypesFromSemanticFlagsBeforeDbType() {
        AttributeMetadataBuilder enumAttribute = new AttributeMetadataBuilder("status");
        enumAttribute.enumType("Model.Status");
        enumAttribute.dbType("VARCHAR");

        AttributeMetadataBuilder referenceAttribute = new AttributeMetadataBuilder("owner");
        referenceAttribute.referencedClass("Model.Owner");
        referenceAttribute.dbType("INTEGER");

        assertThat(coreTypeOf(enumAttribute)).isEqualTo(CoreType.ENUM);
        assertThat(coreTypeOf(referenceAttribute)).isEqualTo(CoreType.REFERENCE);
    }

    @Test
    void infersGeometryKindsButKeepsGenericGeometryUnknown() {
        assertThat(coreTypeOf(geometryAttribute("POINT"))).isEqualTo(CoreType.COORD);
        assertThat(coreTypeOf(geometryAttribute("MULTIPOINT"))).isEqualTo(CoreType.COORD);
        assertThat(coreTypeOf(geometryAttribute("LINESTRING"))).isEqualTo(CoreType.POLYLINE);
        assertThat(coreTypeOf(geometryAttribute("MULTILINESTRING"))).isEqualTo(CoreType.POLYLINE);
        assertThat(coreTypeOf(geometryAttribute("POLYGON"))).isEqualTo(CoreType.SURFACE);
        assertThat(coreTypeOf(geometryAttribute("MULTIPOLYGON"))).isEqualTo(CoreType.SURFACE);
        assertThat(coreTypeOf(geometryAttribute(null))).isEqualTo(CoreType.UNKNOWN);
    }

    @Test
    void normalizesGeometryKindStrings() {
        AttributeMetadataBuilder multiSurface = geometryAttribute("MultiSurface");
        AttributeMetadataBuilder postgisLine = geometryAttribute("LINESTRING ZM");

        assertThat(multiSurface.geometryKind()).isEqualTo(GeometryKind.MULTIPOLYGON);
        assertThat(postgisLine.geometryKind()).isEqualTo(GeometryKind.LINESTRING);
    }

    @Test
    void fallsBackToKnownDbTypes() {
        assertThat(coreTypeOf(attributeWithDbType("VARCHAR(255)"))).isEqualTo(CoreType.TEXT);
        assertThat(coreTypeOf(attributeWithDbType("INTEGER"))).isEqualTo(CoreType.NUMERIC);
        assertThat(coreTypeOf(attributeWithDbType("BOOLEAN"))).isEqualTo(CoreType.BOOLEAN);
        assertThat(coreTypeOf(attributeWithDbType("DATE"))).isEqualTo(CoreType.DATE);
        assertThat(coreTypeOf(attributeWithDbType("TIMESTAMP"))).isEqualTo(CoreType.DATETIME);
        assertThat(coreTypeOf(attributeWithDbType("TIME"))).isEqualTo(CoreType.TIME);
        assertThat(coreTypeOf(attributeWithDbType("GEOMETRY"))).isEqualTo(CoreType.UNKNOWN);
    }

    @Test
    void unknownWhenNoTypeInformationExists() {
        assertThat(coreTypeOf(new AttributeMetadataBuilder("unknown"))).isEqualTo(CoreType.UNKNOWN);
    }

    private static CoreType coreTypeOf(AttributeMetadataBuilder attribute) {
        return AttributeTypeResolver.inferCoreType(attribute);
    }

    private AttributeMetadataBuilder attributeWithIliType(String iliType) {
        AttributeMetadataBuilder attribute = new AttributeMetadataBuilder("attribute");
        attribute.iliType(iliType);
        return attribute;
    }

    private AttributeMetadataBuilder attributeWithDbType(String dbType) {
        AttributeMetadataBuilder attribute = new AttributeMetadataBuilder("attribute");
        attribute.dbType(dbType);
        return attribute;
    }

    private AttributeMetadataBuilder geometryAttribute(String geometryKind) {
        AttributeMetadataBuilder attribute = new AttributeMetadataBuilder("geometry");
        attribute.geometry(true);
        attribute.geometryKind(geometryKind);
        return attribute;
    }
}

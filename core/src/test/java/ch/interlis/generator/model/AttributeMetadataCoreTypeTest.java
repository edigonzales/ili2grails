package ch.interlis.generator.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeMetadataCoreTypeTest {

    @Test
    void infersCoreTypesFromIliTypes() {
        assertThat(attributeWithIliType("TEXT").getCoreType()).isEqualTo(CoreType.TEXT);
        assertThat(attributeWithIliType("MTEXT").getCoreType()).isEqualTo(CoreType.MTEXT);
        assertThat(attributeWithIliType("NumericType").getCoreType()).isEqualTo(CoreType.NUMERIC);
        assertThat(attributeWithIliType("BOOLEAN").getCoreType()).isEqualTo(CoreType.BOOLEAN);
        assertThat(attributeWithIliType("INTERLIS.XMLDATE").getCoreType()).isEqualTo(CoreType.DATE);
        assertThat(attributeWithIliType("INTERLIS.XMLDATETIME").getCoreType()).isEqualTo(CoreType.DATETIME);
        assertThat(attributeWithIliType("INTERLIS.XMLTIME").getCoreType()).isEqualTo(CoreType.TIME);
        assertThat(attributeWithIliType("EnumerationType").getCoreType()).isEqualTo(CoreType.ENUM);
        assertThat(attributeWithIliType("CoordType").getCoreType()).isEqualTo(CoreType.COORD);
        assertThat(attributeWithIliType("PolylineType").getCoreType()).isEqualTo(CoreType.POLYLINE);
        assertThat(attributeWithIliType("SurfaceType").getCoreType()).isEqualTo(CoreType.SURFACE);
        assertThat(attributeWithIliType("ReferenceType").getCoreType()).isEqualTo(CoreType.REFERENCE);
        assertThat(attributeWithIliType("CompositionType").getCoreType()).isEqualTo(CoreType.COMPOSITION);
        assertThat(attributeWithIliType("ObjectType").getCoreType()).isEqualTo(CoreType.OBJECT);
    }

    @Test
    void infersCoreTypesFromSemanticFlagsBeforeDbType() {
        AttributeMetadata enumAttribute = new AttributeMetadata("status");
        enumAttribute.setEnumType("Model.Status");
        enumAttribute.setDbType("VARCHAR");

        AttributeMetadata referenceAttribute = new AttributeMetadata("owner");
        referenceAttribute.setReferencedClass("Model.Owner");
        referenceAttribute.setDbType("INTEGER");

        assertThat(enumAttribute.getCoreType()).isEqualTo(CoreType.ENUM);
        assertThat(referenceAttribute.getCoreType()).isEqualTo(CoreType.REFERENCE);
    }

    @Test
    void infersGeometryKindsButKeepsGenericGeometryUnknown() {
        AttributeMetadata point = geometryAttribute("POINT");
        AttributeMetadata multiPoint = geometryAttribute("MULTIPOINT");
        AttributeMetadata line = geometryAttribute("LINESTRING");
        AttributeMetadata multiLine = geometryAttribute("MULTILINESTRING");
        AttributeMetadata surface = geometryAttribute("POLYGON");
        AttributeMetadata multiSurface = geometryAttribute("MULTIPOLYGON");
        AttributeMetadata generic = geometryAttribute(null);

        assertThat(point.getCoreType()).isEqualTo(CoreType.COORD);
        assertThat(multiPoint.getCoreType()).isEqualTo(CoreType.COORD);
        assertThat(line.getCoreType()).isEqualTo(CoreType.POLYLINE);
        assertThat(multiLine.getCoreType()).isEqualTo(CoreType.POLYLINE);
        assertThat(surface.getCoreType()).isEqualTo(CoreType.SURFACE);
        assertThat(multiSurface.getCoreType()).isEqualTo(CoreType.SURFACE);
        assertThat(generic.getCoreType()).isEqualTo(CoreType.UNKNOWN);
    }

    @Test
    void normalizesGeometryKindStrings() {
        AttributeMetadata multiSurface = geometryAttribute("MultiSurface");
        AttributeMetadata postgisLine = geometryAttribute("LINESTRING ZM");

        assertThat(multiSurface.getGeometryKindEnum()).isEqualTo(GeometryKind.MULTIPOLYGON);
        assertThat(multiSurface.getGeometryKind()).isEqualTo("MULTIPOLYGON");
        assertThat(postgisLine.getGeometryKindEnum()).isEqualTo(GeometryKind.LINESTRING);
    }

    @Test
    void fallsBackToKnownDbTypes() {
        assertThat(attributeWithDbType("VARCHAR(255)").getCoreType()).isEqualTo(CoreType.TEXT);
        assertThat(attributeWithDbType("INTEGER").getCoreType()).isEqualTo(CoreType.NUMERIC);
        assertThat(attributeWithDbType("BOOLEAN").getCoreType()).isEqualTo(CoreType.BOOLEAN);
        assertThat(attributeWithDbType("DATE").getCoreType()).isEqualTo(CoreType.DATE);
        assertThat(attributeWithDbType("TIMESTAMP").getCoreType()).isEqualTo(CoreType.DATETIME);
        assertThat(attributeWithDbType("TIME").getCoreType()).isEqualTo(CoreType.TIME);
        assertThat(attributeWithDbType("GEOMETRY").getCoreType()).isEqualTo(CoreType.UNKNOWN);
    }

    @Test
    void unknownWhenNoTypeInformationExists() {
        assertThat(new AttributeMetadata("unknown").getCoreType()).isEqualTo(CoreType.UNKNOWN);
    }

    private AttributeMetadata attributeWithIliType(String iliType) {
        AttributeMetadata attribute = new AttributeMetadata("attribute");
        attribute.setIliType(iliType);
        return attribute;
    }

    private AttributeMetadata attributeWithDbType(String dbType) {
        AttributeMetadata attribute = new AttributeMetadata("attribute");
        attribute.setDbType(dbType);
        return attribute;
    }

    private AttributeMetadata geometryAttribute(String geometryKind) {
        AttributeMetadata attribute = new AttributeMetadata("geometry");
        attribute.setGeometry(true);
        attribute.setGeometryKind(geometryKind);
        return attribute;
    }
}

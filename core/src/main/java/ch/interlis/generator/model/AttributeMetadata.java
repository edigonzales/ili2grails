package ch.interlis.generator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Metadaten eines Attributes.
 *
 * <p>Typ-Informationen (coreType/javaType) werden vor dem Freeze durch den
 * {@code AttributeTypeResolver} aufgelöst; Getter mutieren niemals Zustand.</p>
 */
public final class AttributeMetadata {

    private final String name;
    private final String qualifiedName;
    private final String columnName;
    private final String sqlName;
    private final String iliType;
    private final String domainName;
    private final CoreType coreType;
    private final String javaType;
    private final String dbType;
    private final boolean mandatory;
    private final boolean primaryKey;
    private final boolean foreignKey;
    private final boolean geometry;
    private final Integer geometrySrid;
    private final GeometryKind geometryKind;
    private final Boolean geometryHasZ;
    private final Boolean geometryHasM;
    private final Boolean allowEmptyGeometry;
    private final String documentation;
    private final AttributeConstraints constraints;
    private final String enumType;
    private final List<EnumMetadata.EnumValue> enumValues;
    private final String unit;
    private final String referencedClass;
    private final String referencedAttribute;
    private final Map<String, String> labels;

    public AttributeMetadata(String name,
                      String qualifiedName,
                      String columnName,
                      String sqlName,
                      String iliType,
                      String domainName,
                      CoreType coreType,
                      String javaType,
                      String dbType,
                      boolean mandatory,
                      boolean primaryKey,
                      boolean foreignKey,
                      boolean geometry,
                      Integer geometrySrid,
                      GeometryKind geometryKind,
                      Boolean geometryHasZ,
                      Boolean geometryHasM,
                      Boolean allowEmptyGeometry,
                      String documentation,
                      AttributeConstraints constraints,
                      String enumType,
                      List<EnumMetadata.EnumValue> enumValues,
                      String unit,
                      String referencedClass,
                      String referencedAttribute,
                      Map<String, String> labels) {
        this.name = Objects.requireNonNull(name, "name");
        this.qualifiedName = qualifiedName;
        this.columnName = columnName;
        this.sqlName = sqlName;
        this.iliType = iliType;
        this.domainName = domainName;
        this.coreType = coreType;
        this.javaType = javaType;
        this.dbType = dbType;
        this.mandatory = mandatory;
        this.primaryKey = primaryKey;
        this.foreignKey = foreignKey;
        this.geometry = geometry;
        this.geometrySrid = geometrySrid;
        this.geometryKind = geometryKind;
        this.geometryHasZ = geometryHasZ;
        this.geometryHasM = geometryHasM;
        this.allowEmptyGeometry = allowEmptyGeometry;
        this.documentation = documentation;
        this.constraints = constraints;
        this.enumType = enumType;
        this.enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        this.unit = unit;
        this.referencedClass = referencedClass;
        this.referencedAttribute = referencedAttribute;
        this.labels = labels == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(labels));
    }

    public static ch.interlis.generator.model.builder.AttributeMetadataBuilder builder(String name) {
        return new ch.interlis.generator.model.builder.AttributeMetadataBuilder(name);
    }

    public ch.interlis.generator.model.builder.AttributeMetadataBuilder toBuilder() {
        return ch.interlis.generator.model.builder.AttributeMetadataBuilder.from(this);
    }

    public String getName() {
        return name;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getSqlName() {
        return sqlName;
    }

    public String getIliType() {
        return iliType;
    }

    public String getDomainName() {
        return domainName;
    }

    public CoreType getCoreType() {
        return coreType;
    }

    public String getJavaType() {
        return javaType;
    }

    public String getDbType() {
        return dbType;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public boolean isForeignKey() {
        return foreignKey;
    }

    public boolean isGeometry() {
        return geometry;
    }

    public Integer getGeometrySrid() {
        return geometrySrid;
    }

    public String getGeometryKind() {
        return geometryKind != null ? geometryKind.name() : null;
    }

    public GeometryKind getGeometryKindEnum() {
        return geometryKind;
    }

    public Boolean getGeometryHasZ() {
        return geometryHasZ;
    }

    public Boolean getGeometryHasM() {
        return geometryHasM;
    }

    public Boolean getAllowEmptyGeometry() {
        return allowEmptyGeometry;
    }

    public String getDocumentation() {
        return documentation;
    }

    public AttributeConstraints getConstraints() {
        return constraints;
    }

    public Integer getMaxLength() {
        return constraints != null ? constraints.maxLength() : null;
    }

    public String getMinValue() {
        return constraints != null ? constraints.minInclusive() : null;
    }

    public String getMaxValue() {
        return constraints != null ? constraints.maxInclusive() : null;
    }

    public Integer getPrecision() {
        return constraints != null ? constraints.precision() : null;
    }

    public Integer getScale() {
        return constraints != null ? constraints.scale() : null;
    }

    public Integer getCardinalityMin() {
        return constraints != null ? constraints.cardinalityMin() : null;
    }

    public Integer getCardinalityMax() {
        return constraints != null ? constraints.cardinalityMax() : null;
    }

    public boolean isOrdered() {
        return constraints != null && constraints.ordered();
    }

    public String getEnumType() {
        return enumType;
    }

    public List<EnumMetadata.EnumValue> getEnumValues() {
        return enumValues;
    }

    public String getUnit() {
        return unit;
    }

    public String getReferencedClass() {
        return referencedClass;
    }

    public String getReferencedAttribute() {
        return referencedAttribute;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public List<EnumMetadata.EnumValue> getEnumValueList() {
        return enumValues;
    }

    @Override
    public String toString() {
        return "AttributeMetadata{" +
            "name='" + name + '\'' +
            ", coreType=" + coreType +
            ", javaType='" + javaType + '\'' +
            ", mandatory=" + mandatory +
            ", primaryKey=" + primaryKey +
            '}';
    }

}


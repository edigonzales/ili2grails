package ch.interlis.generator.model.builder;

import ch.interlis.generator.model.AttributeConstraints;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.GeometryKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable Builder für {@link AttributeMetadata}.
 *
 * <p>Nicht thread-safe; nicht aus Generatoren verwenden. Der Abschluss
 * erfolgt über den {@code ModelMetadataBuilder} und die
 * {@code ModelMetadataFactory} (freeze).</p>
 */
public final class AttributeMetadataBuilder {

    private String name;
    private String qualifiedName;
    private String columnName;
    private String sqlName;
    private String iliType;
    private String domainName;
    private CoreType coreType;
    private String javaType;
    private String dbType;
    private boolean mandatory;
    private boolean primaryKey;
    private boolean foreignKey;
    private boolean geometry;
    private Integer geometrySrid;
    private GeometryKind geometryKind;
    private Boolean geometryHasZ;
    private Boolean geometryHasM;
    private Boolean allowEmptyGeometry;
    private String documentation;
    private Integer maxLength;
    private String minValue;
    private String maxValue;
    private Integer precision;
    private Integer scale;
    private Integer cardinalityMin;
    private Integer cardinalityMax;
    private boolean ordered;
    private String enumType;
    private final List<EnumMetadata.EnumValue> enumValues = new ArrayList<>();
    private String unit;
    private String referencedClass;
    private String referencedAttribute;
    private final Map<String, String> labels = new LinkedHashMap<>();

    public AttributeMetadataBuilder(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public static AttributeMetadataBuilder from(AttributeMetadata attribute) {
        AttributeMetadataBuilder builder = new AttributeMetadataBuilder(attribute.getName());
        builder.qualifiedName = attribute.getQualifiedName();
        builder.columnName = attribute.getColumnName();
        builder.sqlName = attribute.getSqlName();
        builder.iliType = attribute.getIliType();
        builder.domainName = attribute.getDomainName();
        builder.coreType = attribute.getCoreType();
        builder.javaType = attribute.getJavaType();
        builder.dbType = attribute.getDbType();
        builder.mandatory = attribute.isMandatory();
        builder.primaryKey = attribute.isPrimaryKey();
        builder.foreignKey = attribute.isForeignKey();
        builder.geometry = attribute.isGeometry();
        builder.geometrySrid = attribute.getGeometrySrid();
        builder.geometryKind = attribute.getGeometryKindEnum();
        builder.geometryHasZ = attribute.getGeometryHasZ();
        builder.geometryHasM = attribute.getGeometryHasM();
        builder.allowEmptyGeometry = attribute.getAllowEmptyGeometry();
        builder.documentation = attribute.getDocumentation();
        AttributeConstraints constraints = attribute.getConstraints();
        if (constraints != null) {
            builder.maxLength = constraints.maxLength();
            builder.minValue = constraints.minInclusive();
            builder.maxValue = constraints.maxInclusive();
            builder.precision = constraints.precision();
            builder.scale = constraints.scale();
            builder.cardinalityMin = constraints.cardinalityMin();
            builder.cardinalityMax = constraints.cardinalityMax();
            builder.ordered = constraints.ordered();
        }
        builder.enumType = attribute.getEnumType();
        builder.enumValues.addAll(attribute.getEnumValues());
        builder.unit = attribute.getUnit();
        builder.referencedClass = attribute.getReferencedClass();
        builder.referencedAttribute = attribute.getReferencedAttribute();
        builder.labels.putAll(attribute.getLabels());
        return builder;
    }

    public String name() {
        return name;
    }

    public String qualifiedName() {
        return qualifiedName;
    }

    public String columnName() {
        return columnName;
    }

    public String sqlName() {
        return sqlName;
    }

    public Integer geometrySrid() {
        return geometrySrid;
    }

    public AttributeMetadataBuilder qualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
        return this;
    }

    public AttributeMetadataBuilder columnName(String columnName) {
        this.columnName = columnName;
        return this;
    }

    public AttributeMetadataBuilder sqlName(String sqlName) {
        this.sqlName = sqlName;
        return this;
    }

    public AttributeMetadataBuilder iliType(String iliType) {
        this.iliType = iliType;
        return this;
    }

    public AttributeMetadataBuilder domainName(String domainName) {
        this.domainName = domainName;
        return this;
    }

    public AttributeMetadataBuilder coreType(CoreType coreType) {
        this.coreType = coreType;
        return this;
    }

    public AttributeMetadataBuilder javaType(String javaType) {
        this.javaType = javaType;
        return this;
    }

    public AttributeMetadataBuilder dbType(String dbType) {
        this.dbType = dbType;
        return this;
    }

    public AttributeMetadataBuilder mandatory(boolean mandatory) {
        this.mandatory = mandatory;
        return this;
    }

    public AttributeMetadataBuilder primaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
        return this;
    }

    public AttributeMetadataBuilder foreignKey(boolean foreignKey) {
        this.foreignKey = foreignKey;
        return this;
    }

    public AttributeMetadataBuilder geometry(boolean geometry) {
        this.geometry = geometry;
        return this;
    }

    public AttributeMetadataBuilder geometrySrid(Integer geometrySrid) {
        this.geometrySrid = geometrySrid;
        return this;
    }

    public AttributeMetadataBuilder geometryKind(String geometryKind) {
        this.geometryKind = GeometryKind.from(geometryKind);
        return this;
    }

    public AttributeMetadataBuilder geometryKind(GeometryKind geometryKind) {
        this.geometryKind = geometryKind;
        return this;
    }

    public AttributeMetadataBuilder geometryHasZ(Boolean geometryHasZ) {
        this.geometryHasZ = geometryHasZ;
        return this;
    }

    public AttributeMetadataBuilder geometryHasM(Boolean geometryHasM) {
        this.geometryHasM = geometryHasM;
        return this;
    }

    public AttributeMetadataBuilder allowEmptyGeometry(Boolean allowEmptyGeometry) {
        this.allowEmptyGeometry = allowEmptyGeometry;
        return this;
    }

    public AttributeMetadataBuilder documentation(String documentation) {
        this.documentation = documentation;
        return this;
    }

    public AttributeMetadataBuilder maxLength(Integer maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public AttributeMetadataBuilder minValue(String minValue) {
        this.minValue = minValue;
        return this;
    }

    public AttributeMetadataBuilder maxValue(String maxValue) {
        this.maxValue = maxValue;
        return this;
    }

    public AttributeMetadataBuilder precision(Integer precision) {
        this.precision = precision;
        return this;
    }

    public AttributeMetadataBuilder scale(Integer scale) {
        this.scale = scale;
        return this;
    }

    public AttributeMetadataBuilder cardinalityMin(Integer cardinalityMin) {
        this.cardinalityMin = cardinalityMin;
        return this;
    }

    public AttributeMetadataBuilder cardinalityMax(Integer cardinalityMax) {
        this.cardinalityMax = cardinalityMax;
        return this;
    }

    public AttributeMetadataBuilder ordered(boolean ordered) {
        this.ordered = ordered;
        return this;
    }

    public AttributeMetadataBuilder enumType(String enumType) {
        this.enumType = enumType;
        return this;
    }

    public AttributeMetadataBuilder enumValue(EnumMetadata.EnumValue value) {
        this.enumValues.add(Objects.requireNonNull(value, "value"));
        return this;
    }

    public AttributeMetadataBuilder unit(String unit) {
        this.unit = unit;
        return this;
    }

    public AttributeMetadataBuilder referencedClass(String referencedClass) {
        this.referencedClass = referencedClass;
        return this;
    }

    public AttributeMetadataBuilder referencedAttribute(String referencedAttribute) {
        this.referencedAttribute = referencedAttribute;
        return this;
    }

    public AttributeMetadataBuilder label(String language, String label) {
        this.labels.put(language, label);
        return this;
    }

    public String dbType() {
        return dbType;
    }

    public String iliType() {
        return iliType;
    }

    public boolean geometry() {
        return geometry;
    }

    public String enumType() {
        return enumType;
    }

    public String referencedClass() {
        return referencedClass;
    }

    public GeometryKind geometryKind() {
        return geometryKind;
    }

    public String javaType() {
        return javaType;
    }

    public CoreType coreType() {
        return coreType;
    }

    public boolean mandatory() {
        return mandatory;
    }

    public boolean primaryKey() {
        return primaryKey;
    }

    public boolean foreignKey() {
        return foreignKey;
    }

    public Map<String, String> labels() {
        return java.util.Collections.unmodifiableMap(labels);
    }

    public List<EnumMetadata.EnumValue> enumValues() {
        return java.util.Collections.unmodifiableList(enumValues);
    }

    /** Unvalidierter Build; Abschluss über die ModelMetadataFactory. */
    public AttributeMetadata buildUnchecked() {
        AttributeConstraints constraints = new AttributeConstraints(
            mandatory,
            maxLength,
            minValue,
            maxValue,
            precision,
            scale,
            cardinalityMin,
            cardinalityMax,
            ordered
        );
        return new AttributeMetadata(
            name,
            qualifiedName,
            columnName,
            sqlName,
            iliType,
            domainName,
            coreType,
            javaType,
            dbType,
            mandatory,
            primaryKey,
            foreignKey,
            geometry,
            geometrySrid,
            geometryKind,
            geometryHasZ,
            geometryHasM,
            allowEmptyGeometry,
            documentation,
            constraints,
            enumType,
            List.copyOf(enumValues),
            unit,
            referencedClass,
            referencedAttribute,
            java.util.Collections.unmodifiableMap(new LinkedHashMap<>(labels))
        );
    }
}

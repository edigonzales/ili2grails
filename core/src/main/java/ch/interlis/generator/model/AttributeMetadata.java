package ch.interlis.generator.model;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Repräsentiert ein Attribut einer INTERLIS-Klasse (wird zu einer Datenbankspalte).
 */
public class AttributeMetadata {
    
    private String name;                    // INTERLIS Attributname
    private String qualifiedName;           // Vollqualifizierter INTERLIS-Name
    private String columnName;              // Datenbank-Spaltenname
    private String sqlName;                 // SQL-Name (falls abweichend)
    private String iliType;                 // INTERLIS-Typ (TEXT, COORD, etc.)
    private String domainName;              // Benannter INTERLIS-Domain, falls vorhanden
    private CoreType coreType;              // Framework-agnostischer Core-Typ
    private String javaType;                // Gemappter Java-Typ
    private String dbType;                  // Datenbank-Typ
    private boolean mandatory;
    private boolean isPrimaryKey;
    private boolean isForeignKey;
    private boolean isGeometry;
    private Integer geometrySrid;
    private GeometryKind geometryKind;
    private Boolean geometryHasZ;
    private Boolean geometryHasM;
    private Boolean allowEmptyGeometry;
    private String documentation;
    
    // Constraints
    private Integer maxLength;
    private String minValue;
    private String maxValue;
    private Integer precision;
    private Integer scale;
    private Integer cardinalityMin;
    private Integer cardinalityMax;
    private boolean ordered;
    private String enumType;                // Falls Enumeration
    private final java.util.List<EnumMetadata.EnumValue> enumValues = new java.util.ArrayList<>();
    private String unit;                    // Masseinheit
    
    // Beziehungen
    private String referencedClass;         // Falls FK
    private String referencedAttribute;
    
    private Map<String, String> labels = new HashMap<>();
    
    public AttributeMetadata(String name) {
        this.name = name;
    }
    
    public void addLabel(String language, String label) {
        labels.put(language, label);
    }
    
    /**
     * Bestimmt den Java-Typ basierend auf INTERLIS- und Datenbanktyp
     */
    public void inferJavaType() {
        if (javaType != null) return; // Bereits gesetzt
        
        if (isGeometry) {
            javaType = "org.locationtech.jts.geom.Geometry";
            return;
        }
        
        if (enumType != null) {
            javaType = "String"; // Oder spezifische Enum-Klasse
            return;
        }
        
        if (iliType != null) {
            javaType = switch (iliType.toUpperCase()) {
                case "TEXT", "MTEXT" -> "String";
                case "BOOLEAN" -> "Boolean";
                case "DATE" -> "java.time.LocalDate";
                case "DATETIME" -> "java.time.LocalDateTime";
                case "TIME" -> "java.time.LocalTime";
                case "INTERLIS.XMLDATE" -> "java.time.LocalDate";
                case "INTERLIS.XMLDATETIME" -> "java.time.LocalDateTime";
                case "INTERLIS.XMLTIME" -> "java.time.LocalTime";
                default -> {
                    // Numerische Typen
                    String upperIliType = iliType.toUpperCase(Locale.ROOT);
                    if (upperIliType.contains("COORD")
                        || upperIliType.contains("MULTICOORD")
                        || upperIliType.contains("POLYLINE")
                        || upperIliType.contains("LINE")
                        || upperIliType.contains("SURFACE")
                        || upperIliType.contains("AREA")) {
                        yield "org.locationtech.jts.geom.Geometry";
                    } else if (dbType != null) {
                        yield inferJavaTypeFromDbType(dbType);
                    } else {
                        yield "Object";
                    }
                }
            };
        } else if (dbType != null) {
            javaType = inferJavaTypeFromDbType(dbType);
        } else {
            javaType = "Object";
        }
    }
    
    private String inferJavaTypeFromDbType(String dbType) {
        String upperDbType = dbType.toUpperCase();
        if (upperDbType.contains("VARCHAR") || upperDbType.contains("TEXT")
            || upperDbType.contains("CHAR")) {
            return "String";
        } else if (upperDbType.contains("INT")) {
            if (upperDbType.contains("BIGINT")) {
                return "Long";
            }
            return "Integer";
        } else if (upperDbType.contains("DECIMAL") || upperDbType.contains("NUMERIC")) {
            return "java.math.BigDecimal";
        } else if (upperDbType.contains("DOUBLE") || upperDbType.contains("FLOAT")) {
            return "Double";
        } else if (upperDbType.contains("BOOL")) {
            return "Boolean";
        } else if (upperDbType.contains("DATE")) {
            if (upperDbType.contains("TIME")) {
                return "java.time.LocalDateTime";
            }
            return "java.time.LocalDate";
        } else if (upperDbType.contains("GEOMETRY")) {
            return "org.locationtech.jts.geom.Geometry";
        }
        return "Object";
    }

    /**
     * Bestimmt den framework-agnostischen Core-Typ aus semantischen Metadaten
     * und, falls nötig, aus Datenbank-/Java-Fallbacks.
     */
    public void inferCoreType() {
        coreType = inferCoreTypeValue();
    }

    private CoreType inferCoreTypeValue() {
        if (enumType != null) {
            return CoreType.ENUM;
        }

        CoreType typeFromIli = inferCoreTypeFromIliType(iliType);
        if (typeFromIli != null) {
            return typeFromIli;
        }

        if (referencedClass != null) {
            return CoreType.REFERENCE;
        }

        if (isGeometry) {
            return inferGeometryCoreType();
        }

        CoreType typeFromDb = inferCoreTypeFromDbType(dbType);
        if (typeFromDb != null) {
            return typeFromDb;
        }

        CoreType typeFromJava = inferCoreTypeFromJavaType(javaType);
        if (typeFromJava != null) {
            return typeFromJava;
        }

        return CoreType.UNKNOWN;
    }

    private CoreType inferCoreTypeFromIliType(String iliType) {
        if (iliType == null || iliType.isBlank()) {
            return null;
        }
        String upperIliType = iliType.trim().toUpperCase(Locale.ROOT);
        return switch (upperIliType) {
            case "TEXT", "TEXTTYPE", "TEXTOIDTYPE" -> CoreType.TEXT;
            case "MTEXT" -> CoreType.MTEXT;
            case "NUMERIC", "NUMERICTYPE", "NUMERICALTYPE" -> CoreType.NUMERIC;
            case "BOOLEAN" -> CoreType.BOOLEAN;
            case "DATE", "XMLDATE", "INTERLIS.XMLDATE" -> CoreType.DATE;
            case "DATETIME", "XMLDATETIME", "INTERLIS.XMLDATETIME" -> CoreType.DATETIME;
            case "TIME", "XMLTIME", "INTERLIS.XMLTIME" -> CoreType.TIME;
            case "ENUM", "ENUMERATIONTYPE" -> CoreType.ENUM;
            case "COORD", "COORDTYPE", "MULTICOORDTYPE" -> CoreType.COORD;
            case "POLYLINE", "POLYLINETYPE", "LINETYPE", "MULTIPOLYLINETYPE" -> CoreType.POLYLINE;
            case "SURFACE", "SURFACETYPE", "AREATYPE", "MULTISURFACETYPE", "MULTIAREATYPE" -> CoreType.SURFACE;
            case "REFERENCE", "REFERENCETYPE" -> CoreType.REFERENCE;
            case "COMPOSITION", "COMPOSITIONTYPE" -> CoreType.COMPOSITION;
            case "OBJECT", "OBJECTTYPE" -> CoreType.OBJECT;
            default -> null;
        };
    }

    private CoreType inferGeometryCoreType() {
        if (geometryKind == null) {
            return CoreType.UNKNOWN;
        }
        if (geometryKind == GeometryKind.POINT || geometryKind == GeometryKind.MULTIPOINT) {
            return CoreType.COORD;
        }
        if (geometryKind == GeometryKind.LINESTRING || geometryKind == GeometryKind.MULTILINESTRING) {
            return CoreType.POLYLINE;
        }
        if (geometryKind == GeometryKind.POLYGON || geometryKind == GeometryKind.MULTIPOLYGON) {
            return CoreType.SURFACE;
        }
        return CoreType.UNKNOWN;
    }

    private CoreType inferCoreTypeFromDbType(String dbType) {
        if (dbType == null || dbType.isBlank()) {
            return null;
        }
        String upperDbType = dbType.toUpperCase(Locale.ROOT);
        if (upperDbType.contains("GEOMETRY")) {
            return CoreType.UNKNOWN;
        }
        if (upperDbType.contains("VARCHAR") || upperDbType.contains("TEXT")
            || upperDbType.contains("CHAR")) {
            return CoreType.TEXT;
        }
        if (upperDbType.contains("DECIMAL") || upperDbType.contains("NUMERIC")
            || upperDbType.contains("DOUBLE") || upperDbType.contains("FLOAT")
            || upperDbType.contains("REAL") || upperDbType.contains("INT")) {
            return CoreType.NUMERIC;
        }
        if (upperDbType.contains("BOOL") || upperDbType.contains("BIT")) {
            return CoreType.BOOLEAN;
        }
        if (upperDbType.contains("TIMESTAMP") || upperDbType.contains("DATETIME")) {
            return CoreType.DATETIME;
        }
        if (upperDbType.contains("TIME")) {
            return CoreType.TIME;
        }
        if (upperDbType.contains("DATE")) {
            return CoreType.DATE;
        }
        return null;
    }

    private CoreType inferCoreTypeFromJavaType(String javaType) {
        if (javaType == null || javaType.isBlank()) {
            return null;
        }
        String simpleType = javaType;
        int lastDot = simpleType.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleType = simpleType.substring(lastDot + 1);
        }
        return switch (simpleType) {
            case "String" -> CoreType.TEXT;
            case "Boolean" -> CoreType.BOOLEAN;
            case "LocalDate" -> CoreType.DATE;
            case "LocalDateTime" -> CoreType.DATETIME;
            case "LocalTime" -> CoreType.TIME;
            case "Integer", "Long", "BigDecimal", "Double", "Float" -> CoreType.NUMERIC;
            default -> null;
        };
    }
    
    // Getters and Setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }
    
    public String getColumnName() {
        return columnName;
    }
    
    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }
    
    public String getSqlName() {
        return sqlName;
    }
    
    public void setSqlName(String sqlName) {
        this.sqlName = sqlName;
    }
    
    public String getIliType() {
        return iliType;
    }
    
    public void setIliType(String iliType) {
        this.iliType = iliType;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public CoreType getCoreType() {
        return coreType != null ? coreType : inferCoreTypeValue();
    }

    public void setCoreType(CoreType coreType) {
        this.coreType = coreType;
    }

    public AttributeConstraints getConstraints() {
        return new AttributeConstraints(
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
    }
    
    public String getJavaType() {
        if (javaType == null) {
            inferJavaType();
        }
        return javaType;
    }
    
    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }
    
    public String getDbType() {
        return dbType;
    }
    
    public void setDbType(String dbType) {
        this.dbType = dbType;
    }
    
    public boolean isMandatory() {
        return mandatory;
    }
    
    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }
    
    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }
    
    public void setPrimaryKey(boolean primaryKey) {
        isPrimaryKey = primaryKey;
    }
    
    public boolean isForeignKey() {
        return isForeignKey;
    }
    
    public void setForeignKey(boolean foreignKey) {
        isForeignKey = foreignKey;
    }
    
    public boolean isGeometry() {
        return isGeometry;
    }
    
    public void setGeometry(boolean geometry) {
        isGeometry = geometry;
    }

    public Integer getGeometrySrid() {
        return geometrySrid;
    }

    public void setGeometrySrid(Integer geometrySrid) {
        this.geometrySrid = geometrySrid;
    }

    public String getGeometryKind() {
        return geometryKind != null ? geometryKind.name() : null;
    }

    public void setGeometryKind(String geometryKind) {
        this.geometryKind = GeometryKind.from(geometryKind);
    }

    public GeometryKind getGeometryKindEnum() {
        return geometryKind;
    }

    public void setGeometryKind(GeometryKind geometryKind) {
        this.geometryKind = geometryKind;
    }

    public Boolean getGeometryHasZ() {
        return geometryHasZ;
    }

    public void setGeometryHasZ(Boolean geometryHasZ) {
        this.geometryHasZ = geometryHasZ;
    }

    public Boolean getGeometryHasM() {
        return geometryHasM;
    }

    public void setGeometryHasM(Boolean geometryHasM) {
        this.geometryHasM = geometryHasM;
    }

    public Boolean getAllowEmptyGeometry() {
        return allowEmptyGeometry;
    }

    public void setAllowEmptyGeometry(Boolean allowEmptyGeometry) {
        this.allowEmptyGeometry = allowEmptyGeometry;
    }
    
    public String getDocumentation() {
        return documentation;
    }
    
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }
    
    public Integer getMaxLength() {
        return maxLength;
    }
    
    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }
    
    public String getMinValue() {
        return minValue;
    }
    
    public void setMinValue(String minValue) {
        this.minValue = minValue;
    }
    
    public String getMaxValue() {
        return maxValue;
    }
    
    public void setMaxValue(String maxValue) {
        this.maxValue = maxValue;
    }

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public Integer getCardinalityMin() {
        return cardinalityMin;
    }

    public void setCardinalityMin(Integer cardinalityMin) {
        this.cardinalityMin = cardinalityMin;
    }

    public Integer getCardinalityMax() {
        return cardinalityMax;
    }

    public void setCardinalityMax(Integer cardinalityMax) {
        this.cardinalityMax = cardinalityMax;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }
    
    public String getEnumType() {
        return enumType;
    }
    
    public void setEnumType(String enumType) {
        this.enumType = enumType;
    }

    public java.util.List<EnumMetadata.EnumValue> getEnumValues() {
        return enumValues;
    }

    public void addEnumValue(EnumMetadata.EnumValue value) {
        enumValues.add(value);
    }
    
    public String getUnit() {
        return unit;
    }
    
    public void setUnit(String unit) {
        this.unit = unit;
    }
    
    public String getReferencedClass() {
        return referencedClass;
    }
    
    public void setReferencedClass(String referencedClass) {
        this.referencedClass = referencedClass;
    }
    
    public String getReferencedAttribute() {
        return referencedAttribute;
    }
    
    public void setReferencedAttribute(String referencedAttribute) {
        this.referencedAttribute = referencedAttribute;
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
    
    @Override
    public String toString() {
        return "AttributeMetadata{" +
                "name='" + name + '\'' +
                ", columnName='" + columnName + '\'' +
                ", iliType='" + iliType + '\'' +
                ", domainName='" + domainName + '\'' +
                ", coreType=" + getCoreType() +
                ", javaType='" + getJavaType() + '\'' +
                ", mandatory=" + mandatory +
                ", cardinality=" + cardinalityMin + ".." + cardinalityMax +
                ", ordered=" + ordered +
                ", isPrimaryKey=" + isPrimaryKey +
                ", isForeignKey=" + isForeignKey +
                ", geometrySrid=" + geometrySrid +
                ", geometryKind=" + geometryKind +
                ", geometryHasZ=" + geometryHasZ +
                ", geometryHasM=" + geometryHasM +
                ", allowEmptyGeometry=" + allowEmptyGeometry +
                '}';
    }
}

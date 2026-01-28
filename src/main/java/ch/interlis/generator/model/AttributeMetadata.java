package ch.interlis.generator.model;

import java.util.HashMap;
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
    private String javaType;                // Gemappter Java-Typ
    private String dbType;                  // Datenbank-Typ
    private boolean mandatory;
    private boolean isPrimaryKey;
    private boolean isForeignKey;
    private boolean isGeometry;
    private String documentation;
    
    // Constraints
    private Integer maxLength;
    private String minValue;
    private String maxValue;
    private String enumType;                // Falls Enumeration
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
                case "INTERLIS.XMLDATE" -> "java.time.LocalDate";
                case "INTERLIS.XMLDATETIME" -> "java.time.LocalDateTime";
                default -> {
                    // Numerische Typen
                    if (iliType.contains("COORD") || iliType.contains("MULTICOORD")) {
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
    
    public String getEnumType() {
        return enumType;
    }
    
    public void setEnumType(String enumType) {
        this.enumType = enumType;
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
                ", javaType='" + getJavaType() + '\'' +
                ", mandatory=" + mandatory +
                ", isPrimaryKey=" + isPrimaryKey +
                ", isForeignKey=" + isForeignKey +
                '}';
    }
}

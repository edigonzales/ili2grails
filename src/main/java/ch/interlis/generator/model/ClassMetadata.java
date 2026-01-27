package ch.interlis.generator.model;

import java.util.*;

/**
 * Repräsentiert eine INTERLIS-Klasse (wird zu einer Datenbank-Tabelle).
 */
public class ClassMetadata {
    
    private String name;                    // INTERLIS Klassenname (qualifiziert)
    private String simpleName;              // Einfacher Name ohne Topic/Model
    private String tableName;               // Datenbankname der Tabelle
    private String sqlName;                 // SQL-Name (mit Schema)
    private String documentation;           // Dokumentation aus dem Modell
    private boolean isAbstract;
    private String baseClass;               // Vererbung: Name der Basisklasse
    private ClassKind kind;                 // CLASS, STRUCTURE, ASSOCIATION
    
    private Map<String, AttributeMetadata> attributes = new LinkedHashMap<>();
    private List<RelationshipMetadata> relationships = new ArrayList<>();
    private Map<String, String> labels = new HashMap<>();  // Sprache -> Label
    
    // ili2db spezifisch
    private String inheritanceStrategy;     // newClass, superClass, subClass
    
    public enum ClassKind {
        CLASS,
        STRUCTURE,
        ASSOCIATION
    }
    
    public ClassMetadata(String name) {
        this.name = name;
        this.simpleName = extractSimpleName(name);
    }
    
    private String extractSimpleName(String qualifiedName) {
        if (qualifiedName == null) return null;
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }
    
    public void addAttribute(AttributeMetadata attribute) {
        attributes.put(attribute.getName(), attribute);
    }
    
    public void addRelationship(RelationshipMetadata relationship) {
        relationships.add(relationship);
    }
    
    public void addLabel(String language, String label) {
        labels.put(language, label);
    }
    
    public AttributeMetadata getAttribute(String name) {
        return attributes.get(name);
    }
    
    public Collection<AttributeMetadata> getAllAttributes() {
        return attributes.values();
    }
    
    public List<AttributeMetadata> getNonGeometryAttributes() {
        return attributes.values().stream()
            .filter(a -> !a.isGeometry())
            .toList();
    }
    
    public List<AttributeMetadata> getGeometryAttributes() {
        return attributes.values().stream()
            .filter(AttributeMetadata::isGeometry)
            .toList();
    }
    
    // Getters and Setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
        this.simpleName = extractSimpleName(name);
    }
    
    public String getSimpleName() {
        return simpleName;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public String getSqlName() {
        return sqlName;
    }
    
    public void setSqlName(String sqlName) {
        this.sqlName = sqlName;
    }
    
    public String getDocumentation() {
        return documentation;
    }
    
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }
    
    public boolean isAbstract() {
        return isAbstract;
    }
    
    public void setAbstract(boolean anAbstract) {
        isAbstract = anAbstract;
    }
    
    public String getBaseClass() {
        return baseClass;
    }
    
    public void setBaseClass(String baseClass) {
        this.baseClass = baseClass;
    }
    
    public ClassKind getKind() {
        return kind;
    }
    
    public void setKind(ClassKind kind) {
        this.kind = kind;
    }
    
    public Map<String, AttributeMetadata> getAttributes() {
        return attributes;
    }
    
    public void setAttributes(Map<String, AttributeMetadata> attributes) {
        this.attributes = attributes;
    }
    
    public List<RelationshipMetadata> getRelationships() {
        return relationships;
    }
    
    public void setRelationships(List<RelationshipMetadata> relationships) {
        this.relationships = relationships;
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
    
    public String getInheritanceStrategy() {
        return inheritanceStrategy;
    }
    
    public void setInheritanceStrategy(String inheritanceStrategy) {
        this.inheritanceStrategy = inheritanceStrategy;
    }
    
    @Override
    public String toString() {
        return "ClassMetadata{" +
                "name='" + name + '\'' +
                ", tableName='" + tableName + '\'' +
                ", attributes=" + attributes.size() +
                ", isAbstract=" + isAbstract +
                ", kind=" + kind +
                '}';
    }
}

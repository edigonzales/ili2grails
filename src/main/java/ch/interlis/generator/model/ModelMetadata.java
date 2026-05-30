package ch.interlis.generator.model;

import java.util.*;

/**
 * Repräsentiert die vollständigen Metadaten eines INTERLIS-Modells.
 * Kombiniert Informationen aus ili2db-Metatabellen und dem ili2c-Modell.
 */
public class ModelMetadata {
    
    private String modelName;
    private String schemaName;
    private Map<String, ClassMetadata> classes = new LinkedHashMap<>();
    private Map<String, EnumMetadata> enums = new LinkedHashMap<>();
    private List<RelationshipMetadata> relationships = new ArrayList<>();
    private String iliVersion;
    private String modelVersion;
    private Date importDate;
    
    // ili2db spezifische Informationen
    private String ili2dbVersion;
    private Map<String, String> settings = new HashMap<>();
    
    public ModelMetadata(String modelName) {
        this.modelName = modelName;
    }
    
    public void addClass(ClassMetadata classMetadata) {
        classes.put(classMetadata.getName(), classMetadata);
    }
    
    public void addEnum(EnumMetadata enumMetadata) {
        enums.put(enumMetadata.getName(), enumMetadata);
    }

    public void addRelationship(RelationshipMetadata relationship) {
        Objects.requireNonNull(relationship, "relationship");
        if (relationships.stream().noneMatch(existing -> sameRelationship(existing, relationship))) {
            relationships.add(relationship);
        }
        ClassMetadata sourceClass = getClass(relationship.getSourceClass());
        if (sourceClass != null) {
            sourceClass.addRelationship(relationship);
        }
    }
    
    public ClassMetadata getClass(String name) {
        return classes.get(name);
    }
    
    public Collection<ClassMetadata> getAllClasses() {
        return classes.values();
    }
    
    public Collection<EnumMetadata> getAllEnums() {
        return enums.values();
    }

    public List<RelationshipMetadata> getAllRelationships() {
        List<RelationshipMetadata> allRelationships = new ArrayList<>(relationships);
        for (ClassMetadata classMetadata : classes.values()) {
            for (RelationshipMetadata relationship : classMetadata.getRelationships()) {
                if (allRelationships.stream().noneMatch(existing -> sameRelationship(existing, relationship))) {
                    allRelationships.add(relationship);
                }
            }
        }
        return allRelationships;
    }
    
    // Getters and Setters
    
    public String getModelName() {
        return modelName;
    }
    
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    public String getSchemaName() {
        return schemaName;
    }
    
    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }
    
    public Map<String, ClassMetadata> getClasses() {
        return classes;
    }
    
    public void setClasses(Map<String, ClassMetadata> classes) {
        this.classes = classes;
    }
    
    public Map<String, EnumMetadata> getEnums() {
        return enums;
    }
    
    public void setEnums(Map<String, EnumMetadata> enums) {
        this.enums = enums;
    }
    
    public String getIliVersion() {
        return iliVersion;
    }
    
    public void setIliVersion(String iliVersion) {
        this.iliVersion = iliVersion;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }
    
    public Date getImportDate() {
        return importDate;
    }
    
    public void setImportDate(Date importDate) {
        this.importDate = importDate;
    }
    
    public String getIli2dbVersion() {
        return ili2dbVersion;
    }
    
    public void setIli2dbVersion(String ili2dbVersion) {
        this.ili2dbVersion = ili2dbVersion;
    }
    
    public Map<String, String> getSettings() {
        return settings;
    }
    
    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
    }

    public List<RelationshipMetadata> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<RelationshipMetadata> relationships) {
        this.relationships = relationships;
    }
    
    @Override
    public String toString() {
        return "ModelMetadata{" +
                "modelName='" + modelName + '\'' +
                ", schemaName='" + schemaName + '\'' +
                ", classes=" + classes.size() +
                ", enums=" + enums.size() +
                ", relationships=" + getAllRelationships().size() +
                ", iliVersion='" + iliVersion + '\'' +
                ", modelVersion='" + modelVersion + '\'' +
                '}';
    }

    private boolean sameRelationship(RelationshipMetadata left, RelationshipMetadata right) {
        return Objects.equals(left.getName(), right.getName())
            && Objects.equals(left.getSourceClass(), right.getSourceClass())
            && Objects.equals(left.getTargetClass(), right.getTargetClass())
            && Objects.equals(left.getSourceAttribute(), right.getSourceAttribute())
            && Objects.equals(left.getTargetRoleName(), right.getTargetRoleName())
            && Objects.equals(left.getSemanticKind(), right.getSemanticKind());
    }
}

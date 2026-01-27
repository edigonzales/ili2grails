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
    private String iliVersion;
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
    
    public ClassMetadata getClass(String name) {
        return classes.get(name);
    }
    
    public Collection<ClassMetadata> getAllClasses() {
        return classes.values();
    }
    
    public Collection<EnumMetadata> getAllEnums() {
        return enums.values();
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
    
    @Override
    public String toString() {
        return "ModelMetadata{" +
                "modelName='" + modelName + '\'' +
                ", schemaName='" + schemaName + '\'' +
                ", classes=" + classes.size() +
                ", enums=" + enums.size() +
                ", iliVersion='" + iliVersion + '\'' +
                '}';
    }
}

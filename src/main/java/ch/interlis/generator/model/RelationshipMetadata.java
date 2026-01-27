package ch.interlis.generator.model;

/**
 * Repräsentiert eine Beziehung zwischen zwei INTERLIS-Klassen.
 */
public class RelationshipMetadata {
    
    private String name;
    private String sourceClass;
    private String targetClass;
    private RelationType type;
    private String sourceAttribute;         // FK-Spalte in source
    private String targetAttribute;         // Referenzierte Spalte in target (meist PK)
    private Cardinality cardinality;
    private boolean mandatory;
    
    public enum RelationType {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY,
        ASSOCIATION                         // INTERLIS Association Class
    }
    
    public static class Cardinality {
        private int minSource;
        private int maxSource;
        private int minTarget;
        private int maxTarget;
        
        public Cardinality(int minSource, int maxSource, int minTarget, int maxTarget) {
            this.minSource = minSource;
            this.maxSource = maxSource;
            this.minTarget = minTarget;
            this.maxTarget = maxTarget;
        }
        
        public int getMinSource() {
            return minSource;
        }
        
        public int getMaxSource() {
            return maxSource;
        }
        
        public int getMinTarget() {
            return minTarget;
        }
        
        public int getMaxTarget() {
            return maxTarget;
        }
        
        @Override
        public String toString() {
            return "{" + minSource + ".." + (maxSource == -1 ? "*" : maxSource) +
                    " -> " + minTarget + ".." + (maxTarget == -1 ? "*" : maxTarget) + "}";
        }
    }
    
    public RelationshipMetadata(String name) {
        this.name = name;
    }
    
    // Getters and Setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getSourceClass() {
        return sourceClass;
    }
    
    public void setSourceClass(String sourceClass) {
        this.sourceClass = sourceClass;
    }
    
    public String getTargetClass() {
        return targetClass;
    }
    
    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }
    
    public RelationType getType() {
        return type;
    }
    
    public void setType(RelationType type) {
        this.type = type;
    }
    
    public String getSourceAttribute() {
        return sourceAttribute;
    }
    
    public void setSourceAttribute(String sourceAttribute) {
        this.sourceAttribute = sourceAttribute;
    }
    
    public String getTargetAttribute() {
        return targetAttribute;
    }
    
    public void setTargetAttribute(String targetAttribute) {
        this.targetAttribute = targetAttribute;
    }
    
    public Cardinality getCardinality() {
        return cardinality;
    }
    
    public void setCardinality(Cardinality cardinality) {
        this.cardinality = cardinality;
    }
    
    public boolean isMandatory() {
        return mandatory;
    }
    
    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }
    
    @Override
    public String toString() {
        return "RelationshipMetadata{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", sourceClass='" + sourceClass + '\'' +
                ", targetClass='" + targetClass + '\'' +
                ", cardinality=" + cardinality +
                '}';
    }
}

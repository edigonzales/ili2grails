package ch.interlis.generator.model;

import java.util.*;

/**
 * Repräsentiert eine INTERLIS-Enumeration.
 */
public class EnumMetadata {
    
    private String name;                    // Qualifizierter Name
    private String simpleName;
    private List<EnumValue> values = new ArrayList<>();
    private boolean isExtendable;
    private String baseEnum;                // Falls erweitert
    
    public static class EnumValue {
        private String iliCode;             // INTERLIS Code
        private String dispName;            // Display Name
        private int seq;                    // Reihenfolge
        private Map<String, String> labels = new HashMap<>();
        
        public EnumValue(String iliCode, int seq) {
            this.iliCode = iliCode;
            this.seq = seq;
        }
        
        public String getIliCode() {
            return iliCode;
        }
        
        public void setIliCode(String iliCode) {
            this.iliCode = iliCode;
        }
        
        public String getDispName() {
            return dispName;
        }
        
        public void setDispName(String dispName) {
            this.dispName = dispName;
        }
        
        public int getSeq() {
            return seq;
        }
        
        public void setSeq(int seq) {
            this.seq = seq;
        }
        
        public Map<String, String> getLabels() {
            return labels;
        }
        
        public void addLabel(String language, String label) {
            labels.put(language, label);
        }
        
        @Override
        public String toString() {
            return "EnumValue{" +
                    "iliCode='" + iliCode + '\'' +
                    ", dispName='" + dispName + '\'' +
                    ", seq=" + seq +
                    '}';
        }
    }
    
    public EnumMetadata(String name) {
        this.name = name;
        this.simpleName = extractSimpleName(name);
    }
    
    private String extractSimpleName(String qualifiedName) {
        if (qualifiedName == null) return null;
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }
    
    public void addValue(EnumValue value) {
        values.add(value);
    }
    
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
    
    public List<EnumValue> getValues() {
        return values;
    }
    
    public void setValues(List<EnumValue> values) {
        this.values = values;
    }
    
    public boolean isExtendable() {
        return isExtendable;
    }
    
    public void setExtendable(boolean extendable) {
        isExtendable = extendable;
    }
    
    public String getBaseEnum() {
        return baseEnum;
    }
    
    public void setBaseEnum(String baseEnum) {
        this.baseEnum = baseEnum;
    }
    
    @Override
    public String toString() {
        return "EnumMetadata{" +
                "name='" + name + '\'' +
                ", values=" + values.size() +
                ", isExtendable=" + isExtendable +
                '}';
    }
}

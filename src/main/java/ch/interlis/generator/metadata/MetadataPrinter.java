package ch.interlis.generator.metadata;

import ch.interlis.generator.model.*;

import java.io.PrintStream;

/**
 * Utility-Klasse zum Ausgeben von Metadaten in lesbarer Form.
 */
public class MetadataPrinter {
    
    private final PrintStream out;
    
    public MetadataPrinter() {
        this(System.out);
    }
    
    public MetadataPrinter(PrintStream out) {
        this.out = out;
    }
    
    /**
     * Gibt alle Metadaten aus.
     */
    public void print(ModelMetadata metadata) {
        printHeader(metadata);
        printClasses(metadata);
        printEnums(metadata);
    }
    
    private void printHeader(ModelMetadata metadata) {
        out.println("═══════════════════════════════════════════════════════════");
        out.println("INTERLIS Model Metadata");
        out.println("═══════════════════════════════════════════════════════════");
        out.println("Model Name:     " + metadata.getModelName());
        out.println("Schema:         " + metadata.getSchemaName());
        out.println("ILI Version:    " + metadata.getIliVersion());
        out.println("ili2db Version: " + metadata.getIli2dbVersion());
        out.println("Classes:        " + metadata.getClasses().size());
        out.println("Enumerations:   " + metadata.getEnums().size());
        out.println("═══════════════════════════════════════════════════════════");
        out.println();
    }
    
    private void printClasses(ModelMetadata metadata) {
        out.println("CLASSES:");
        out.println("───────────────────────────────────────────────────────────");
        
        for (ClassMetadata clazz : metadata.getAllClasses()) {
            printClass(clazz);
        }
    }
    
    private void printClass(ClassMetadata clazz) {
        out.println();
        out.printf("■ %s%n", clazz.getName());
        out.printf("  Simple Name:  %s%n", clazz.getSimpleName());
        out.printf("  Table:        %s%n", clazz.getTableName());
        out.printf("  Kind:         %s%n", clazz.getKind());
        out.printf("  Abstract:     %s%n", clazz.isAbstract());
        
        if (clazz.getBaseClass() != null) {
            out.printf("  Extends:      %s%n", clazz.getBaseClass());
        }
        
        if (clazz.getDocumentation() != null && !clazz.getDocumentation().isEmpty()) {
            out.printf("  Doc:          %s%n", truncate(clazz.getDocumentation(), 60));
        }
        
        // Attribute
        if (!clazz.getAllAttributes().isEmpty()) {
            out.println("  Attributes:");
            for (AttributeMetadata attr : clazz.getAllAttributes()) {
                printAttribute(attr, "    ");
            }
        }
        
        // Beziehungen
        if (!clazz.getRelationships().isEmpty()) {
            out.println("  Relationships:");
            for (RelationshipMetadata rel : clazz.getRelationships()) {
                printRelationship(rel, "    ");
            }
        }
    }
    
    private void printAttribute(AttributeMetadata attr, String indent) {
        String flags = buildAttributeFlags(attr);
        
        out.printf("%s◦ %-20s : %-15s [%-12s] %s%n",
            indent,
            attr.getName(),
            attr.getJavaType(),
            attr.getColumnName(),
            flags
        );
        
        if (attr.getDocumentation() != null && !attr.getDocumentation().isEmpty()) {
            out.printf("%s  → %s%n", indent, truncate(attr.getDocumentation(), 55));
        }
        
        if (attr.getEnumType() != null) {
            out.printf("%s  → Enum: %s%n", indent, attr.getEnumType());
        }
        
        if (attr.getUnit() != null) {
            out.printf("%s  → Unit: %s%n", indent, attr.getUnit());
        }
        
        if (attr.getMinValue() != null || attr.getMaxValue() != null) {
            out.printf("%s  → Range: [%s .. %s]%n", indent, 
                attr.getMinValue() != null ? attr.getMinValue() : "-∞",
                attr.getMaxValue() != null ? attr.getMaxValue() : "+∞"
            );
        }
    }
    
    private String buildAttributeFlags(AttributeMetadata attr) {
        StringBuilder flags = new StringBuilder();
        
        if (attr.isPrimaryKey()) flags.append("PK ");
        if (attr.isForeignKey()) flags.append("FK ");
        if (attr.isMandatory()) flags.append("NOT NULL ");
        if (attr.isGeometry()) flags.append("GEOMETRY ");
        if (attr.getMaxLength() != null) flags.append("(").append(attr.getMaxLength()).append(") ");
        
        return flags.toString().trim();
    }
    
    private void printRelationship(RelationshipMetadata rel, String indent) {
        out.printf("%s→ %s [%s]%n", indent, rel.getTargetClass(), rel.getType());
        out.printf("%s  via: %s → %s%n", indent, rel.getSourceAttribute(), rel.getTargetAttribute());
        if (rel.getCardinality() != null) {
            out.printf("%s  cardinality: %s%n", indent, rel.getCardinality());
        }
    }
    
    private void printEnums(ModelMetadata metadata) {
        if (metadata.getEnums().isEmpty()) {
            return;
        }
        
        out.println();
        out.println("ENUMERATIONS:");
        out.println("───────────────────────────────────────────────────────────");
        
        for (EnumMetadata enumMetadata : metadata.getAllEnums()) {
            printEnum(enumMetadata);
        }
    }
    
    private void printEnum(EnumMetadata enumMetadata) {
        out.println();
        out.printf("■ %s%n", enumMetadata.getName());
        out.printf("  Simple Name:  %s%n", enumMetadata.getSimpleName());
        out.printf("  Extensible:   %s%n", enumMetadata.isExtendable());
        
        if (enumMetadata.getBaseEnum() != null) {
            out.printf("  Extends:      %s%n", enumMetadata.getBaseEnum());
        }
        
        out.println("  Values:");
        for (EnumMetadata.EnumValue value : enumMetadata.getValues()) {
            out.printf("    %2d. %s%n", value.getSeq(), value.getIliCode());
            if (value.getDispName() != null) {
                out.printf("        → %s%n", value.getDispName());
            }
        }
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}

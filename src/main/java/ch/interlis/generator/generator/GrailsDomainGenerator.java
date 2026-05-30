package ch.interlis.generator.generator;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generiert Grails Domain-Klassen inkl. Constraints und Mapping.
 */
public class GrailsDomainGenerator {

    public void generate(ModelMetadata metadata, GenerationConfig config) throws IOException {
        generate(metadata, config, TargetNameRegistry.forMetadata(metadata, config));
    }

    public void generate(ModelMetadata metadata,
                         GenerationConfig config,
                         TargetNameRegistry registry) throws IOException {
        Path baseDir = config.getOutputDir()
            .resolve("grails-app/domain")
            .resolve(NameUtils.packageToPath(config.getDomainPackage()));
        Files.createDirectories(baseDir);

        Map<String, List<RelationshipMetadata>> incomingRelationships = indexIncomingRelations(metadata);

        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            if (classMetadata.isAbstract()) {
                continue;
            }
            String content = renderDomain(classMetadata, metadata, config, registry, incomingRelationships);
            Path target = baseDir.resolve(registry.className(classMetadata) + ".groovy");
            Files.writeString(target, content, StandardCharsets.UTF_8);
        }
    }

    private String renderDomain(ClassMetadata classMetadata,
                                ModelMetadata metadata,
                                GenerationConfig config,
                                TargetNameRegistry registry,
                                Map<String, List<RelationshipMetadata>> incomingRelations) {
        String className = registry.className(classMetadata);
        String packageName = config.getDomainPackage();

        Set<String> imports = new LinkedHashSet<>();
        List<String> properties = new ArrayList<>();
        Map<String, String> columnMappings = new LinkedHashMap<>();
        Map<String, AttributeMetadata> geometryAttributes = new LinkedHashMap<>();
        boolean hasIdAttribute = false;
        boolean hasPrimaryKeyTId = false;
        boolean hasTIdColumn = false;

        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if ("id".equalsIgnoreCase(attr.getName())) {
                hasIdAttribute = true;
            }
            if (isTIdColumn(attr)) {
                hasTIdColumn = true;
                if (attr.isPrimaryKey()) {
                    hasPrimaryKeyTId = true;
                }
            }
            if (attr.isPrimaryKey()) {
                continue;
            }
            String propertyName = registry.propertyName(classMetadata, attr);
            String type = resolveType(attr, metadata, config, registry, imports);
            properties.add("    " + type + " " + propertyName);
            if (attr.isGeometry()) {
                geometryAttributes.put(propertyName, attr);
            }

            if (attr.getColumnName() != null
                && (attr.isForeignKey() || !attr.getColumnName().equalsIgnoreCase(propertyName))) {
                columnMappings.put(propertyName, attr.getColumnName());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append("\n\n");
        if (!imports.isEmpty()) {
            imports.forEach(imp -> sb.append("import ").append(imp).append("\n"));
            sb.append("\n");
        }
        sb.append("class ").append(className).append(" {\n\n");

        for (String property : properties) {
            sb.append(property).append("\n");
        }

        if (!geometryAttributes.isEmpty()) {
            sb.append("\n    static final Map<String, Map<String, Object>> geometryMeta = [\n");
            String geometryMetaBlock = geometryAttributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "        " + entry.getKey() + ": [srid: "
                    + renderSrid(entry.getValue())
                    + ", kind: '" + renderGeometryKind(entry.getValue()) + "']")
                .collect(Collectors.joining(",\n"));
            sb.append(geometryMetaBlock).append("\n");
            sb.append("    ]\n");
        }

        List<RelationshipMetadata> ownedBy = incomingRelations.getOrDefault(classMetadata.getName(), List.of());
        if (!ownedBy.isEmpty()) {
            String hasManyBlock = ownedBy.stream()
                .sorted(relationshipComparator(registry))
                .map(relationship -> {
                    String propName = registry.collectionPropertyName(relationship);
                    ClassMetadata source = metadata.getClass(relationship.getSourceClass());
                    String sourceClassName = source != null
                        ? registry.className(source)
                        : registry.className(relationship.getSourceClass());
                    return propName + ": " + sourceClassName;
                })
                .collect(Collectors.joining(", "));
            sb.append("\n    static hasMany = [").append(hasManyBlock).append("]\n");
        }

        Map<String, String> belongsTo = resolveBelongsTo(classMetadata, metadata, registry);
        if (!belongsTo.isEmpty()) {
            String belongsToBlock = belongsTo.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "));
            sb.append("\n    static belongsTo = [").append(belongsToBlock).append("]\n");
        }

        sb.append("\n    static mapping = {\n");
        if (classMetadata.getTableName() != null) {
            sb.append("        table '").append(classMetadata.getTableName()).append("'\n");
        }
        boolean requiresTIdMapping = hasPrimaryKeyTId || (!hasIdAttribute && hasTIdColumn);
        if (requiresTIdMapping) {
            sb.append("        id column: 't_id', generator: 'identity'\n");
        }
        
        sb.append("        version false\n");
        
        if (!columnMappings.isEmpty()) {
            sb.append("        columns {\n");
            columnMappings.forEach((propertyName, columnName) ->
                sb.append("            ").append(propertyName).append(" column: '")
                    .append(columnName).append("'\n")
            );
            sb.append("        }\n");
        }
        sb.append("    }\n");

        sb.append("\n    static constraints = {\n");
        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if (attr.isPrimaryKey()) {
                continue;
            }
            String propertyName = registry.propertyName(classMetadata, attr);
            List<String> constraintParts = new ArrayList<>();
            if (!attr.isMandatory()) {
                constraintParts.add("nullable: true");
            }
            if (attr.getMaxLength() != null) {
                constraintParts.add("maxSize: " + attr.getMaxLength());
            }
            if (isNumeric(attr.getMinValue())) {
                constraintParts.add("min: " + attr.getMinValue());
            }
            if (isNumeric(attr.getMaxValue())) {
                constraintParts.add("max: " + attr.getMaxValue());
            }
            if (!constraintParts.isEmpty()) {
                sb.append("        ")
                    .append(propertyName)
                    .append(" ")
                    .append(String.join(", ", constraintParts))
                    .append("\n");
            }
        }
        sb.append("    }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private Map<String, List<RelationshipMetadata>> indexIncomingRelations(ModelMetadata metadata) {
        Map<String, List<RelationshipMetadata>> incoming = new LinkedHashMap<>();
        for (RelationshipMetadata rel : metadata.getAllRelationships()) {
            if (rel.getType() == RelationshipMetadata.RelationType.MANY_TO_ONE
                && isGeneratedDomainClass(metadata, rel.getSourceClass())
                && isGeneratedDomainClass(metadata, rel.getTargetClass())) {
                incoming.computeIfAbsent(rel.getTargetClass(), key -> new ArrayList<>())
                    .add(rel);
            }
        }
        return incoming;
    }

    private String resolveType(AttributeMetadata attr,
                               ModelMetadata metadata,
                               GenerationConfig config,
                               TargetNameRegistry registry,
                               Set<String> imports) {
        if (attr.getEnumType() != null) {
            EnumMetadata enumMetadata = metadata.getEnums().get(attr.getEnumType());
            if (enumMetadata != null) {
                String enumName = registry.enumName(enumMetadata);
                imports.add(config.getEnumPackage() + "." + enumName);
                return enumName;
            }
        }

        if (attr.isForeignKey() && attr.getReferencedClass() != null) {
            ClassMetadata referenced = metadata.getClass(attr.getReferencedClass());
            if (referenced != null && !referenced.isAbstract()) {
                return registry.className(referenced);
            }
        }

        String javaType = attr.getJavaType();
        String simpleType = NameUtils.simpleType(javaType);
        if (javaType != null && javaType.contains(".")) {
            String packageName = javaType.substring(0, javaType.lastIndexOf('.'));
            if (!packageName.startsWith("java.lang")) {
                imports.add(javaType);
            }
        }
        return simpleType;
    }

    private boolean isNumeric(String value) {
        if (value == null) {
            return false;
        }
        return value.matches("-?\\d+(\\.\\d+)?");
    }

    private boolean isTIdColumn(AttributeMetadata attr) {
        String columnName = attr.getColumnName();
        if (columnName != null && columnName.equalsIgnoreCase("t_id")) {
            return true;
        }
        return "t_id".equalsIgnoreCase(attr.getName());
    }

    private String renderSrid(AttributeMetadata attributeMetadata) {
        Integer geometrySrid = attributeMetadata.getGeometrySrid();
        return geometrySrid == null ? "null" : Integer.toString(geometrySrid);
    }

    private String renderGeometryKind(AttributeMetadata attributeMetadata) {
        String geometryKind = attributeMetadata.getGeometryKind();
        if (geometryKind == null || geometryKind.isBlank()) {
            return "GEOMETRY";
        }
        return geometryKind.toUpperCase();
    }

    private Map<String, String> resolveBelongsTo(ClassMetadata classMetadata,
                                                 ModelMetadata metadata,
                                                 TargetNameRegistry registry) {
        Map<String, String> belongsTo = new LinkedHashMap<>();
        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if (!attr.isForeignKey() || attr.getReferencedClass() == null) {
                continue;
            }
            if (!isGeneratedDomainClass(metadata, attr.getReferencedClass())) {
                continue;
            }
            String propertyName = registry.propertyName(classMetadata, attr);
            if (propertyName == null || propertyName.isBlank()) {
                continue;
            }
            ClassMetadata referenced = metadata.getClass(attr.getReferencedClass());
            String targetName = referenced != null ? registry.className(referenced)
                : registry.className(attr.getReferencedClass());
            belongsTo.put(propertyName, targetName);
        }
        return belongsTo;
    }

    private boolean isGeneratedDomainClass(ModelMetadata metadata, String className) {
        if (className == null) {
            return false;
        }
        ClassMetadata classMetadata = metadata.getClass(className);
        return classMetadata != null && !classMetadata.isAbstract();
    }

    private Comparator<RelationshipMetadata> relationshipComparator(TargetNameRegistry registry) {
        return Comparator
            .comparing((RelationshipMetadata relationship) -> registry.collectionPropertyName(relationship))
            .thenComparing(RelationshipMetadata::getSourceClass, Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMetadata::getName, Comparator.nullsLast(String::compareTo));
    }
}

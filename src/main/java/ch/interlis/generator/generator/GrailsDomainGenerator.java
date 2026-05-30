package ch.interlis.generator.generator;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        for (ClassMetadata classMetadata : mapper.generatedClasses()) {
            GrailsRelationshipMapper.DomainMapping mapping = mapper.map(classMetadata);
            String content = renderDomain(classMetadata, mapping, metadata, config, registry);
            Path target = baseDir.resolve(registry.className(classMetadata) + ".groovy");
            Files.writeString(target, content, StandardCharsets.UTF_8);
        }
    }

    private String renderDomain(ClassMetadata classMetadata,
                                GrailsRelationshipMapper.DomainMapping mapping,
                                ModelMetadata metadata,
                                GenerationConfig config,
                                TargetNameRegistry registry) {
        String className = registry.className(classMetadata);
        String packageName = config.getDomainPackage();

        Set<String> imports = new LinkedHashSet<>();
        List<String> properties = new ArrayList<>();
        Map<String, String> columnMappings = new LinkedHashMap<>();
        Map<String, GrailsRelationshipMapper.DomainProperty> geometryAttributes = new LinkedHashMap<>();
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
        }

        for (GrailsRelationshipMapper.DomainProperty property : mapping.properties()) {
            addImport(property, metadata, config, registry, imports);
            properties.add("    " + property.type() + " " + property.name());
            if (property.geometry()) {
                geometryAttributes.put(property.name(), property);
            }

            AttributeMetadata attribute = property.attribute();
            if (property.columnName() != null
                && (attribute == null
                || attribute.isForeignKey()
                || !property.columnName().equalsIgnoreCase(property.name()))) {
                columnMappings.put(property.name(), property.columnName());
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

        if (!mapping.collections().isEmpty()) {
            String hasManyBlock = mapping.collections().stream()
                .map(collection -> collection.name() + ": " + collection.type())
                .collect(Collectors.joining(", "));
            sb.append("\n    static hasMany = [").append(hasManyBlock).append("]\n");
        }

        if (!mapping.belongsTo().isEmpty()) {
            String belongsToBlock = mapping.belongsTo().stream()
                .map(ownership -> ownership.name() + ": " + ownership.type())
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
        for (GrailsRelationshipMapper.DomainProperty property : mapping.properties()) {
            List<String> constraintParts = new ArrayList<>();
            if (property.nullable()) {
                constraintParts.add("nullable: true");
            }
            if (property.maxLength() != null) {
                constraintParts.add("maxSize: " + property.maxLength());
            }
            if (isNumeric(property.minValue())) {
                constraintParts.add("min: " + property.minValue());
            }
            if (isNumeric(property.maxValue())) {
                constraintParts.add("max: " + property.maxValue());
            }
            if (!constraintParts.isEmpty()) {
                sb.append("        ")
                    .append(property.name())
                    .append(" ")
                    .append(String.join(", ", constraintParts))
                    .append("\n");
            }
        }
        sb.append("    }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private void addImport(GrailsRelationshipMapper.DomainProperty property,
                           ModelMetadata metadata,
                           GenerationConfig config,
                           TargetNameRegistry registry,
                           Set<String> imports) {
        AttributeMetadata attribute = property.attribute();
        if (attribute == null) {
            return;
        }
        if (attribute.getEnumType() != null) {
            EnumMetadata enumMetadata = metadata.getEnums().get(attribute.getEnumType());
            if (enumMetadata != null && property.type().equals(registry.enumName(enumMetadata))) {
                imports.add(config.getEnumPackage() + "." + property.type());
                return;
            }
        }

        String javaType = attribute.getJavaType();
        if (javaType != null
            && javaType.contains(".")
            && property.type().equals(NameUtils.simpleType(javaType))) {
            String packageName = javaType.substring(0, javaType.lastIndexOf('.'));
            if (!packageName.startsWith("java.lang")) {
                imports.add(javaType);
            }
        }
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

    private String renderSrid(GrailsRelationshipMapper.DomainProperty property) {
        Integer geometrySrid = property.geometrySrid();
        return geometrySrid == null ? "null" : Integer.toString(geometrySrid);
    }

    private String renderGeometryKind(GrailsRelationshipMapper.DomainProperty property) {
        String geometryKind = property.geometryKind();
        if (geometryKind == null || geometryKind.isBlank()) {
            return "GEOMETRY";
        }
        return geometryKind.toUpperCase();
    }
}

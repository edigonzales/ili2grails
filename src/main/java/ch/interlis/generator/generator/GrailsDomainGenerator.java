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
        Path baseDir = config.getOutputDir()
            .resolve("grails-app/domain")
            .resolve(NameUtils.packageToPath(config.getDomainPackage()));
        Files.createDirectories(baseDir);

        Map<String, List<ClassMetadata>> incomingRelationships = indexIncomingRelations(metadata);

        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            if (classMetadata.isAbstract()) {
                continue;
            }
            String content = renderDomain(classMetadata, metadata, config, incomingRelationships);
            Path target = baseDir.resolve(classMetadata.getSimpleName() + ".groovy");
            Files.writeString(target, content, StandardCharsets.UTF_8);
        }
    }

    private String renderDomain(ClassMetadata classMetadata,
                                ModelMetadata metadata,
                                GenerationConfig config,
                                Map<String, List<ClassMetadata>> incomingRelations) {
        String className = classMetadata.getSimpleName();
        String packageName = config.getDomainPackage();

        Set<String> imports = new LinkedHashSet<>();
        List<String> properties = new ArrayList<>();
        Map<String, String> columnMappings = new LinkedHashMap<>();

        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if (attr.isPrimaryKey()) {
                continue;
            }
            String propertyName = NameUtils.toLowerCamel(attr.getName());
            String type = resolveType(attr, metadata, config, imports);
            properties.add("    " + type + " " + propertyName);

            if (attr.getColumnName() != null && !attr.getColumnName().equalsIgnoreCase(propertyName)) {
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

        List<ClassMetadata> ownedBy = incomingRelations.getOrDefault(classMetadata.getName(), List.of());
        if (!ownedBy.isEmpty()) {
            String hasManyBlock = ownedBy.stream()
                .sorted(Comparator.comparing(ClassMetadata::getSimpleName))
                .map(source -> {
                    String propName = NameUtils.pluralize(NameUtils.toLowerCamel(source.getSimpleName()));
                    return propName + ": " + source.getSimpleName();
                })
                .collect(Collectors.joining(", "));
            sb.append("\n    static hasMany = [").append(hasManyBlock).append("]\n");
        }

        sb.append("\n    static mapping = {\n");
        if (classMetadata.getTableName() != null) {
            sb.append("        table '").append(classMetadata.getTableName()).append("'\n");
        }
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
            String propertyName = NameUtils.toLowerCamel(attr.getName());
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

    private Map<String, List<ClassMetadata>> indexIncomingRelations(ModelMetadata metadata) {
        Map<String, List<ClassMetadata>> incoming = new LinkedHashMap<>();
        for (ClassMetadata source : metadata.getAllClasses()) {
            for (RelationshipMetadata rel : source.getRelationships()) {
                if (rel.getType() == RelationshipMetadata.RelationType.MANY_TO_ONE
                    && rel.getTargetClass() != null) {
                    incoming.computeIfAbsent(rel.getTargetClass(), key -> new ArrayList<>())
                        .add(source);
                }
            }
        }
        return incoming;
    }

    private String resolveType(AttributeMetadata attr,
                               ModelMetadata metadata,
                               GenerationConfig config,
                               Set<String> imports) {
        if (attr.getEnumType() != null) {
            EnumMetadata enumMetadata = metadata.getEnums().get(attr.getEnumType());
            if (enumMetadata != null) {
                imports.add(config.getEnumPackage() + "." + enumMetadata.getSimpleName());
                return enumMetadata.getSimpleName();
            }
        }

        if (attr.isForeignKey() && attr.getReferencedClass() != null) {
            ClassMetadata referenced = metadata.getClass(attr.getReferencedClass());
            if (referenced != null) {
                return referenced.getSimpleName();
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
}

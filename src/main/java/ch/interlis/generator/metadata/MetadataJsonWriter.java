package ch.interlis.generator.metadata;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Schreibt eine deterministische JSON-Repräsentation der framework-agnostischen Metadaten-IR.
 */
public class MetadataJsonWriter {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public void write(ModelMetadata metadata, Path outputPath) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, toJson(metadata), StandardCharsets.UTF_8);
    }

    public String toJson(ModelMetadata metadata) throws IOException {
        return JSON_MAPPER.writeValueAsString(toDto(metadata)) + System.lineSeparator();
    }

    private Map<String, Object> toDto(ModelMetadata metadata) {
        Map<String, Object> dto = new LinkedHashMap<>();
        putIfNotNull(dto, "modelName", metadata.getModelName());
        putIfNotNull(dto, "modelVersion", metadata.getModelVersion());
        putIfNotNull(dto, "iliVersion", metadata.getIliVersion());
        putIfNotNull(dto, "schemaName", metadata.getSchemaName());
        putIfNotNull(dto, "ili2dbVersion", metadata.getIli2dbVersion());
        dto.put("settings", new TreeMap<>(metadata.getSettings()));
        dto.put("classes", metadata.getAllClasses().stream()
            .sorted(Comparator.comparing(ClassMetadata::getName, Comparator.nullsLast(String::compareTo)))
            .map(this::classDto)
            .toList());
        dto.put("enums", metadata.getAllEnums().stream()
            .sorted(Comparator.comparing(EnumMetadata::getName, Comparator.nullsLast(String::compareTo)))
            .map(this::enumDto)
            .toList());
        dto.put("relationships", metadata.getAllRelationships().stream()
            .sorted(relationshipComparator())
            .map(this::relationshipDto)
            .toList());
        return dto;
    }

    private Map<String, Object> classDto(ClassMetadata classMetadata) {
        Map<String, Object> dto = new LinkedHashMap<>();
        putIfNotNull(dto, "name", classMetadata.getName());
        putIfNotNull(dto, "simpleName", classMetadata.getSimpleName());
        putIfNotNull(dto, "topicName", classMetadata.getTopicName());
        putIfNotNull(dto, "tableName", classMetadata.getTableName());
        putIfNotNull(dto, "sqlName", classMetadata.getSqlName());
        putIfNotNull(dto, "documentation", classMetadata.getDocumentation());
        dto.put("abstract", classMetadata.isAbstract());
        putIfNotNull(dto, "baseClass", classMetadata.getBaseClass());
        putIfNotNull(dto, "kind", enumName(classMetadata.getKind()));
        putIfNotNull(dto, "inheritanceStrategy", classMetadata.getInheritanceStrategy());
        dto.put("labels", new TreeMap<>(classMetadata.getLabels()));
        dto.put("attributes", classMetadata.getAllAttributes().stream()
            .sorted(attributeComparator())
            .map(this::attributeDto)
            .toList());
        return dto;
    }

    private Map<String, Object> attributeDto(AttributeMetadata attribute) {
        Map<String, Object> dto = new LinkedHashMap<>();
        putIfNotNull(dto, "name", attribute.getName());
        putIfNotNull(dto, "qualifiedName", attribute.getQualifiedName());
        putIfNotNull(dto, "columnName", attribute.getColumnName());
        putIfNotNull(dto, "sqlName", attribute.getSqlName());
        putIfNotNull(dto, "iliType", attribute.getIliType());
        putIfNotNull(dto, "domainName", attribute.getDomainName());
        putIfNotNull(dto, "javaType", attribute.getJavaType());
        putIfNotNull(dto, "dbType", attribute.getDbType());
        dto.put("mandatory", attribute.isMandatory());
        dto.put("primaryKey", attribute.isPrimaryKey());
        dto.put("foreignKey", attribute.isForeignKey());
        dto.put("geometry", attribute.isGeometry());
        putIfNotNull(dto, "geometrySrid", attribute.getGeometrySrid());
        putIfNotNull(dto, "geometryKind", attribute.getGeometryKind());
        putIfNotNull(dto, "documentation", attribute.getDocumentation());
        putIfNotNull(dto, "maxLength", attribute.getMaxLength());
        putIfNotNull(dto, "minValue", attribute.getMinValue());
        putIfNotNull(dto, "maxValue", attribute.getMaxValue());
        putIfNotNull(dto, "cardinalityMin", attribute.getCardinalityMin());
        putIfNotNull(dto, "cardinalityMax", attribute.getCardinalityMax());
        dto.put("ordered", attribute.isOrdered());
        putIfNotNull(dto, "enumType", attribute.getEnumType());
        dto.put("enumValues", enumValueDtos(attribute.getEnumValues()));
        putIfNotNull(dto, "unit", attribute.getUnit());
        putIfNotNull(dto, "referencedClass", attribute.getReferencedClass());
        putIfNotNull(dto, "referencedAttribute", attribute.getReferencedAttribute());
        dto.put("labels", new TreeMap<>(attribute.getLabels()));
        return dto;
    }

    private Map<String, Object> enumDto(EnumMetadata enumMetadata) {
        Map<String, Object> dto = new LinkedHashMap<>();
        putIfNotNull(dto, "name", enumMetadata.getName());
        putIfNotNull(dto, "simpleName", enumMetadata.getSimpleName());
        dto.put("extendable", enumMetadata.isExtendable());
        putIfNotNull(dto, "baseEnum", enumMetadata.getBaseEnum());
        dto.put("values", enumValueDtos(enumMetadata.getValues()));
        return dto;
    }

    private List<Map<String, Object>> enumValueDtos(List<EnumMetadata.EnumValue> values) {
        return values.stream()
            .sorted(Comparator.comparingInt(EnumMetadata.EnumValue::getSeq)
                .thenComparing(EnumMetadata.EnumValue::getIliCode, Comparator.nullsLast(String::compareTo)))
            .map(value -> {
                Map<String, Object> dto = new LinkedHashMap<>();
                putIfNotNull(dto, "iliCode", value.getIliCode());
                putIfNotNull(dto, "dispName", value.getDispName());
                dto.put("seq", value.getSeq());
                dto.put("labels", new TreeMap<>(value.getLabels()));
                return dto;
            })
            .toList();
    }

    private Map<String, Object> relationshipDto(RelationshipMetadata relationship) {
        Map<String, Object> dto = new LinkedHashMap<>();
        putIfNotNull(dto, "name", relationship.getName());
        putIfNotNull(dto, "sourceClass", relationship.getSourceClass());
        putIfNotNull(dto, "targetClass", relationship.getTargetClass());
        putIfNotNull(dto, "type", enumName(relationship.getType()));
        putIfNotNull(dto, "semanticKind", enumName(relationship.getSemanticKind()));
        putIfNotNull(dto, "sourceAttribute", relationship.getSourceAttribute());
        putIfNotNull(dto, "targetAttribute", relationship.getTargetAttribute());
        putIfNotNull(dto, "associationName", relationship.getAssociationName());
        putIfNotNull(dto, "sourceRoleName", relationship.getSourceRoleName());
        putIfNotNull(dto, "targetRoleName", relationship.getTargetRoleName());
        putIfNotNull(dto, "oppositeRoleName", relationship.getOppositeRoleName());
        if (relationship.getCardinality() != null) {
            dto.put("cardinality", cardinalityDto(relationship.getCardinality()));
        }
        dto.put("mandatory", relationship.isMandatory());
        dto.put("ordered", relationship.isOrdered());
        dto.put("external", relationship.isExternal());
        dto.put("composition", relationship.isComposition());
        putIfNotNull(dto, "source", relationship.getSource());
        return dto;
    }

    private Map<String, Object> cardinalityDto(RelationshipMetadata.Cardinality cardinality) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("minSource", cardinality.getMinSource());
        dto.put("maxSource", cardinality.getMaxSource());
        dto.put("minTarget", cardinality.getMinTarget());
        dto.put("maxTarget", cardinality.getMaxTarget());
        return dto;
    }

    private Comparator<AttributeMetadata> attributeComparator() {
        return Comparator
            .comparing(AttributeMetadata::getQualifiedName, Comparator.nullsLast(String::compareTo))
            .thenComparing(AttributeMetadata::getName, Comparator.nullsLast(String::compareTo))
            .thenComparing(AttributeMetadata::getColumnName, Comparator.nullsLast(String::compareTo));
    }

    private Comparator<RelationshipMetadata> relationshipComparator() {
        return Comparator
            .comparing(RelationshipMetadata::getSourceClass, Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMetadata::getTargetRoleName, Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMetadata::getTargetClass, Comparator.nullsLast(String::compareTo))
            .thenComparing(RelationshipMetadata::getName, Comparator.nullsLast(String::compareTo));
    }

    private String enumName(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    private void putIfNotNull(Map<String, Object> dto, String key, Object value) {
        if (value != null) {
            dto.put(key, value);
        }
    }
}

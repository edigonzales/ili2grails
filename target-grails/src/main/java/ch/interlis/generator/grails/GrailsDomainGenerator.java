package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.AttributeConstraints;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.grails.project.plan.PlannedProjectFile;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generiert Grails Domain-Klassen inkl. Constraints und Mapping.
 */
public class GrailsDomainGenerator {

    private static final List<String> DISPLAY_FIELD_PREFERENCES = List.of(
        "name",
        "bezeichnung",
        "label",
        "title",
        "code",
        "ident"
    );

    public void generate(ModelMetadata metadata, GenerationConfig config) throws IOException {
        generate(metadata, config, TargetNameRegistry.forMetadata(metadata, config));
    }

    public void generate(ModelMetadata metadata,
                         GenerationConfig config,
                         TargetNameRegistry registry) throws IOException {
        generate(metadata, config, registry, GrailsRelationshipMapper.forMetadata(metadata, config, registry));
    }

    public void generate(ModelMetadata metadata,
                         GenerationConfig config,
                         TargetNameRegistry registry,
                         GrailsRelationshipMapper mapper) throws IOException {
        generate(
            metadata,
            config,
            registry,
            mapper,
            GrailsInverseRelationshipPlanner.forMetadata(metadata, config, registry, mapper)
        );
    }

    /**
     * Reine Planungsfunktion (Spezifikation §41.1): kein Write.
     */
    public List<PlannedProjectFile> plan(ModelMetadata metadata,
                                         GenerationConfig config,
                                         TargetNameRegistry registry,
                                         GrailsRelationshipMapper mapper,
                                         GrailsInverseRelationshipPlanner inverseRelationshipPlanner) {
        List<PlannedProjectFile> planned = new ArrayList<>();
        Path baseDir = Path.of("grails-app/domain")
            .resolve(NameUtils.packageToPath(config.getDomainPackage()));
        for (ClassMetadata classMetadata : mapper.generatedClasses()) {
            GrailsRelationshipMapper.DomainMapping mapping = mapper.map(classMetadata);
            String content = renderDomain(
                classMetadata,
                mapping,
                inverseRelationshipPlanner.plansForOwner(classMetadata.getName()),
                metadata,
                config,
                registry
            );
            planned.add(PlannedProjectFile.text(
                baseDir.resolve(registry.className(classMetadata) + ".groovy"),
                ch.interlis.generator.grails.project.GrailsProjectFileOwner.GENERATOR_MANAGED,
                content,
                "generated domain " + registry.className(classMetadata)));
        }
        return planned;
    }

    public void generate(ModelMetadata metadata,
                         GenerationConfig config,
                         TargetNameRegistry registry,
                         GrailsRelationshipMapper mapper,
                         GrailsInverseRelationshipPlanner inverseRelationshipPlanner) throws IOException {
        Path baseDir = config.getOutputDir()
            .resolve("grails-app/domain")
            .resolve(NameUtils.packageToPath(config.getDomainPackage()));
        for (PlannedProjectFile planned : plan(metadata, config, registry, mapper,
            inverseRelationshipPlanner)) {
            Path target = baseDir.resolve(planned.relativePath().getFileName());
            Files.createDirectories(target.getParent());
            Files.write(target, planned.content());
        }
    }

    private String renderDomain(ClassMetadata classMetadata,
                                GrailsRelationshipMapper.DomainMapping mapping,
                                List<GrailsInverseRelationshipPlan> inverseRelationshipPlans,
                                ModelMetadata metadata,
                                GenerationConfig config,
                                TargetNameRegistry registry) {
        String className = registry.className(classMetadata);
        String packageName = config.getDomainPackage();

        Set<String> imports = new LinkedHashSet<>();
        List<String> properties = new ArrayList<>();
        Map<String, String> columnMappings = new LinkedHashMap<>();
        Map<String, GrailsRelationshipMapper.DomainProperty> geometryAttributes = new LinkedHashMap<>();
        Map<String, AttributeMetadata> fieldMetadata = new LinkedHashMap<>();
        List<String> displayFields = displayFields(mapping.properties());
        List<String> searchFields = searchFields(mapping.properties());
        Map<String, GrailsRelationshipMapper.DomainProperty> relationshipMetadata = relationshipMetadata(mapping.properties());
        boolean hasIdAttribute = false;
        boolean hasPrimaryKeyTId = false;
        boolean hasTIdColumn = false;
        boolean hasVersionColumn = false;

        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if ("id".equalsIgnoreCase(attr.getName())) {
                hasIdAttribute = true;
            }
            if (isVersionColumn(attr)) {
                hasVersionColumn = true;
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
            if (attribute != null) {
                fieldMetadata.put(property.name(), attribute);
            }
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
                .map(entry -> "        " + entry.getKey() + ": " + renderGeometryMeta(entry.getValue()))
                .collect(Collectors.joining(",\n"));
            sb.append(geometryMetaBlock).append("\n");
            sb.append("    ]\n");
        }

        if (!fieldMetadata.isEmpty()) {
            sb.append("\n    static final Map<String, Map<String, Object>> interlisFieldMeta = [\n");
            String fieldMetaBlock = fieldMetadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "        " + entry.getKey() + ": " + renderFieldMeta(entry.getValue()))
                .collect(Collectors.joining(",\n"));
            sb.append(fieldMetaBlock).append("\n");
            sb.append("    ]\n");
        }

        if (!displayFields.isEmpty() || !searchFields.isEmpty()) {
            sb.append("\n    static final Map<String, Object> interlisDisplayMeta = [\n");
            sb.append("        displayFields: ").append(renderStringList(displayFields)).append(",\n");
            sb.append("        searchFields: ").append(renderStringList(searchFields)).append("\n");
            sb.append("    ]\n");
        }

        if (!relationshipMetadata.isEmpty()) {
            sb.append("\n    static final Map<String, Map<String, Object>> interlisRelationshipMeta = [\n");
            String relationshipMetaBlock = relationshipMetadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "        " + entry.getKey() + ": " + renderRelationshipMeta(entry.getValue()))
                .collect(Collectors.joining(",\n"));
            sb.append(relationshipMetaBlock).append("\n");
            sb.append("    ]\n");
        }

        List<GrailsInverseRelationshipPlan> visibleInverseRelationships = inverseRelationshipPlans.stream()
            .filter(GrailsInverseRelationshipPlan::visible)
            .toList();
        if (!visibleInverseRelationships.isEmpty()) {
            sb.append("\n    static final Map<String, Map<String, Object>> interlisInverseRelationshipMeta = [\n");
            String inverseRelationshipMetaBlock = visibleInverseRelationships.stream()
                .sorted(java.util.Comparator.comparing(GrailsInverseRelationshipPlan::collectionPropertyName))
                .map(plan -> "        " + plan.collectionPropertyName() + ": "
                    + renderInverseRelationshipMeta(plan))
                .collect(Collectors.joining(",\n"));
            sb.append(inverseRelationshipMetaBlock).append("\n");
            sb.append("    ]\n");
        }

        if (!mapping.collections().isEmpty()) {
            renderHasMany(sb, mapping.collections());
            renderMappedBy(sb, mapping.collections());
        }

        if (!mapping.belongsTo().isEmpty()) {
            renderBelongsTo(sb, mapping.belongsTo());
        }

        sb.append("\n    static mapping = {\n");
        if (classMetadata.getTableName() != null) {
            sb.append("        table '").append(classMetadata.getTableName()).append("'\n");
        }
        boolean requiresTIdMapping = hasPrimaryKeyTId || (!hasIdAttribute && hasTIdColumn);
        if (requiresTIdMapping) {
            sb.append("        id column: 't_id', generator: 'identity'\n");
        }
        
        if (!hasVersionColumn) {
            sb.append("        version false\n");
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
        for (GrailsRelationshipMapper.DomainProperty property : mapping.properties()) {
            AttributeConstraints constraints = property.constraints();
            List<String> constraintParts = new ArrayList<>();
            if (property.nullable()) {
                constraintParts.add("nullable: true");
            }
            if (constraints != null && constraints.maxLength() != null) {
                constraintParts.add("maxSize: " + constraints.maxLength());
            }
            if (constraints != null && isNumeric(constraints.minInclusive())) {
                constraintParts.add("min: " + constraints.minInclusive());
            }
            if (constraints != null && isNumeric(constraints.maxInclusive())) {
                constraintParts.add("max: " + constraints.maxInclusive());
            }
            if (constraints != null && constraints.scale() != null && isBigDecimal(property.type())) {
                constraintParts.add("scale: " + constraints.scale());
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

    private boolean isBigDecimal(String type) {
        return "BigDecimal".equals(type) || "java.math.BigDecimal".equals(type);
    }

    private boolean isTIdColumn(AttributeMetadata attr) {
        String columnName = attr.getColumnName();
        if (columnName != null && columnName.equalsIgnoreCase("t_id")) {
            return true;
        }
        return "t_id".equalsIgnoreCase(attr.getName());
    }

    private boolean isVersionColumn(AttributeMetadata attr) {
        String columnName = attr.getColumnName();
        if (columnName != null && columnName.equalsIgnoreCase("version")) {
            return true;
        }
        return "version".equalsIgnoreCase(attr.getName());
    }

    /**
     * Rendert {@code static hasMany} ausschliesslich aus persistenten
     * Collections (echte Kompositionen mit eindeutiger physischer Abbildung).
     */
    private void renderHasMany(StringBuilder sb,
                               List<GrailsRelationshipMapper.PersistentCollection> collections) {
        String hasManyBlock = collections.stream()
            .sorted(java.util.Comparator.comparing(
                GrailsRelationshipMapper.PersistentCollection::name))
            .map(collection -> collection.name() + ": " + collection.elementType())
            .collect(Collectors.joining(", "));
        sb.append("\n    static hasMany = [").append(hasManyBlock).append("]\n");
    }

    /**
     * Rendert {@code static mappedBy} nur für Collections mit eindeutig
     * aufgelöster Child-Property (nichtblanker {@code mappedByProperty}).
     */
    private void renderMappedBy(StringBuilder sb,
                                List<GrailsRelationshipMapper.PersistentCollection> collections) {
        String mappedByBlock = collections.stream()
            .filter(collection -> collection.mappedByProperty() != null
                && !collection.mappedByProperty().isBlank())
            .sorted(java.util.Comparator.comparing(
                GrailsRelationshipMapper.PersistentCollection::name))
            .map(collection -> collection.name() + ": '" + escapeGroovy(collection.mappedByProperty()) + "'")
            .collect(Collectors.joining(", "));
        if (!mappedByBlock.isEmpty()) {
            sb.append("\n    static mappedBy = [").append(mappedByBlock).append("]\n");
        }
    }

    /**
     * Rendert {@code static belongsTo} nur für echte Ownership/Komposition.
     */
    private void renderBelongsTo(StringBuilder sb,
                                 List<GrailsRelationshipMapper.DomainOwnership> ownerships) {
        String belongsToBlock = ownerships.stream()
            .map(ownership -> ownership.name() + ": " + ownership.type())
            .collect(Collectors.joining(", "));
        sb.append("\n    static belongsTo = [").append(belongsToBlock).append("]\n");
    }

    private String renderGeometryMeta(GrailsRelationshipMapper.DomainProperty property) {
        List<String> entries = new ArrayList<>();
        entries.add("srid: " + renderSrid(property));
        entries.add("kind: '" + renderGeometryKind(property) + "'");
        if (property.geometryHasZ() != null) {
            entries.add("hasZ: " + property.geometryHasZ());
        }
        if (property.geometryHasM() != null) {
            entries.add("hasM: " + property.geometryHasM());
        }
        if (property.allowEmptyGeometry() != null) {
            entries.add("allowEmpty: " + property.allowEmptyGeometry());
        }
        return "[" + String.join(", ", entries) + "]";
    }

    private String renderRelationshipMeta(GrailsRelationshipMapper.DomainProperty property) {
        RelationshipMetadata relationship = property.relationship();
        List<String> entries = new ArrayList<>();
        entries.add("targetClass: '" + escapeGroovy(property.type()) + "'");
        if (relationship != null && relationship.getSemanticKind() != null) {
            entries.add("semanticKind: '" + relationship.getSemanticKind().name() + "'");
        }
        String label = relationshipLabel(property);
        if (label != null && !label.isBlank()) {
            entries.add("label: '" + escapeGroovy(label) + "'");
        }
        if (relationship != null && relationship.getSourceAttribute() != null && !relationship.getSourceAttribute().isBlank()) {
            entries.add("sourceAttribute: '" + escapeGroovy(relationship.getSourceAttribute()) + "'");
        }
        if (relationship != null && relationship.getTargetRoleName() != null && !relationship.getTargetRoleName().isBlank()) {
            entries.add("targetRole: '" + escapeGroovy(relationship.getTargetRoleName()) + "'");
        }
        if (relationship != null && relationship.getAssociationName() != null && !relationship.getAssociationName().isBlank()) {
            entries.add("association: '" + escapeGroovy(relationship.getAssociationName()) + "'");
        }
        entries.add("mandatory: " + !property.nullable());
        return "[" + String.join(", ", entries) + "]";
    }

    private String renderInverseRelationshipMeta(GrailsInverseRelationshipPlan plan) {
        List<String> entries = new ArrayList<>();
        entries.add("relatedDomainClass: '" + escapeGroovy(plan.relatedDomainQualifiedName()) + "'");
        entries.add("relatedIliName: '" + escapeGroovy(plan.relatedIliClassName()) + "'");
        entries.add("relatedProperty: '" + escapeGroovy(plan.relatedPropertyName()) + "'");
        if (plan.relationshipName() != null && !plan.relationshipName().isBlank()) {
            entries.add("relationshipName: '" + escapeGroovy(plan.relationshipName()) + "'");
        }
        entries.add("label: '" + escapeGroovy(plan.label()) + "'");
        entries.add("relatedLabel: '" + escapeGroovy(plan.relatedLabel()) + "'");
        entries.add("mandatory: " + plan.mandatory());
        entries.add("writable: " + plan.writable());
        return "[" + String.join(", ", entries) + "]";
    }

    private String renderFieldMeta(AttributeMetadata attribute) {
        List<String> entries = new ArrayList<>();
        String label = resolveDefaultLabel(attribute);
        if (label != null && !label.isBlank()) {
            entries.add("label: '" + escapeGroovy(label) + "'");
        }
        if (attribute.getDocumentation() != null && !attribute.getDocumentation().isBlank()) {
            entries.add("documentation: '" + escapeGroovy(attribute.getDocumentation()) + "'");
        }
        if (attribute.getUnit() != null && !attribute.getUnit().isBlank()) {
            entries.add("unit: '" + escapeGroovy(attribute.getUnit()) + "'");
        }
        if (attribute.getQualifiedName() != null && !attribute.getQualifiedName().isBlank()) {
            entries.add("qualifiedName: '" + escapeGroovy(attribute.getQualifiedName()) + "'");
        }
        return "[" + String.join(", ", entries) + "]";
    }

    private String resolveDefaultLabel(AttributeMetadata attribute) {
        if (attribute.getLabels().containsKey("de-CH")) {
            return attribute.getLabels().get("de-CH");
        }
        if (attribute.getLabels().containsKey("de")) {
            return attribute.getLabels().get("de");
        }
        if (attribute.getLabels().containsKey("en")) {
            return attribute.getLabels().get("en");
        }
        return attribute.getName();
    }

    private String escapeGroovy(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n");
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

    private List<String> displayFields(List<GrailsRelationshipMapper.DomainProperty> properties) {
        List<String> preferred = DISPLAY_FIELD_PREFERENCES.stream()
            .flatMap(preference -> properties.stream()
                .filter(this::isDisplayCandidate)
                .filter(property -> preference.equals(normalizedName(property.name())))
                .map(GrailsRelationshipMapper.DomainProperty::name))
            .distinct()
            .limit(2)
            .toList();
        if (!preferred.isEmpty()) {
            return preferred;
        }
        return properties.stream()
            .filter(this::isTextDisplayCandidate)
            .map(GrailsRelationshipMapper.DomainProperty::name)
            .distinct()
            .limit(2)
            .toList();
    }

    private List<String> searchFields(List<GrailsRelationshipMapper.DomainProperty> properties) {
        List<String> preferred = DISPLAY_FIELD_PREFERENCES.stream()
            .flatMap(preference -> properties.stream()
                .filter(this::isTextDisplayCandidate)
                .filter(property -> preference.equals(normalizedName(property.name())))
                .map(GrailsRelationshipMapper.DomainProperty::name))
            .distinct()
            .toList();
        List<String> textFields = properties.stream()
            .filter(this::isTextDisplayCandidate)
            .map(GrailsRelationshipMapper.DomainProperty::name)
            .distinct()
            .toList();
        List<String> result = new ArrayList<>();
        preferred.forEach(result::add);
        for (String field : textFields) {
            if (!result.contains(field)) {
                result.add(field);
            }
        }
        return result;
    }

    private Map<String, GrailsRelationshipMapper.DomainProperty> relationshipMetadata(
        List<GrailsRelationshipMapper.DomainProperty> properties
    ) {
        Map<String, GrailsRelationshipMapper.DomainProperty> result = new LinkedHashMap<>();
        for (GrailsRelationshipMapper.DomainProperty property : properties) {
            if (property.relationship() != null) {
                result.put(property.name(), property);
            }
        }
        return result;
    }

    private boolean isDisplayCandidate(GrailsRelationshipMapper.DomainProperty property) {
        if (property == null || property.geometry() || property.relationship() != null || property.attribute() == null) {
            return false;
        }
        String name = normalizedName(property.name());
        return !"id".equals(name) && !"version".equals(name) && !"tid".equals(name);
    }

    private boolean isTextDisplayCandidate(GrailsRelationshipMapper.DomainProperty property) {
        if (!isDisplayCandidate(property)) {
            return false;
        }
        AttributeMetadata attribute = property.attribute();
        CoreType coreType = attribute.getCoreType();
        return "String".equals(property.type())
            || coreType == CoreType.TEXT
            || coreType == CoreType.MTEXT;
    }

    private String relationshipLabel(GrailsRelationshipMapper.DomainProperty property) {
        RelationshipMetadata relationship = property.relationship();
        if (relationship == null) {
            return property.name();
        }
        if (relationship.getTargetRoleName() != null && !relationship.getTargetRoleName().isBlank()) {
            return relationship.getTargetRoleName();
        }
        if (relationship.getSourceAttribute() != null && !relationship.getSourceAttribute().isBlank()) {
            return relationship.getSourceAttribute();
        }
        if (relationship.getName() != null && !relationship.getName().isBlank()) {
            return relationship.getName();
        }
        return property.name();
    }

    private String renderStringList(List<String> values) {
        return values.stream()
            .map(value -> "'" + escapeGroovy(value) + "'")
            .collect(Collectors.joining(", ", "[", "]"));
    }

    private String normalizedName(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }
}

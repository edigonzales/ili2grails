package ch.interlis.generator.model.builder;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable Builder für {@link ClassMetadata}.
 *
 * <p>Duplicate Attribute-Namen werden abgelehnt (kein stilles Überschreiben).</p>
 */
public final class ClassMetadataBuilder {

    private String name;
    private String simpleName;
    private String topicName;
    private String tableName;
    private String sqlName;
    private String documentation;
    private boolean abstractClass;
    private String baseClass;
    private ClassMetadata.ClassKind kind;
    private final Map<String, AttributeMetadataBuilder> attributeBuilders = new LinkedHashMap<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private String inheritanceStrategy;

    public ClassMetadataBuilder(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.simpleName = extractSimpleName(name);
        this.topicName = extractTopicName(name);
    }

    public static ClassMetadataBuilder from(ClassMetadata classMetadata) {
        ClassMetadataBuilder builder = new ClassMetadataBuilder(classMetadata.getName());
        builder.simpleName = classMetadata.getSimpleName();
        builder.topicName = classMetadata.getTopicName();
        builder.tableName = classMetadata.getTableName();
        builder.sqlName = classMetadata.getSqlName();
        builder.documentation = classMetadata.getDocumentation();
        builder.abstractClass = classMetadata.isAbstract();
        builder.baseClass = classMetadata.getBaseClass();
        builder.kind = classMetadata.getKind();
        for (AttributeMetadata attribute : classMetadata.getAllAttributes()) {
            builder.attributeBuilders.put(attribute.getName(), AttributeMetadataBuilder.from(attribute));
        }
        builder.labels.putAll(classMetadata.getLabels());
        builder.inheritanceStrategy = classMetadata.getInheritanceStrategy();
        return builder;
    }

    public String name() {
        return name;
    }

    public ClassMetadataBuilder name(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.simpleName = extractSimpleName(name);
        this.topicName = extractTopicName(name);
        return this;
    }

    public ClassMetadataBuilder topicName(String topicName) {
        this.topicName = topicName;
        return this;
    }

    public ClassMetadataBuilder tableName(String tableName) {
        this.tableName = tableName;
        return this;
    }

    public ClassMetadataBuilder sqlName(String sqlName) {
        this.sqlName = sqlName;
        return this;
    }

    public ClassMetadataBuilder documentation(String documentation) {
        this.documentation = documentation;
        return this;
    }

    public ClassMetadataBuilder abstractClass(boolean abstractClass) {
        this.abstractClass = abstractClass;
        return this;
    }

    public ClassMetadataBuilder baseClass(String baseClass) {
        this.baseClass = baseClass;
        return this;
    }

    public ClassMetadataBuilder kind(ClassMetadata.ClassKind kind) {
        this.kind = kind;
        return this;
    }

    public ClassMetadataBuilder attribute(AttributeMetadataBuilder attribute) {
        Objects.requireNonNull(attribute, "attribute");
        if (attributeBuilders.containsKey(attribute.name())) {
            throw new IllegalArgumentException(
                "Duplicate attribute name '" + attribute.name() + "' in class '" + name + "'");
        }
        attributeBuilders.put(attribute.name(), attribute);
        return this;
    }

    public ClassMetadataBuilder attribute(AttributeMetadata attribute) {
        return attribute(AttributeMetadataBuilder.from(attribute));
    }

    public ClassMetadataBuilder label(String language, String label) {
        labels.put(language, label);
        return this;
    }

    public ClassMetadataBuilder inheritanceStrategy(String inheritanceStrategy) {
        this.inheritanceStrategy = inheritanceStrategy;
        return this;
    }

    public Map<String, AttributeMetadataBuilder> attributeBuilders() {
        return java.util.Collections.unmodifiableMap(attributeBuilders);
    }

    public java.util.Optional<AttributeMetadataBuilder> findAttributeBuilder(String attributeName) {
        return java.util.Optional.ofNullable(attributeBuilders.get(attributeName));
    }

    public void replaceAttribute(AttributeMetadataBuilder attribute) {
        Objects.requireNonNull(attribute, "attribute");
        attributeBuilders.put(attribute.name(), attribute);
    }

    public AttributeMetadataBuilder requireAttributeBuilder(String attributeName) {
        AttributeMetadataBuilder builder = attributeBuilders.get(attributeName);
        if (builder == null) {
            throw new IllegalArgumentException(
                "Unknown attribute '" + attributeName + "' in class '" + name + "'");
        }
        return builder;
    }

    public String tableName() {
        return tableName;
    }

    public String sqlName() {
        return sqlName;
    }

    public ClassMetadata.ClassKind kind() {
        return kind;
    }

    private static String extractSimpleName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    private static String extractTopicName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(0, lastDot) : null;
    }

    /** Unvalidierter Build; Abschluss über die ModelMetadataFactory. */
    public ClassMetadata buildUnchecked() {
        Map<String, AttributeMetadata> attributes = new LinkedHashMap<>();
        attributeBuilders.forEach((attributeName, builder) ->
            attributes.put(attributeName, builder.buildUnchecked()));
        return new ClassMetadata(
            name,
            simpleName,
            topicName,
            tableName,
            sqlName,
            documentation,
            abstractClass,
            baseClass,
            kind,
            attributes,
            labels,
            inheritanceStrategy
        );
    }
}

package ch.interlis.generator.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable Metadaten einer INTERLIS-Klasse.
 *
 * <p>Relationships sind nicht Teil der Klasse; die kanonische
 * Relationship-Liste liegt ausschliesslich im {@link ModelMetadata}
 * (Indizes via {@code relationshipsFrom}/{@code relationshipsTo}).</p>
 */
public final class ClassMetadata {

    public enum ClassKind {
        CLASS,
        STRUCTURE,
        ASSOCIATION
    }

    private final String name;
    private final String simpleName;
    private final String topicName;
    private final String tableName;
    private final String sqlName;
    private final String documentation;
    private final boolean abstractClass;
    private final String baseClass;
    private final ClassKind kind;
    private final Map<String, AttributeMetadata> attributes;
    private final Map<String, String> labels;
    private final String inheritanceStrategy;

    public ClassMetadata(String name,
                  String simpleName,
                  String topicName,
                  String tableName,
                  String sqlName,
                  String documentation,
                  boolean abstractClass,
                  String baseClass,
                  ClassKind kind,
                  Map<String, AttributeMetadata> attributes,
                  Map<String, String> labels,
                  String inheritanceStrategy) {
        this.name = Objects.requireNonNull(name, "name");
        this.simpleName = simpleName;
        this.topicName = topicName;
        this.tableName = tableName;
        this.sqlName = sqlName;
        this.documentation = documentation;
        this.abstractClass = abstractClass;
        this.baseClass = baseClass;
        this.kind = kind;
        this.attributes = attributes == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.labels = labels == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(labels));
        this.inheritanceStrategy = inheritanceStrategy;
    }

    public static ch.interlis.generator.model.builder.ClassMetadataBuilder builder(String name) {
        return new ch.interlis.generator.model.builder.ClassMetadataBuilder(name);
    }

    public ch.interlis.generator.model.builder.ClassMetadataBuilder toBuilder() {
        return ch.interlis.generator.model.builder.ClassMetadataBuilder.from(this);
    }

    public String getName() {
        return name;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getTableName() {
        return tableName;
    }

    public String getSqlName() {
        return sqlName;
    }

    public String getDocumentation() {
        return documentation;
    }

    public boolean isAbstract() {
        return abstractClass;
    }

    public String getBaseClass() {
        return baseClass;
    }

    public ClassKind getKind() {
        return kind;
    }

    public Optional<AttributeMetadata> findAttribute(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(attributes.get(name));
    }

    public AttributeMetadata getAttribute(String name) {
        return attributes.get(name);
    }

    public Collection<AttributeMetadata> getAllAttributes() {
        return attributes.values();
    }

    public List<AttributeMetadata> getGeometryAttributes() {
        return attributes.values().stream()
            .filter(AttributeMetadata::isGeometry)
            .toList();
    }

    public List<AttributeMetadata> getNonGeometryAttributes() {
        return attributes.values().stream()
            .filter(attribute -> !attribute.isGeometry())
            .toList();
    }

    public Map<String, AttributeMetadata> getAttributes() {
        return attributes;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public String getInheritanceStrategy() {
        return inheritanceStrategy;
    }

    @Override
    public String toString() {
        return "ClassMetadata{" +
            "name='" + name + '\'' +
            ", topicName='" + topicName + '\'' +
            ", tableName='" + tableName + '\'' +
            ", attributes=" + attributes.size() +
            ", abstractClass=" + abstractClass +
            ", kind=" + kind +
            '}';
    }

}


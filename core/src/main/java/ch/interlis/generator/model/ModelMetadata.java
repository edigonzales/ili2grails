package ch.interlis.generator.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Kanonischer, nach dem Build unveränderlicher Metadaten-Snapshot.
 *
 * <p>Enthält genau eine kanonische Relationship-Sammlung; abgeleitete Indizes
 * liegen in {@link ModelMetadataIndexes} und referenzieren dieselben Objekte.
 * Es gibt keine Setter und keine Mutatoren.</p>
 */
public final class ModelMetadata {

    private final String modelName;
    private final String schemaName;
    private final Map<String, ClassMetadata> classes;
    private final Map<String, EnumMetadata> enums;
    private final Map<String, AssociationMetadata> associations;
    private final List<RelationshipMetadata> relationships;
    private final String iliVersion;
    private final String modelVersion;
    private final java.time.Instant importDate;
    private final String ili2dbVersion;
    private final Map<String, String> settings;
    private final ModelMetadataIndexes indexes;

    public ModelMetadata(ch.interlis.generator.model.builder.ModelMetadataBuilder builder,
                  ModelMetadataIndexes indexes) {
        this.modelName = Objects.requireNonNull(builder.modelName(), "modelName");
        this.schemaName = builder.schemaName();
        this.classes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.classes()));
        this.enums = Collections.unmodifiableMap(new LinkedHashMap<>(builder.enums()));
        this.associations = Collections.unmodifiableMap(new LinkedHashMap<>(builder.associations()));
        this.relationships = List.copyOf(builder.relationships());
        this.iliVersion = builder.iliVersion();
        this.modelVersion = builder.modelVersion();
        this.importDate = builder.importDate();
        this.ili2dbVersion = builder.ili2dbVersion();
        this.settings = Collections.unmodifiableMap(new LinkedHashMap<>(builder.settings()));
        this.indexes = indexes;
    }

    public static ch.interlis.generator.model.builder.ModelMetadataBuilder builder(String modelName) {
        return new ch.interlis.generator.model.builder.ModelMetadataBuilder(modelName);
    }

    public ch.interlis.generator.model.builder.ModelMetadataBuilder toBuilder() {
        return ch.interlis.generator.model.builder.ModelMetadataBuilder.from(this);
    }

    public String getModelName() {
        return modelName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public Optional<ClassMetadata> findClass(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(classes.get(name));
    }

    public ClassMetadata getClass(String name) {
        return classes.get(name);
    }

    public Collection<ClassMetadata> getAllClasses() {
        return classes.values();
    }

    public Map<String, ClassMetadata> getClasses() {
        return classes;
    }

    public List<RelationshipMetadata> getAllRelationships() {
        return relationships;
    }

    public List<RelationshipMetadata> getRelationships() {
        return relationships;
    }

    public List<RelationshipMetadata> relationshipsFrom(String sourceClass) {
        return indexes.bySource(sourceClass);
    }

    public List<RelationshipMetadata> relationshipsTo(String targetClass) {
        return indexes.byTarget(targetClass);
    }

    public Optional<RelationshipMetadata> relationship(RelationshipIdentity id) {
        return indexes.byIdentity(id);
    }

    public Collection<AssociationMetadata> getAllAssociations() {
        return associations.values();
    }

    public AssociationMetadata getAssociation(String name) {
        return associations.get(name);
    }

    public Map<String, AssociationMetadata> getAssociations() {
        return associations;
    }

    public Collection<EnumMetadata> getAllEnums() {
        return enums.values();
    }

    public Map<String, EnumMetadata> getEnums() {
        return enums;
    }

    public String getIliVersion() {
        return iliVersion;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public java.time.Instant getImportDate() {
        return importDate;
    }

    public String getIli2dbVersion() {
        return ili2dbVersion;
    }

    public Map<String, String> getSettings() {
        return settings;
    }

    @Override
    public String toString() {
        return "ModelMetadata{" +
            "modelName='" + modelName + '\'' +
            ", schemaName='" + schemaName + '\'' +
            ", classes=" + classes.size() +
            ", enums=" + enums.size() +
            ", associations=" + associations.size() +
            ", relationships=" + relationships.size() +
            ", iliVersion='" + iliVersion + '\'' +
            ", modelVersion='" + modelVersion + '\'' +
            '}';
    }
}

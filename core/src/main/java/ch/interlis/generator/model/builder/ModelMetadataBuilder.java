package ch.interlis.generator.model.builder;

import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataIndexes;
import ch.interlis.generator.model.RelationshipMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable Build-Model für den kanonischen {@link ModelMetadata}-Snapshot.
 *
 * <p>Nicht thread-safe; nicht aus Generatoren verwenden. Der Abschluss
 * erfolgt über {@code ModelMetadataFactory.buildValidated()} (freeze).</p>
 */
public final class ModelMetadataBuilder {

    private final String modelName;
    private String schemaName;
    private final LinkedHashMap<String, ClassMetadataBuilder> classes = new LinkedHashMap<>();
    private final LinkedHashMap<String, EnumMetadataBuilder> enums = new LinkedHashMap<>();
    private final LinkedHashMap<String, AssociationMetadataBuilder> associations = new LinkedHashMap<>();
    private final List<RelationshipMetadataBuilder> relationships = new ArrayList<>();
    private String iliVersion;
    private String modelVersion;
    private Instant importDate;
    private String ili2dbVersion;
    private final Map<String, String> settings = new LinkedHashMap<>();

    public ModelMetadataBuilder(String modelName) {
        this.modelName = Objects.requireNonNull(modelName, "modelName");
    }

    public static ModelMetadataBuilder model(String modelName) {
        return new ModelMetadataBuilder(modelName);
    }

    public static ModelMetadataBuilder from(ModelMetadata metadata) {
        ModelMetadataBuilder builder = new ModelMetadataBuilder(metadata.getModelName());
        builder.schemaName = metadata.getSchemaName();
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            builder.classes.put(classMetadata.getName(), ClassMetadataBuilder.from(classMetadata));
        }
        for (EnumMetadata enumMetadata : metadata.getAllEnums()) {
            builder.enums.put(enumMetadata.getName(), EnumMetadataBuilder.from(enumMetadata));
        }
        for (AssociationMetadata association : metadata.getAllAssociations()) {
            builder.associations.put(association.getName(), AssociationMetadataBuilder.from(association));
        }
        for (RelationshipMetadata relationship : metadata.getAllRelationships()) {
            builder.relationships.add(RelationshipMetadataBuilder.from(relationship));
        }
        builder.iliVersion = metadata.getIliVersion();
        builder.modelVersion = metadata.getModelVersion();
        builder.importDate = metadata.getImportDate();
        builder.ili2dbVersion = metadata.getIli2dbVersion();
        builder.settings.putAll(metadata.getSettings());
        return builder;
    }

    public String modelName() {
        return modelName;
    }

    public String schemaName() {
        return schemaName;
    }

    public ModelMetadataBuilder schemaName(String schemaName) {
        this.schemaName = schemaName;
        return this;
    }

    public ModelMetadataBuilder iliVersion(String iliVersion) {
        this.iliVersion = iliVersion;
        return this;
    }

    public ModelMetadataBuilder modelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
        return this;
    }

    public ModelMetadataBuilder importDate(Instant importDate) {
        this.importDate = importDate;
        return this;
    }

    public ModelMetadataBuilder ili2dbVersion(String ili2dbVersion) {
        this.ili2dbVersion = ili2dbVersion;
        return this;
    }

    public ModelMetadataBuilder setting(String key, String value) {
        settings.put(key, value);
        return this;
    }

    /**
     * Erzeugt oder liefert den Class-Builder; Duplicate-Namen werden
     * abgelehnt (kein stilles Überschreiben).
     */
    public ClassMetadataBuilder classBuilder(String className) {
        Objects.requireNonNull(className, "className");
        ClassMetadataBuilder existing = classes.get(className);
        if (existing != null) {
            throw new IllegalArgumentException(
                "Duplicate class name '" + className + "' in model '" + modelName + "'");
        }
        ClassMetadataBuilder created = new ClassMetadataBuilder(className);
        classes.put(className, created);
        return created;
    }

    public ClassMetadataBuilder requireClassBuilder(String className) {
        ClassMetadataBuilder builder = classes.get(className);
        if (builder == null) {
            throw new IllegalArgumentException(
                "Unknown class '" + className + "' in model '" + modelName + "'");
        }
        return builder;
    }

    public boolean hasClassBuilder(String className) {
        return classes.containsKey(className);
    }

    public java.util.Optional<ClassMetadataBuilder> findClassBuilder(String className) {
        return java.util.Optional.ofNullable(classes.get(className));
    }

    public EnumMetadataBuilder enumBuilder(String enumName) {
        Objects.requireNonNull(enumName, "enumName");
        EnumMetadataBuilder existing = enums.get(enumName);
        if (existing != null) {
            throw new IllegalArgumentException(
                "Duplicate enum name '" + enumName + "' in model '" + modelName + "'");
        }
        EnumMetadataBuilder created = new EnumMetadataBuilder(enumName);
        enums.put(enumName, created);
        return created;
    }

    public java.util.Optional<EnumMetadataBuilder> findEnumBuilder(String enumName) {
        return java.util.Optional.ofNullable(enums.get(enumName));
    }

    public EnumMetadataBuilder addEnumFrom(ch.interlis.generator.model.EnumMetadata enumMetadata) {
        EnumMetadataBuilder builder = EnumMetadataBuilder.from(enumMetadata);
        enums.put(builder.name(), builder);
        return builder;
    }

    public ClassMetadataBuilder addClassFrom(ch.interlis.generator.model.ClassMetadata classMetadata) {
        ClassMetadataBuilder builder = ClassMetadataBuilder.from(classMetadata);
        classes.put(builder.name(), builder);
        return builder;
    }

    public AssociationMetadataBuilder addAssociationFrom(
        ch.interlis.generator.model.AssociationMetadata associationMetadata) {
        AssociationMetadataBuilder builder = AssociationMetadataBuilder.from(associationMetadata);
        associations.put(builder.name(), builder);
        return builder;
    }

    public EnumMetadataBuilder requireEnumBuilder(String enumName) {
        EnumMetadataBuilder builder = enums.get(enumName);
        if (builder == null) {
            throw new IllegalArgumentException(
                "Unknown enum '" + enumName + "' in model '" + modelName + "'");
        }
        return builder;
    }

    public AssociationMetadataBuilder associationBuilder(String associationName) {
        Objects.requireNonNull(associationName, "associationName");
        AssociationMetadataBuilder existing = associations.get(associationName);
        if (existing != null) {
            throw new IllegalArgumentException(
                "Duplicate association name '" + associationName + "' in model '" + modelName + "'");
        }
        AssociationMetadataBuilder created = new AssociationMetadataBuilder(associationName);
        associations.put(associationName, created);
        return created;
    }

    public java.util.Optional<AssociationMetadataBuilder> findAssociationBuilder(String associationName) {
        return java.util.Optional.ofNullable(associations.get(associationName));
    }

    public AssociationMetadataBuilder requireAssociationBuilder(String associationName) {
        AssociationMetadataBuilder builder = associations.get(associationName);
        if (builder == null) {
            throw new IllegalArgumentException(
                "Unknown association '" + associationName + "' in model '" + modelName + "'");
        }
        return builder;
    }

    public RelationshipMetadataBuilder relationshipBuilder(String relationshipName) {
        Objects.requireNonNull(relationshipName, "relationshipName");
        RelationshipMetadataBuilder created = new RelationshipMetadataBuilder(relationshipName);
        relationships.add(created);
        return created;
    }

    public ModelMetadataBuilder relationship(RelationshipMetadataBuilder relationship) {
        relationships.add(Objects.requireNonNull(relationship, "relationship"));
        return this;
    }

    public Map<String, ClassMetadataBuilder> classBuilders() {
        return java.util.Collections.unmodifiableMap(classes);
    }

    public Map<String, EnumMetadataBuilder> enumBuilders() {
        return java.util.Collections.unmodifiableMap(enums);
    }

    public Map<String, AssociationMetadataBuilder> associationBuilders() {
        return java.util.Collections.unmodifiableMap(associations);
    }

    public List<RelationshipMetadataBuilder> relationshipBuilders() {
        return java.util.Collections.unmodifiableList(relationships);
    }

    /**
     * Ersetzt den Relationship-Builder an einer Position (Merge-Ergebnis).
     */
    public void replaceRelationshipBuilder(int index, RelationshipMetadataBuilder replacement) {
        relationships.set(index, Objects.requireNonNull(replacement, "replacement"));
    }

    public void appendRelationshipBuilder(RelationshipMetadataBuilder relationship) {
        relationships.add(Objects.requireNonNull(relationship, "relationship"));
    }

    public String iliVersion() {
        return iliVersion;
    }

    public String modelVersion() {
        return modelVersion;
    }

    public Instant importDate() {
        return importDate;
    }

    public String ili2dbVersion() {
        return ili2dbVersion;
    }

    public Map<String, String> settings() {
        return java.util.Collections.unmodifiableMap(settings);
    }

    /** @return built classes; internal, use the factory for validated builds */
    public Map<String, ClassMetadata> classes() {
        Map<String, ClassMetadata> built = new LinkedHashMap<>();
        classes.forEach((className, builder) -> built.put(className, builder.buildUnchecked()));
        return built;
    }

    /** @return built enums; internal, use the factory for validated builds */
    public Map<String, EnumMetadata> enums() {
        Map<String, EnumMetadata> built = new LinkedHashMap<>();
        enums.forEach((enumName, builder) -> built.put(enumName, builder.buildUnchecked()));
        return built;
    }

    /** @return built associations; internal, use the factory for validated builds */
    public Map<String, AssociationMetadata> associations() {
        Map<String, AssociationMetadata> built = new LinkedHashMap<>();
        associations.forEach((associationName, builder) ->
            built.put(associationName, builder.buildUnchecked()));
        return built;
    }

    /** @return built relationships; internal, use the factory for validated builds */
    public List<RelationshipMetadata> relationships() {
        List<RelationshipMetadata> built = new ArrayList<>();
        for (RelationshipMetadataBuilder builder : relationships) {
            built.add(builder.buildUnchecked());
        }
        return built;
    }

    /**
     * Unvalidierter Build; primärer Abschluss über die
     * {@code ModelMetadataFactory} (freeze mit Validierung).
     */
    public ModelMetadata buildUnchecked() {
        Map<String, ClassMetadata> builtClasses = classes();
        List<RelationshipMetadata> builtRelationships = relationships();
        ModelMetadataIndexes indexes = ModelMetadataIndexes.build(
            builtClasses.values(), builtRelationships);
        return new ModelMetadata(this, indexes);
    }
}

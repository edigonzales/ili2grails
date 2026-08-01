package ch.interlis.generator.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Unveränderliche Indizes über die kanonischen Relationship- und
 * Klassen-Objekte. Werden genau einmal beim Build erzeugt.
 */
public final class ModelMetadataIndexes {

    private final Map<String, List<RelationshipMetadata>> bySourceClass;
    private final Map<String, List<RelationshipMetadata>> byTargetClass;
    private final Map<RelationshipIdentity, RelationshipMetadata> byIdentity;
    private final Map<String, ClassMetadata> byPhysicalTable;

    private ModelMetadataIndexes(Map<String, List<RelationshipMetadata>> bySourceClass,
                                 Map<String, List<RelationshipMetadata>> byTargetClass,
                                 Map<RelationshipIdentity, RelationshipMetadata> byIdentity,
                                 Map<String, ClassMetadata> byPhysicalTable) {
        this.bySourceClass = bySourceClass;
        this.byTargetClass = byTargetClass;
        this.byIdentity = byIdentity;
        this.byPhysicalTable = byPhysicalTable;
    }

    public static ModelMetadataIndexes build(Collection<ClassMetadata> classes,
                                             Collection<RelationshipMetadata> relationships) {
        Map<String, List<RelationshipMetadata>> bySource = new LinkedHashMap<>();
        Map<String, List<RelationshipMetadata>> byTarget = new LinkedHashMap<>();
        Map<RelationshipIdentity, RelationshipMetadata> byIdentity = new LinkedHashMap<>();
        Map<String, ClassMetadata> byPhysicalTable = new LinkedHashMap<>();

        for (RelationshipMetadata relationship : relationships) {
            bySource.computeIfAbsent(relationship.getSourceClass(), key -> new java.util.ArrayList<>())
                .add(relationship);
            byTarget.computeIfAbsent(relationship.getTargetClass(), key -> new java.util.ArrayList<>())
                .add(relationship);
            byIdentity.put(relationship.identity(), relationship);
        }
        for (ClassMetadata classMetadata : classes) {
            if (classMetadata.getTableName() != null && !classMetadata.getTableName().isBlank()) {
                byPhysicalTable.putIfAbsent(
                    classMetadata.getTableName().toLowerCase(java.util.Locale.ROOT),
                    classMetadata);
            }
        }

        Map<String, List<RelationshipMetadata>> immutableSource = new LinkedHashMap<>();
        Map<String, List<RelationshipMetadata>> immutableTarget = new LinkedHashMap<>();
        bySource.forEach((key, value) ->
            immutableSource.put(key, Collections.unmodifiableList(List.copyOf(value))));
        byTarget.forEach((key, value) ->
            immutableTarget.put(key, Collections.unmodifiableList(List.copyOf(value))));

        return new ModelMetadataIndexes(
            Collections.unmodifiableMap(immutableSource),
            Collections.unmodifiableMap(immutableTarget),
            Collections.unmodifiableMap(byIdentity),
            Collections.unmodifiableMap(byPhysicalTable)
        );
    }

    public List<RelationshipMetadata> bySource(String className) {
        return bySourceClass.getOrDefault(className, List.of());
    }

    public List<RelationshipMetadata> byTarget(String className) {
        return byTargetClass.getOrDefault(className, List.of());
    }

    public Optional<RelationshipMetadata> byIdentity(RelationshipIdentity id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byIdentity.get(id));
    }

    public Optional<ClassMetadata> byPhysicalTable(String tableName) {
        if (tableName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byPhysicalTable.get(tableName.toLowerCase(java.util.Locale.ROOT)));
    }
}

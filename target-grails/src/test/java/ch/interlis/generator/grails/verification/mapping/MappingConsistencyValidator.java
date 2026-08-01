package ch.interlis.generator.grails.verification.mapping;

import ch.interlis.generator.grails.verification.corpus.AllowedDifference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Vergleicht die drei unabhängigen Sichten (Spezifikation §34.3, §34.5):
 * erwartetes Mapping (Core-IR + Grails-Planer), tatsächliches
 * Hibernate-Mapping der gestarteten App und reales PostgreSQL-Schema.
 * Ein unerklärter Mismatch ist ein Fehler.
 */
public final class MappingConsistencyValidator {

    public MappingConsistencyReport validate(ExpectedPersistenceMapping expected,
                                             HibernateMappingSnapshot hibernate,
                                             DatabasePhysicalSnapshot database) {
        return validate(expected, hibernate, database, List.of());
    }

    /**
     * Dokumentierte Abweichungen (Spezifikation §34.6): exakte (Code, Entity,
     * Property)-Treffer werden aus dem Report entfernt und gesondert
     * ausgewiesen. Jede andere Abweichung bleibt ein Fehler.
     */
    public MappingConsistencyReport validate(ExpectedPersistenceMapping expected,
                                             HibernateMappingSnapshot hibernate,
                                             DatabasePhysicalSnapshot database,
                                             List<AllowedDifference> allowedDifferences) {
        List<MappingMismatch> mismatches = new ArrayList<>();
        String scenarioId = "mapping";

        for (ExpectedEntityMapping entity : expected.entities().values()) {
            HibernateEntityMapping hibernateEntity = findHibernateEntity(hibernate, entity);
            if (hibernateEntity == null) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.ENTITY_MISSING_IN_HIBERNATE,
                    entity.domainClassName(), null,
                    entity.domainClassName(), null,
                    "expected entity does not exist in the Hibernate mapping"));
                continue;
            }
            DatabaseTableMapping databaseTable = database.table(entity.tableName());
            if (databaseTable == null) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.ENTITY_MISSING_IN_DATABASE,
                    entity.domainClassName(), null,
                    entity.tableName(), null,
                    "expected table does not exist in the database"));
                continue;
            }
            validateTableName(entity, hibernateEntity, mismatches);
            validateId(entity, hibernateEntity, databaseTable, mismatches);
            validateVersion(entity, hibernateEntity, mismatches);
            validateProperties(entity, hibernateEntity, databaseTable, mismatches);
            validateCollections(entity, hibernateEntity, hibernate, mismatches);
        }

        validateAssociations(expected, database, mismatches);
        List<MappingMismatch> unexplained = new ArrayList<>();
        List<MappingMismatch> documented = new ArrayList<>();
        for (MappingMismatch mismatch : mismatches) {
            boolean matches = allowedDifferences.stream().anyMatch(allowed ->
                allowed.code() != null && allowed.code().equals(mismatch.code().name())
                    && sameName(allowed.entity(), mismatch.entity())
                    && sameName(allowed.property(), mismatch.property()));
            if (matches) {
                documented.add(mismatch);
            } else {
                unexplained.add(mismatch);
            }
        }
        unexplained.sort(Comparator
            .comparing((MappingMismatch mismatch) -> mismatch.code().name())
            .thenComparing(mismatch -> mismatch.entity() == null ? "" : mismatch.entity())
            .thenComparing(mismatch -> mismatch.property() == null ? "" : mismatch.property()));
        return new MappingConsistencyReport(scenarioId, unexplained, documented);
    }

    private HibernateEntityMapping findHibernateEntity(HibernateMappingSnapshot hibernate,
                                                       ExpectedEntityMapping entity) {
        HibernateEntityMapping byClass = hibernate.entities().get(entity.domainClassName());
        if (byClass != null) {
            return byClass;
        }
        return hibernate.entities().values().stream()
            .filter(candidate -> candidate.tables().stream()
                .anyMatch(table -> table.equalsIgnoreCase(entity.tableName())))
            .findFirst()
            .orElse(null);
    }

    private void validateTableName(ExpectedEntityMapping entity,
                                   HibernateEntityMapping hibernateEntity,
                                   List<MappingMismatch> mismatches) {
        if (entity.tableName() == null) {
            return;
        }
        boolean matches = hibernateEntity.tables().stream()
            .map(this::unqualified)
            .anyMatch(table -> table.equalsIgnoreCase(entity.tableName()));
        if (!matches) {
            mismatches.add(new MappingMismatch(
                MappingMismatchCode.TABLE_NAME_MISMATCH,
                entity.domainClassName(), null,
                entity.tableName(),
                String.join(",", hibernateEntity.tables()),
                "Hibernate root table does not match the expected table"));
        }
    }

    private void validateId(ExpectedEntityMapping entity,
                            HibernateEntityMapping hibernateEntity,
                            DatabaseTableMapping databaseTable,
                            List<MappingMismatch> mismatches) {
        if (entity.id() == null) {
            return;
        }
        boolean idColumnMatches = hibernateEntity.idColumns().stream()
            .anyMatch(column -> column.equalsIgnoreCase(entity.id().columnName()));
        if (!idColumnMatches) {
            mismatches.add(new MappingMismatch(
                MappingMismatchCode.ID_COLUMN_MISMATCH,
                entity.domainClassName(), entity.id().propertyName(),
                entity.id().columnName(),
                String.join(",", hibernateEntity.idColumns()),
                "Hibernate identifier column does not match t_id"));
        }
        boolean dbPkMatches = databaseTable.primaryKeyColumns().stream()
            .anyMatch(column -> column.equalsIgnoreCase(entity.id().columnName()));
        if (!dbPkMatches) {
            mismatches.add(new MappingMismatch(
                MappingMismatchCode.ID_COLUMN_MISMATCH,
                entity.domainClassName(), entity.id().propertyName(),
                entity.id().columnName(),
                String.join(",", databaseTable.primaryKeyColumns()),
                "database primary key does not contain t_id"));
        }
    }

    private void validateVersion(ExpectedEntityMapping entity,
                                 HibernateEntityMapping hibernateEntity,
                                 List<MappingMismatch> mismatches) {
        boolean hibernateVersioned = hibernateEntity.versionProperty() != null
            || !hibernateEntity.versionColumns().isEmpty();
        if (entity.versioned() != hibernateVersioned) {
            mismatches.add(new MappingMismatch(
                MappingMismatchCode.VERSION_MAPPING_MISMATCH,
                entity.domainClassName(), "version",
                String.valueOf(entity.versioned()),
                String.valueOf(hibernateVersioned),
                "version mapping differs between expected IR and Hibernate"));
        }
    }

    private void validateProperties(ExpectedEntityMapping entity,
                                    HibernateEntityMapping hibernateEntity,
                                    DatabaseTableMapping databaseTable,
                                    List<MappingMismatch> mismatches) {
        for (ExpectedPropertyMapping expectedProperty : entity.properties().values()) {
            HibernatePropertyMapping hibernateProperty =
                hibernateEntity.properties().get(expectedProperty.propertyName());
            if (hibernateProperty == null) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.PROPERTY_MISSING,
                    entity.domainClassName(), expectedProperty.propertyName(),
                    expectedProperty.propertyName(), null,
                    "expected property is missing from the Hibernate mapping"));
                continue;
            }
            if (!sameName(expectedProperty.columnName(), hibernateProperty.columnName())) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.COLUMN_NAME_MISMATCH,
                    entity.domainClassName(), expectedProperty.propertyName(),
                    expectedProperty.columnName(),
                    hibernateProperty.columnName(),
                    "column name differs between expected mapping and Hibernate"));
            }
            if (expectedProperty.relationship() && hibernateProperty.relationship()
                && !sameName(expectedProperty.targetDomainClass(), hibernateProperty.targetEntity())) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.RELATIONSHIP_TARGET_MISMATCH,
                    entity.domainClassName(), expectedProperty.propertyName(),
                    expectedProperty.targetDomainClass(),
                    hibernateProperty.targetEntity(),
                    "relationship target entity differs"));
            }
            if (expectedProperty.relationship() && hibernateProperty.relationship()
                && !sameName(expectedProperty.foreignKeyColumn(), hibernateProperty.foreignKeyColumn())) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.FOREIGN_KEY_COLUMN_MISMATCH,
                    entity.domainClassName(), expectedProperty.propertyName(),
                    expectedProperty.foreignKeyColumn(),
                    hibernateProperty.foreignKeyColumn(),
                    "foreign key column differs"));
            }
            DatabaseColumnMapping databaseColumn = expectedProperty.columnName() == null
                ? null : databaseTable.columns().get(expectedProperty.columnName());
            if (expectedProperty.columnName() != null && databaseColumn == null) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.COLUMN_NAME_MISMATCH,
                    entity.domainClassName(), expectedProperty.propertyName(),
                    expectedProperty.columnName(), null,
                    "expected column is missing from the database table"));
                continue;
            }
            // Nullability: die Datenbank ist die Durchsetzungs-Wahrheit
            // (Hibernate meldet bei GORM-Associations constraints-abhängig
            // abweichende Werte).
            if (databaseColumn != null && expectedProperty.nullable() != databaseColumn.nullable()) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.NULLABILITY_MISMATCH,
                    entity.domainClassName(), expectedProperty.propertyName(),
                    String.valueOf(expectedProperty.nullable()),
                    String.valueOf(databaseColumn.nullable()),
                    "nullability differs between expected mapping and the database"));
            }
            if (expectedProperty.geometry() && databaseColumn != null) {
                if (databaseColumn.geometryType() == null) {
                    mismatches.add(new MappingMismatch(
                        MappingMismatchCode.GEOMETRY_TYPE_MISMATCH,
                        entity.domainClassName(), expectedProperty.propertyName(),
                        "geometry column", "non-geometry column",
                        "expected geometry column has no PostGIS geometry type"));
                }
                if (expectedProperty.srid() != null && databaseColumn.srid() != null
                    && !expectedProperty.srid().equals(databaseColumn.srid())) {
                    mismatches.add(new MappingMismatch(
                        MappingMismatchCode.GEOMETRY_SRID_MISMATCH,
                        entity.domainClassName(), expectedProperty.propertyName(),
                        String.valueOf(expectedProperty.srid()),
                        String.valueOf(databaseColumn.srid()),
                        "SRID differs from PostGIS metadata"));
                }
            }
        }
    }

    private void validateCollections(ExpectedEntityMapping entity,
                                     HibernateEntityMapping hibernateEntity,
                                     HibernateMappingSnapshot snapshot,
                                     List<MappingMismatch> mismatches) {
        for (ExpectedCollectionMapping expectedCollection : entity.collections().values()) {
            HibernateCollectionMapping hibernateCollection =
                hibernateEntity.collections().get(expectedCollection.propertyName());
            if (hibernateCollection == null) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.COLLECTION_MAPPING_MISMATCH,
                    entity.domainClassName(), expectedCollection.propertyName(),
                    "persistent collection", null,
                    "expected persistent collection is missing from Hibernate"));
                continue;
            }
            if (hibernateCollection.mappedByProperty() != null
                && expectedCollection.mappedByProperty() != null
                && !sameName(expectedCollection.mappedByProperty(), hibernateCollection.mappedByProperty())) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.COLLECTION_MAPPING_MISMATCH,
                    entity.domainClassName(), expectedCollection.propertyName(),
                    expectedCollection.mappedByProperty(),
                    hibernateCollection.mappedByProperty(),
                    "mappedBy differs"));
            }
            String childTable = childTableOf(snapshot, hibernateCollection);
            if (hibernateCollection.tableName() != null && childTable != null
                && !sameName(unqualified(hibernateCollection.tableName()), childTable)) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.UNEXPECTED_JOIN_TABLE,
                    entity.domainClassName(), expectedCollection.propertyName(),
                    "child FK table " + childTable,
                    hibernateCollection.tableName(),
                    "collection uses an unexpected join table instead of a child FK"));
            }
        }
        for (String unexpected : hibernateEntity.collections().keySet()) {
            if (!entity.collections().containsKey(unexpected)) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.COLLECTION_MAPPING_MISMATCH,
                    entity.domainClassName(), unexpected,
                    "no collection", "persistent collection",
                    "unexpected persistent collection (e.g. inverse reference) in Hibernate"));
            }
        }
    }

    private String childTableOf(HibernateMappingSnapshot snapshot,
                                HibernateCollectionMapping collection) {
        if (collection.elementEntity() == null) {
            return null;
        }
        HibernateEntityMapping child = snapshot.entities().get(collection.elementEntity());
        if (child == null || child.tables().isEmpty()) {
            return null;
        }
        return unqualified(child.tables().get(0));
    }

    private String unqualified(String tableName) {
        if (tableName == null) {
            return null;
        }
        int separator = tableName.lastIndexOf('.');
        return separator < 0 ? tableName : tableName.substring(separator + 1);
    }

    private void validateAssociations(ExpectedPersistenceMapping expected,
                                      DatabasePhysicalSnapshot database,
                                      List<MappingMismatch> mismatches) {
        for (ExpectedAssociationMapping association : expected.associations()) {
            if (association.linkTable() == null) {
                continue;
            }
            DatabaseTableMapping table = database.table(association.linkTable());
            if (table == null) {
                mismatches.add(new MappingMismatch(
                    MappingMismatchCode.ENTITY_MISSING_IN_DATABASE,
                    association.linkDomainClass(), association.associationName(),
                    association.linkTable(), null,
                    "association link table is missing from the database"));
            }
        }
    }

    private static boolean sameName(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }
}

package com.example

/**
 * Sammlung des tatsächlichen Hibernate-Mappings über die SessionFactory-
 * Persister (Spezifikation §32.1). Keine reine Reflection auf Domainklassen.
 */
final class HibernateMappingSnapshotCollector {

    static Map<String, Object> collect(grailsApplication, sessionFactory) {
        def metamodel = sessionFactory.metamodel
        def entities = [:]

        // Hibernate 5.6: MetamodelImpl.getEntityPersisterMap(); andere
        // Implementierungen bieten getEntityPersisters().
        def entityPersisters = metamodel.hasProperty('entityPersisterMap')
            ? metamodel.entityPersisterMap
            : metamodel.entityPersisters
        def collectionPersisters = metamodel.hasProperty('collectionPersisterMap')
            ? metamodel.collectionPersisterMap
            : (metamodel.hasProperty('collectionPersisters') ? metamodel.collectionPersisters : [:])

        entityPersisters.each { String entityName, persister ->
            def entity = [
                entityClass   : entityName,
                tables        : [],
                idProperty    : null,
                idColumns     : [],
                versionProperty: null,
                versionColumns: [],
                properties    : [:],
                collections   : [:]
            ]
            try {
                if (persister.tableName != null) {
                    entity.tables = [persister.tableName.toString()]
                }
            } catch (Exception ignored) {
                // persister may not expose a table for unmapped roles
            }
            try {
                entity.idProperty = persister.identifierPropertyName
                def idColumns = persister.identifierColumnNames
                if (idColumns != null) {
                    entity.idColumns = idColumns.collect { it.toString() }
                }
            } catch (Exception ignored) {
            }
            try {
                int versionIndex = persister.versionProperty
                if (versionIndex >= 0) {
                    entity.versionProperty = persister.propertyNames[versionIndex]
                    def versionColumns = persister.getPropertyColumnNames(versionIndex)
                    if (versionColumns != null) {
                        entity.versionColumns = versionColumns.collect { it.toString() }
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                persister.propertyNames.eachWithIndex { String propertyName, int index ->
                    def type = persister.propertyTypes[index]
                    def columns = persister.getPropertyColumnNames(index)
                    def columnNames = columns == null ? [] : columns.collect { it.toString() }
                    if (type instanceof org.hibernate.type.EntityType) {
                        entity.properties[propertyName] = [
                            propertyName    : propertyName,
                            columnName      : columnNames.isEmpty() ? null : columnNames[0],
                            nullable        : isNullable(persister, index),
                            relationship    : true,
                            targetEntity    : ((org.hibernate.type.EntityType) type).associatedEntityName,
                            foreignKeyColumn: columnNames.isEmpty() ? null : columnNames[0],
                            geometry        : false,
                            srid            : null,
                            geometryType    : null
                        ]
                    } else {
                        boolean geometry = type?.name?.toString()?.toLowerCase()?.contains('geometry')
                        Integer srid = null
                        String geometryType = type?.name?.toString()
                        if (geometry) {
                            try {
                                if (type.hasProperty('srid')) {
                                    srid = type.srid
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        entity.properties[propertyName] = [
                            propertyName    : propertyName,
                            columnName      : columnNames.isEmpty() ? null : columnNames[0],
                            nullable        : isNullable(persister, index),
                            relationship    : false,
                            targetEntity    : null,
                            foreignKeyColumn: null,
                            geometry        : geometry,
                            srid            : srid,
                            geometryType    : geometryType
                        ]
                    }
                }
            } catch (Exception ignored) {
            }
            entities[entityName] = entity
        }

        // Collections (persistente Kompositionen) über die Collection-Persister.
        try {
            collectionPersisters.each { String role, persister ->
                String ownerEntity = role.contains('.')
                    ? role.substring(0, role.lastIndexOf('.'))
                    : role
                String propertyName = role.contains('.')
                    ? role.substring(role.lastIndexOf('.') + 1)
                    : role
                def entity = entities[ownerEntity]
                if (entity == null) {
                    return
                }
                def collection = [
                    propertyName    : propertyName,
                    elementEntity   : null,
                    tableName       : null,
                    mappedByProperty: null,
                    inverse         : false
                ]
                try {
                    collection.tableName = persister.tableName?.toString()
                } catch (Exception ignored) {
                }
                try {
                    collection.inverse = persister.isInverse()
                } catch (Exception ignored) {
                }
                try {
                    if (persister.hasProperty('mappedByProperty')) {
                        collection.mappedByProperty = persister.mappedByProperty?.toString()
                    }
                } catch (Exception ignored) {
                }
                try {
                    def elementType = persister.elementType
                    if (elementType instanceof org.hibernate.type.EntityType) {
                        collection.elementEntity = ((org.hibernate.type.EntityType) elementType).associatedEntityName
                    }
                } catch (Exception ignored) {
                }
                entity.collections[propertyName] = collection
            }
        } catch (Exception ignored) {
        }

        [entities: entities]
    }

    private static boolean isNullable(persister, int index) {
        try {
            def nullability = persister.getPropertyNullability(index)
            return nullability != null && nullability == 1
        } catch (Exception ignored) {
            return true
        }
    }
}

package ch.interlis.generator.grails.verification.mapping;

import java.util.List;
import java.util.Map;

/**
 * Hibernate-Entity-Abbildung (Spezifikation §32.3).
 */
public record HibernateEntityMapping(
    String entityClass,
    List<String> tables,
    String idProperty,
    List<String> idColumns,
    String versionProperty,
    List<String> versionColumns,
    Map<String, HibernatePropertyMapping> properties,
    Map<String, HibernateCollectionMapping> collections
) {

    public HibernateEntityMapping {
        tables = tables == null ? List.of() : List.copyOf(tables);
        idColumns = idColumns == null ? List.of() : List.copyOf(idColumns);
        versionColumns = versionColumns == null ? List.of() : List.copyOf(versionColumns);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        collections = collections == null ? Map.of() : Map.copyOf(collections);
    }
}

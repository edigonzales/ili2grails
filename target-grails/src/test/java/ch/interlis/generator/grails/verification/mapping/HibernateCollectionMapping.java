package ch.interlis.generator.grails.verification.mapping;

/**
 * Hibernate-Collection-Abbildung (Spezifikation §32.3).
 */
public record HibernateCollectionMapping(
    String propertyName,
    String elementEntity,
    String tableName,
    String mappedByProperty,
    boolean inverse
) {
}

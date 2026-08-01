package ch.interlis.generator.grails.verification.mapping;

/**
 * Hibernate-Property-Abbildung (Spezifikation §32.3).
 */
public record HibernatePropertyMapping(
    String propertyName,
    String columnName,
    boolean nullable,
    boolean relationship,
    String targetEntity,
    String foreignKeyColumn,
    boolean geometry,
    Integer srid,
    String geometryType
) {
}

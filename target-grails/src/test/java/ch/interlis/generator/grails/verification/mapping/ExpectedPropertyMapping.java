package ch.interlis.generator.grails.verification.mapping;

/**
 * Erwartete Property-Abbildung (Spezifikation §31.3).
 */
public record ExpectedPropertyMapping(
    String propertyName,
    String columnName,
    String javaType,
    boolean nullable,
    boolean relationship,
    String targetDomainClass,
    String targetTable,
    String foreignKeyColumn,
    boolean geometry,
    Integer srid
) {
}

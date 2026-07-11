package ch.interlis.generator.grails;

/**
 * Grails-specific plan for a genuine association attribute (not a role foreign key,
 * primary key or technical ili2db column).
 */
public record GrailsAssociationAttributePlan(
    String iliName,
    String domainPropertyName,
    String javaType,
    String coreType,
    String label,
    String documentation,
    String unit,
    boolean mandatory,
    Integer maxLength,
    String minInclusive,
    String maxInclusive,
    Integer precision,
    Integer scale,
    boolean geometry,
    String geometryKind,
    Integer geometrySrid,
    String enumType
) {
}

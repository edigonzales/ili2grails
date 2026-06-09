package ch.interlis.generator.model;

/**
 * Framework-agnostischer Constraint-Vertrag fuer Attribute in der Core-IR.
 */
public record AttributeConstraints(
    boolean required,
    Integer maxLength,
    String minInclusive,
    String maxInclusive,
    Integer precision,
    Integer scale,
    Integer cardinalityMin,
    Integer cardinalityMax,
    boolean ordered
) {
}

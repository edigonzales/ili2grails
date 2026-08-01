package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * Immutable descriptor of an attribute carried by an association domain.
 */
public record AssociationAttributeDescriptor(
    String iliName,
    String propertyName,
    String javaType,
    RuntimeCoreType coreType,
    String label,
    boolean mandatory,
    Integer maxLength,
    String unit,
    String enumType,
    boolean geometry
) {

    public AssociationAttributeDescriptor {
        if (propertyName != null && propertyName.isBlank()) {
            throw new IllegalArgumentException("propertyName must not be blank");
        }
    }
}

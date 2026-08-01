package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * Immutable metadata for a single generated domain field.
 */
public record FieldDescriptor(
    String name,
    String iliName,
    String javaType,
    RuntimeCoreType coreType,
    FieldKind kind,
    String label,
    boolean mandatory,
    Integer maxLength,
    String minValue,
    String maxValue,
    Integer precision,
    Integer scale,
    String unit,
    String enumType
) {

    public FieldDescriptor {
        name = DescriptorValidation.requireText(name, "name");
        kind = kind == null ? FieldKind.SCALAR : kind;
    }
}

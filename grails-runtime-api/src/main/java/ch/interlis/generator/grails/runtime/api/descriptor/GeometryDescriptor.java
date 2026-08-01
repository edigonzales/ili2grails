package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * Immutable metadata for a single generated geometry field.
 */
public record GeometryDescriptor(
    String fieldName,
    Integer srid,
    String kind,
    Boolean hasZ,
    Boolean hasM,
    Boolean allowEmpty
) {

    public GeometryDescriptor {
        fieldName = DescriptorValidation.requireText(fieldName, "fieldName");
    }
}

package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * Immutable metadata of an inverse (navigational) relationship section.
 *
 * <p>Writability is the conjunction of the generated capability and the
 * runtime mode. Runtime overrides always create a new descriptor instance;
 * the map-based mutation path is not supported.</p>
 */
public record InverseRelationshipDescriptor(
    String name,
    String label,
    String ownerIliClassName,
    String relatedIliClassName,
    String relatedDomainClassName,
    String relatedControllerName,
    String relatedPropertyName,
    String relatedLabel,
    boolean mandatory,
    boolean generatedWritable,
    boolean visible,
    InverseRelationshipMode mode
) {

    public InverseRelationshipDescriptor {
        name = DescriptorValidation.requireText(name, "name");
        if (relatedDomainClassName != null && relatedDomainClassName.isBlank()) {
            throw new IllegalArgumentException("relatedDomainClassName must not be blank");
        }
        if (relatedPropertyName != null && relatedPropertyName.isBlank()) {
            throw new IllegalArgumentException("relatedPropertyName must not be blank");
        }
        mode = mode == null ? InverseRelationshipMode.AUTO : mode;
    }

    public boolean writable() {
        return generatedWritable
            && (mode == InverseRelationshipMode.AUTO
            || mode == InverseRelationshipMode.EDITABLE);
    }
}

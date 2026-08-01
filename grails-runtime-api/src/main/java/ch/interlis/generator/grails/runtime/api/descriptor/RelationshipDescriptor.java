package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * Immutable metadata of a to-one relationship property on a domain.
 */
public record RelationshipDescriptor(
    String name,
    String propertyName,
    String targetDomainClassName,
    String semanticKind,
    String label,
    String sourceAttribute,
    String targetRoleName,
    boolean mandatory
) {

    public RelationshipDescriptor {
        name = DescriptorValidation.requireText(name, "name");
        propertyName = DescriptorValidation.requireText(propertyName, "propertyName");
        if (targetDomainClassName != null && targetDomainClassName.isBlank()) {
            throw new IllegalArgumentException("targetDomainClassName must not be blank");
        }
    }
}

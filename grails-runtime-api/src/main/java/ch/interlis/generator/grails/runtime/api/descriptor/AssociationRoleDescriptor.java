package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * Immutable descriptor of a single association role.
 */
public record AssociationRoleDescriptor(
    String name,
    String label,
    String propertyName,
    String targetIliClassName,
    String targetDomainClassName,
    int minCardinality,
    int maxCardinality,
    boolean mandatory,
    boolean ordered,
    boolean external,
    boolean composition
) {

    public AssociationRoleDescriptor {
        name = DescriptorValidation.requireText(name, "name");
        if (propertyName != null && propertyName.isBlank()) {
            throw new IllegalArgumentException("propertyName must not be blank");
        }
        validateCardinality(minCardinality, "minCardinality");
        validateCardinality(maxCardinality, "maxCardinality");
        if (maxCardinality >= 0 && minCardinality >= 0 && maxCardinality < minCardinality) {
            throw new IllegalArgumentException(
                "maxCardinality must not be smaller than minCardinality");
        }
    }

    public boolean isUnbounded() {
        return maxCardinality == -1;
    }

    private static void validateCardinality(int value, String fieldName) {
        if (value < -1) {
            throw new IllegalArgumentException(fieldName + " must not be smaller than -1");
        }
    }
}

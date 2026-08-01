package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * Immutable definition of a search field, either a direct text property or a
 * single whitelisted relationship hop.
 */
public record SearchFieldDescriptor(
    String path,
    String criteriaPath,
    boolean relationship,
    String relationshipField,
    String field,
    String alias,
    String targetClass
) {

    public SearchFieldDescriptor {
        path = DescriptorValidation.requireText(path, "path");
        criteriaPath = DescriptorValidation.requireText(criteriaPath, "criteriaPath");
    }

    public boolean isRelationshipHop() {
        return relationship;
    }
}

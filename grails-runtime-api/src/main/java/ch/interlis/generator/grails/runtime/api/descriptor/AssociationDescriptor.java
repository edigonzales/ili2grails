package ch.interlis.generator.grails.runtime.api.descriptor;

import java.util.List;
import java.util.Optional;

/**
 * Immutable descriptor of an association (link entity) as generated into the
 * {@code InterlisAssociationRegistry}.
 */
public record AssociationDescriptor(
    String associationName,
    String iliClassName,
    String domainClassName,
    String controllerName,
    String viewPath,
    String physicalTable,
    String physicalSqlName,
    AssociationStorageKind storageKind,
    boolean writable,
    boolean showInNavigation,
    List<AssociationRoleDescriptor> roles,
    List<AssociationAttributeDescriptor> attributes,
    List<String> diagnostics
) {

    public AssociationDescriptor {
        associationName = DescriptorValidation.requireText(associationName, "associationName");
        storageKind = storageKind == null ? AssociationStorageKind.UNMAPPED : storageKind;
        roles = DescriptorValidation.immutableCopy(roles, "roles");
        attributes = DescriptorValidation.immutableCopy(attributes, "attributes");
        diagnostics = DescriptorValidation.immutableCopy(diagnostics, "diagnostics");
        List<String> roleNames = roles.stream().map(AssociationRoleDescriptor::name).toList();
        DescriptorValidation.requireDistinctNames(roleNames, "roles");
        if (storageKind == AssociationStorageKind.UNMAPPED && writable) {
            throw new IllegalArgumentException(
                "Association '" + associationName + "' is writable but has no storage kind");
        }
    }

    public Optional<AssociationRoleDescriptor> role(String roleName) {
        if (roleName == null) {
            return Optional.empty();
        }
        return roles.stream()
            .filter(role -> roleName.equals(role.name()))
            .findFirst();
    }

    public boolean isBinary() {
        return roles.size() == 2;
    }

    public boolean hasOwnAttributes() {
        return !attributes.isEmpty();
    }
}

package ch.interlis.generator.grails.runtime.api.descriptor;

import java.util.List;

/**
 * Immutable association context: the perspective of one fixed participant
 * role inside an association.
 */
public record AssociationContextDescriptor(
    String id,
    String associationName,
    String participantDomainClassName,
    String fixedRoleName,
    String fixedPropertyName,
    List<String> editableRoleNames,
    List<String> editablePropertyNames,
    String defaultLabel,
    String messageCode,
    String presentation,
    AssociationCreateMode createMode,
    boolean writable,
    boolean removable,
    boolean showAssociationObjectLink,
    int perspectiveMin,
    int perspectiveMax,
    List<String> diagnostics
) {

    public AssociationContextDescriptor {
        id = DescriptorValidation.requireText(id, "id");
        associationName = DescriptorValidation.requireText(associationName, "associationName");
        editableRoleNames = DescriptorValidation.immutableCopy(editableRoleNames, "editableRoleNames");
        editablePropertyNames = DescriptorValidation.immutableCopy(
            editablePropertyNames, "editablePropertyNames");
        diagnostics = DescriptorValidation.immutableCopy(diagnostics, "diagnostics");
        createMode = createMode == null ? AssociationCreateMode.NONE : createMode;
        if (perspectiveMin >= 0 && perspectiveMax >= 0 && perspectiveMax < perspectiveMin) {
            throw new IllegalArgumentException(
                "perspectiveMax must not be smaller than perspectiveMin");
        }
        if (writable && createMode == AssociationCreateMode.NONE) {
            throw new IllegalArgumentException(
                "Writable context '" + id + "' has no supported create mode");
        }
        if (createMode == AssociationCreateMode.QUICK
            && (fixedRoleName == null || fixedRoleName.isBlank())) {
            throw new IllegalArgumentException(
                "QUICK context '" + id + "' requires a fixed role");
        }
    }
}

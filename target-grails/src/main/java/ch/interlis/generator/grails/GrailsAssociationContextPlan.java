package ch.interlis.generator.grails;

import java.util.List;

/**
 * Describes an association from the perspective of exactly one fixed role.
 */
public record GrailsAssociationContextPlan(
    String contextId,
    String messageCode,
    String defaultLabel,
    String participantIliClassName,
    String participantDomainClassName,
    String participantDomainQualifiedName,
    String fixedRoleName,
    String fixedRolePropertyName,
    List<String> editableRoleNames,
    List<String> editableRolePropertyNames,
    Integer perspectiveMinCardinality,
    Integer perspectiveMaxCardinality,
    AssociationPresentationKind presentationKind,
    AssociationCreateMode createMode,
    boolean writable,
    boolean removable,
    boolean showAssociationObjectLink,
    List<String> diagnostics
) {

    public GrailsAssociationContextPlan {
        editableRoleNames = editableRoleNames == null ? List.of() : List.copyOf(editableRoleNames);
        editableRolePropertyNames = editableRolePropertyNames == null
            ? List.of()
            : List.copyOf(editableRolePropertyNames);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}

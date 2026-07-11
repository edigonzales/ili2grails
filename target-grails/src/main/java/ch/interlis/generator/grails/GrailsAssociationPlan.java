package ch.interlis.generator.grails;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic, framework-specific plan for a single INTERLIS association.
 *
 * <p>Lists are defensively copied and stored in a stable order:
 * roles by role name then target class, attributes by domain property name,
 * contexts by context id and diagnostics lexicographically.
 */
public record GrailsAssociationPlan(
    String associationName,
    String associationIliClassName,
    String associationDomainClassName,
    String associationDomainQualifiedName,
    String associationControllerName,
    String associationViewPath,
    String physicalTable,
    String physicalSqlName,
    AssociationStorageKind storageKind,
    boolean physicalMappingPresent,
    boolean writable,
    boolean showInNavigation,
    List<GrailsAssociationRolePlan> roles,
    List<GrailsAssociationAttributePlan> attributes,
    List<GrailsAssociationContextPlan> contexts,
    List<String> diagnostics
) {

    public GrailsAssociationPlan {
        roles = roles == null ? List.of() : roles.stream()
            .sorted(Comparator
                .comparing(GrailsAssociationRolePlan::roleName, Comparator.nullsLast(String::compareTo))
                .thenComparing(GrailsAssociationRolePlan::targetIliClassName, Comparator.nullsLast(String::compareTo)))
            .toList();
        attributes = attributes == null ? List.of() : attributes.stream()
            .sorted(Comparator
                .comparing(GrailsAssociationAttributePlan::domainPropertyName, Comparator.nullsLast(String::compareTo)))
            .toList();
        contexts = contexts == null ? List.of() : contexts.stream()
            .sorted(Comparator
                .comparing(GrailsAssociationContextPlan::contextId, Comparator.nullsLast(String::compareTo)))
            .toList();
        diagnostics = diagnostics == null ? List.of() : diagnostics.stream()
            .sorted(Comparator.nullsLast(String::compareTo))
            .toList();
    }

    public boolean isBinary() {
        return roles.size() == 2;
    }

    public boolean isNary() {
        return roles.size() >= 3;
    }

    public boolean hasOwnAttributes() {
        return !attributes.isEmpty();
    }

    public Optional<GrailsAssociationRolePlan> role(String roleName) {
        if (roleName == null) {
            return Optional.empty();
        }
        return roles.stream()
            .filter(role -> roleName.equals(role.roleName()))
            .findFirst();
    }
}

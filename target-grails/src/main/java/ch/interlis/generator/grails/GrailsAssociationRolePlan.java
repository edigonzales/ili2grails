package ch.interlis.generator.grails;

/**
 * Grails-specific plan for a single INTERLIS association role.
 *
 * <p>Cardinality values follow the core IR convention: {@code null} means unknown,
 * {@code -1} means unbounded ({@code *}), any other value is the literal bound.
 */
public record GrailsAssociationRolePlan(
    String roleName,
    String roleLabel,
    String domainPropertyName,
    String targetIliClassName,
    String targetDomainClassName,
    String targetDomainQualifiedName,
    Integer minCardinality,
    Integer maxCardinality,
    boolean mandatory,
    boolean ordered,
    boolean external,
    boolean composition,
    String physicalName,
    String semanticName
) {

    public boolean isUnbounded() {
        return maxCardinality != null && maxCardinality == -1;
    }

    public boolean isToOne() {
        return maxCardinality != null && maxCardinality == 1;
    }

    public boolean isToMany() {
        return maxCardinality == null || maxCardinality == -1 || maxCardinality > 1;
    }

    /** True when the target class was generated as a persistent Grails domain. */
    public boolean hasResolvedProperty() {
        return domainPropertyName != null && !domainPropertyName.isBlank();
    }
}

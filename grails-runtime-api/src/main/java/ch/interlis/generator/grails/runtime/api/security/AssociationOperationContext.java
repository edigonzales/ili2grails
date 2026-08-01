package ch.interlis.generator.grails.runtime.api.security;

/**
 * Typed context for an association operation (quick-link create or delete).
 */
public record AssociationOperationContext(
    String associationName,
    String contextId,
    String participantDomainClassName,
    String associationDomainClassName
) {
}

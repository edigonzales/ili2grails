package ch.interlis.generator.grails.runtime.api.security;

/**
 * Typed context for an inverse relationship operation (assign or reassign).
 */
public record InverseRelationshipOperationContext(
    String relationshipName,
    String ownerDomainClassName,
    String relatedDomainClassName,
    String relatedPropertyName
) {
}

package ch.interlis.generator.grails.runtime.api.security;

/**
 * Server-side authorization policy for generated runtime operations.
 *
 * <p>Default implementations are provided by the runtime plugin; applications
 * may supply their own bean implementing this contract. Policy decisions must
 * not be based on client-supplied class or property names.</p>
 */
public interface InterlisAuthorizationPolicy {

    boolean canView(DomainOperationContext context);

    boolean canCreate(DomainOperationContext context);

    boolean canUpdate(DomainOperationContext context, Object instance);

    boolean canDelete(DomainOperationContext context, Object instance);

    boolean canCreateAssociation(
        AssociationOperationContext context,
        Object participant,
        Object target
    );

    boolean canDeleteAssociation(
        AssociationOperationContext context,
        Object participant,
        Object associationInstance
    );

    boolean canAssignInverseRelationship(
        InverseRelationshipOperationContext context,
        Object owner,
        Object related
    );

    boolean canReassignInverseRelationship(
        InverseRelationshipOperationContext context,
        Object owner,
        Object previousOwner,
        Object related
    );
}

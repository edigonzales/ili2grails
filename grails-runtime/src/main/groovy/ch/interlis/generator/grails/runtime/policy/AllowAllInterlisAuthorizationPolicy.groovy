package ch.interlis.generator.grails.runtime.policy

import ch.interlis.generator.grails.runtime.api.security.AssociationOperationContext
import ch.interlis.generator.grails.runtime.api.security.DomainOperationContext
import ch.interlis.generator.grails.runtime.api.security.InterlisAuthorizationPolicy
import ch.interlis.generator.grails.runtime.api.security.InverseRelationshipOperationContext

/**
 * Default authorization policy: every generated operation is allowed.
 *
 * <p>This preserves the pre-plugin behavior. Applications that need
 * authorization must register their own {@link InterlisAuthorizationPolicy}
 * bean; the runtime never silently falls back to a broader policy when a
 * custom bean fails to construct.</p>
 */
final class AllowAllInterlisAuthorizationPolicy implements InterlisAuthorizationPolicy {

    @Override
    boolean canView(DomainOperationContext context) {
        return true
    }

    @Override
    boolean canCreate(DomainOperationContext context) {
        return true
    }

    @Override
    boolean canUpdate(DomainOperationContext context, Object instance) {
        return true
    }

    @Override
    boolean canDelete(DomainOperationContext context, Object instance) {
        return true
    }

    @Override
    boolean canCreateAssociation(AssociationOperationContext context,
                                 Object participant,
                                 Object target) {
        return true
    }

    @Override
    boolean canDeleteAssociation(AssociationOperationContext context,
                                 Object participant,
                                 Object associationInstance) {
        return true
    }

    @Override
    boolean canAssignInverseRelationship(InverseRelationshipOperationContext context,
                                         Object owner,
                                         Object related) {
        return true
    }

    @Override
    boolean canReassignInverseRelationship(InverseRelationshipOperationContext context,
                                           Object owner,
                                           Object previousOwner,
                                           Object related) {
        return true
    }
}

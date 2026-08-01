package ch.interlis.generator.grails.runtime.policy

import ch.interlis.generator.grails.runtime.api.security.AssociationOperationContext
import ch.interlis.generator.grails.runtime.api.security.DomainOperationContext
import ch.interlis.generator.grails.runtime.api.security.DomainOperation
import ch.interlis.generator.grails.runtime.api.security.InverseRelationshipOperationContext
import spock.lang.Specification

class AllowAllInterlisAuthorizationPolicySpec extends Specification {

    private static final DomainOperationContext VIEW = new DomainOperationContext(
        DomainOperation.VIEW, 'com.example.P', 'M.T.P')
    private static final AssociationOperationContext ASSOC = new AssociationOperationContext(
        'M.T.Assoc', 'ctx-1', 'com.example.P', 'com.example.Assoc')
    private static final InverseRelationshipOperationContext INVERSE =
        new InverseRelationshipOperationContext('children', 'com.example.P', 'com.example.C', 'parent')

    def "allows every operation"() {
        given:
        def policy = new AllowAllInterlisAuthorizationPolicy()
        def instance = new Object()

        expect:
        policy.canView(VIEW)
        policy.canCreate(VIEW)
        policy.canUpdate(VIEW, instance)
        policy.canDelete(VIEW, instance)
        policy.canCreateAssociation(ASSOC, instance, instance)
        policy.canDeleteAssociation(ASSOC, instance, instance)
        policy.canAssignInverseRelationship(INVERSE, instance, instance)
        policy.canReassignInverseRelationship(INVERSE, instance, instance, instance)
    }
}

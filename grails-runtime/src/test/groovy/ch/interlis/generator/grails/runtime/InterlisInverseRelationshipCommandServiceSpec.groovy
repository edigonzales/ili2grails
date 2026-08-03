package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.command.CommandCode
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DisplayDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipMode
import ch.interlis.generator.grails.runtime.api.persistence.LockResult
import ch.interlis.generator.grails.runtime.api.persistence.RuntimeRecordLoader
import ch.interlis.generator.grails.runtime.api.registry.AssociationRegistry
import ch.interlis.generator.grails.runtime.api.registry.DomainRegistry
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry
import ch.interlis.generator.grails.runtime.api.registry.RuntimeClassResolver
import ch.interlis.generator.grails.runtime.api.security.InterlisAuthorizationPolicy
import ch.interlis.generator.grails.runtime.config.InterlisRuntimeOverridesService
import ch.interlis.generator.grails.runtime.registry.InterlisRuntimeSafetyState
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import spock.lang.Specification

class InterlisInverseRelationshipCommandServiceSpec extends Specification {

    def "assigns an unowned related record"() {
        given:
        def owner = new Owner(id: 1L, name: 'New owner')
        def related = new Related(id: 2L, name: 'Related')
        def service = service(owner, related, true, true)

        when:
        def result = assign(service, 1L, 2L, false)

        then:
        result.success()
        result.code() == CommandCode.ASSIGNED
        related.owner.is(owner)
        related.saveCalls == 1
    }

    def "authorization denial leaves the related record unchanged"() {
        given:
        def previous = new Owner(id: 9L, name: 'Previous owner')
        def owner = new Owner(id: 1L, name: 'New owner')
        def related = new Related(id: 2L, name: 'Related', owner: previous)
        def service = service(owner, related, false, false)

        when:
        def result = assign(service, 1L, 2L, true)

        then:
        !result.success()
        result.code() == CommandCode.FORBIDDEN
        related.owner.is(previous)
        related.saveCalls == 0
    }

    def "reassignment requires confirmation and does not mutate early"() {
        given:
        def previous = new Owner(id: 9L, name: 'Previous owner')
        def owner = new Owner(id: 1L, name: 'New owner')
        def related = new Related(id: 2L, name: 'Related', owner: previous)
        def service = service(owner, related, true, true)

        when:
        def result = assign(service, 1L, 2L, false)

        then:
        result.code() == CommandCode.REASSIGNMENT_CONFIRMATION_REQUIRED
        result.reassignmentConfirmation() != null
        related.owner.is(previous)
        related.saveCalls == 0
    }

    def "database and concurrency failures leave no partial assignment"() {
        given:
        def owner = new Owner(id: 1L, name: 'Owner')
        def related = new Related(
            id: 2L,
            name: 'Related',
            saveFailure: new DataIntegrityViolationException('constraint'))
        def databaseFailureService = service(owner, related, true, true)

        when:
        def databaseResult = assign(databaseFailureService, 1L, 2L, false)

        then:
        databaseResult.code() == CommandCode.DATA_INTEGRITY
        related.owner == null
        related.saveCalls == 1

        when:
        RuntimeRecordLoader loader = Stub() {
            lock(Owner, 1L) >> LockResult.locked(owner)
            lock(Related, 2L) >> LockResult.failed(
                new OptimisticLockingFailureException('stale'))
        }
        def concurrentService = configuredService(loader, true, true)
        def concurrentResult = assign(concurrentService, 1L, 2L, false)

        then:
        concurrentResult.code() == CommandCode.CONCURRENT_MODIFICATION
        related.owner == null
        related.saveCalls == 1
    }

    private InterlisInverseRelationshipCommandService service(Owner owner,
                                                               Related related,
                                                               boolean assignAllowed,
                                                               boolean reassignAllowed) {
        RuntimeRecordLoader loader = Stub() {
            lock(Owner, 1L) >> LockResult.locked(owner)
            lock(Related, 2L) >> LockResult.locked(related)
        }
        return configuredService(loader, assignAllowed, reassignAllowed)
    }

    private static def assign(InterlisInverseRelationshipCommandService service,
                              Serializable ownerId,
                              Serializable relatedId,
                              boolean confirmReassignment) {
        return service.'$tt__assign'(
            Owner, ownerId, 'related', relatedId, confirmReassignment, null)
    }

    private InterlisInverseRelationshipCommandService configuredService(
        RuntimeRecordLoader loader,
        boolean assignAllowed,
        boolean reassignAllowed
    ) {
        InterlisAuthorizationPolicy policy = Stub() {
            canAssignInverseRelationship(_, _, _) >> assignAllowed
            canReassignInverseRelationship(_, _, _, _) >> reassignAllowed
        }
        return new InterlisInverseRelationshipCommandService(
            grailsApplication: null,
            runtimeRegistry: registry(),
            authorizationPolicy: policy,
            recordLoader: loader,
            overridesService: new InterlisRuntimeOverridesService(),
            runtimeSafetyState: new InterlisRuntimeSafetyState()
        )
    }

    private InterlisRuntimeRegistry registry() {
        InverseRelationshipDescriptor relationship = new InverseRelationshipDescriptor(
            'related', 'Related records', 'Test.Owner', 'Test.Related',
            Related.name, 'related', 'owner', 'Owner', false,
            true, true, InverseRelationshipMode.AUTO)
        List<DomainDescriptor> domainDescriptors = [
            domain(Owner, 'owner', [related: relationship]),
            domain(Related, 'related', [:])
        ]
        DomainRegistry domainRegistry = Stub() {
            domains() >> domainDescriptors
        }
        AssociationRegistry associationRegistry = Stub() {
            associations() >> ([] as Collection<AssociationDescriptor>)
            contexts() >> ([] as Collection<AssociationContextDescriptor>)
        }
        Map<String, Class> types = [(Owner.name): Owner, (Related.name): Related]
        return new InterlisRuntimeRegistry(
            domainRegistry,
            associationRegistry,
            ({ String name -> types[name] } as RuntimeClassResolver)
        )
    }

    private static DomainDescriptor domain(Class type,
                                           String controller,
                                           Map<String, InverseRelationshipDescriptor> inverse) {
        return new DomainDescriptor(
            "Test.Topic.${type.simpleName}", 'Test', 'Test.Topic', type.name,
            controller, type.simpleName, type.simpleName, DomainKind.CLASS, true,
            new DisplayDescriptor(type.simpleName, ['name'], ['name']),
            [:], [:], inverse, [:]
        )
    }

    static class Owner {
        Long id
        String name
    }

    static class Related {
        Long id
        String name
        Owner owner
        Throwable saveFailure
        int saveCalls
        boolean valid = true
        boolean errorsPresent
        def errors = [fieldErrors: []]

        boolean validate() {
            return valid
        }

        Object save(Map arguments) {
            saveCalls++
            if (saveFailure != null) {
                throw saveFailure
            }
            return this
        }

        boolean hasErrors() {
            return errorsPresent
        }
    }
}

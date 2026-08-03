package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.command.CommandCode
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationCreateMode
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind
import ch.interlis.generator.grails.runtime.api.descriptor.DisplayDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind
import ch.interlis.generator.grails.runtime.api.persistence.LockResult
import ch.interlis.generator.grails.runtime.api.persistence.RuntimeRecordLoader
import ch.interlis.generator.grails.runtime.api.registry.AssociationRegistry
import ch.interlis.generator.grails.runtime.api.registry.DomainRegistry
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry
import ch.interlis.generator.grails.runtime.api.registry.RuntimeClassResolver
import ch.interlis.generator.grails.runtime.api.security.InterlisAuthorizationPolicy
import ch.interlis.generator.grails.runtime.registry.InterlisRuntimeSafetyState
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import spock.lang.Specification

class InterlisAssociationCommandServiceSpec extends Specification {

    def setup() {
        AssociationLink.reset()
    }

    def "creates a quick link through typed descriptors"() {
        given:
        AssociationLink.criteriaResults.addAll([0L, 0L])
        def owner = new Owner(id: 1L, name: 'Owner')
        def target = new Target(id: 2L, name: 'Target')
        def service = service(owner, target, true, 2)

        when:
        def result = create(service, 1L, 2L)

        then:
        result.success()
        result.code() == CommandCode.CREATED
        result.associationId() == '77'
        AssociationLink.lastSaved.owner.is(owner)
        AssociationLink.lastSaved.target.is(target)
    }

    def "authorization denial creates no transient or persistent link"() {
        given:
        def service = service(
            new Owner(id: 1L, name: 'Owner'),
            new Target(id: 2L, name: 'Target'),
            false,
            2)

        when:
        def result = create(service, 1L, 2L)

        then:
        result.code() == CommandCode.FORBIDDEN
        AssociationLink.lastSaved == null
        AssociationLink.createdInstances == 0
    }

    def "cardinality prevents a second link before mutation"() {
        given:
        AssociationLink.criteriaResults.addAll([0L, 1L])
        def service = service(
            new Owner(id: 1L, name: 'Owner'),
            new Target(id: 2L, name: 'Target'),
            true,
            1)

        when:
        def result = create(service, 1L, 2L)

        then:
        result.code() == CommandCode.CARDINALITY_MAX_EXCEEDED
        AssociationLink.lastSaved == null
        AssociationLink.createdInstances == 0
    }

    def "database and concurrency failures do not persist a partial link"() {
        given:
        AssociationLink.criteriaResults.addAll([0L, 0L])
        AssociationLink.saveFailure = new DataIntegrityViolationException('constraint')
        def owner = new Owner(id: 1L, name: 'Owner')
        def target = new Target(id: 2L, name: 'Target')
        def databaseFailureService = service(owner, target, true, 2)

        when:
        def databaseResult = create(databaseFailureService, 1L, 2L)

        then:
        databaseResult.code() == CommandCode.DATA_INTEGRITY
        AssociationLink.lastSaved == null

        when:
        AssociationLink.reset()
        RuntimeRecordLoader loader = Stub() {
            lock(Owner, 1L) >> LockResult.failed(
                new OptimisticLockingFailureException('stale'))
        }
        def concurrentService = configuredService(loader, true, 2)
        def concurrentResult = create(concurrentService, 1L, 2L)

        then:
        concurrentResult.code() == CommandCode.CONCURRENT_MODIFICATION
        AssociationLink.lastSaved == null
        AssociationLink.createdInstances == 0
    }

    private InterlisAssociationCommandService service(Owner owner,
                                                       Target target,
                                                       boolean createAllowed,
                                                       int perspectiveMax) {
        RuntimeRecordLoader loader = Stub() {
            lock(Owner, 1L) >> LockResult.locked(owner)
            get(Target, 2L) >> target
        }
        return configuredService(loader, createAllowed, perspectiveMax)
    }

    private InterlisAssociationCommandService configuredService(RuntimeRecordLoader loader,
                                                                 boolean createAllowed,
                                                                 int perspectiveMax) {
        InterlisAuthorizationPolicy policy = Stub() {
            canCreateAssociation(_, _, _) >> createAllowed
        }
        return new InterlisAssociationCommandService(
            grailsApplication: null,
            runtimeRegistry: registry(perspectiveMax),
            authorizationPolicy: policy,
            recordLoader: loader,
            runtimeSafetyState: new InterlisRuntimeSafetyState()
        )
    }

    private static def create(InterlisAssociationCommandService service,
                              Serializable ownerId,
                              Serializable targetId) {
        return service.'$tt__createQuickLink'(
            Owner, ownerId, 'owner-links', 'targets', targetId, null)
    }

    private InterlisRuntimeRegistry registry(int perspectiveMax) {
        AssociationRoleDescriptor ownerRole = new AssociationRoleDescriptor(
            'owners', 'Owner', 'owner', 'Test.Owner', Owner.name,
            0, -1, false, false, false, false)
        AssociationRoleDescriptor targetRole = new AssociationRoleDescriptor(
            'targets', 'Target', 'target', 'Test.Target', Target.name,
            0, -1, false, false, false, false)
        AssociationDescriptor association = new AssociationDescriptor(
            'Test.Topic.OwnerTarget', 'Test.Topic.OwnerTarget', AssociationLink.name,
            'ownerTarget', 'ownerTarget', 'owner_target', 'owner_target',
            AssociationStorageKind.LINK_ENTITY, true, false,
            [ownerRole, targetRole], [], [])
        AssociationContextDescriptor context = new AssociationContextDescriptor(
            'owner-links', association.associationName(), Owner.name,
            'owners', 'owner', ['targets'], ['target'], 'Targets',
            'test.owner.targets', 'inline', AssociationCreateMode.QUICK,
            true, true, false, 0, perspectiveMax, [])

        List<DomainDescriptor> domainDescriptors = [
            domain(Owner, 'owner', DomainKind.CLASS, true),
            domain(Target, 'target', DomainKind.CLASS, true),
            domain(AssociationLink, 'ownerTarget', DomainKind.ASSOCIATION, false)
        ]
        DomainRegistry domainRegistry = Stub() {
            domains() >> domainDescriptors
        }
        AssociationRegistry associationRegistry = Stub() {
            associations() >> [association]
            contexts() >> [context]
        }
        Map<String, Class> types = [
            (Owner.name): Owner,
            (Target.name): Target,
            (AssociationLink.name): AssociationLink
        ]
        return new InterlisRuntimeRegistry(
            domainRegistry,
            associationRegistry,
            ({ String name -> types[name] } as RuntimeClassResolver)
        )
    }

    private static DomainDescriptor domain(Class type,
                                           String controller,
                                           DomainKind kind,
                                           boolean visible) {
        return new DomainDescriptor(
            "Test.Topic.${type.simpleName}", 'Test', 'Test.Topic', type.name,
            controller, type.simpleName, type.simpleName, kind, visible,
            new DisplayDescriptor(type.simpleName, ['name'], ['name']),
            [:], [:], [:], [:]
        )
    }

    static class Owner {
        Long id
        String name
    }

    static class Target {
        Long id
        String name
    }

    static class AssociationLink {
        static final Deque<Long> criteriaResults = new ArrayDeque<>()
        static Throwable saveFailure
        static AssociationLink lastSaved
        static int createdInstances

        Long id
        Owner owner
        Target target
        def errors = [fieldErrors: []]

        AssociationLink() {
            createdInstances++
        }

        static void reset() {
            criteriaResults.clear()
            saveFailure = null
            lastSaved = null
            createdInstances = 0
        }

        static Object createCriteria() {
            return new CriteriaFixture()
        }

        boolean validate() {
            return true
        }

        Object save(Map arguments) {
            if (saveFailure != null) {
                throw saveFailure
            }
            id = 77L
            lastSaved = this
            return this
        }

        boolean hasErrors() {
            return false
        }

        void delete(Map arguments) {
            lastSaved = null
        }

        static class CriteriaFixture {
            Object get(Closure query) {
                query.delegate = this
                query.resolveStrategy = Closure.DELEGATE_FIRST
                query.call()
                return AssociationLink.criteriaResults.removeFirst()
            }

            void eq(String property, Object value) {
            }

            void projections(Closure projection) {
                projection.delegate = this
                projection.resolveStrategy = Closure.DELEGATE_FIRST
                projection.call()
            }

            void count(String property) {
            }
        }
    }
}

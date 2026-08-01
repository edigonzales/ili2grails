package ch.interlis.generator.grails.runtime.controller

/**
 * Typed context handed to the controller flows. All dependencies are injected
 * by the Grails container; the flows never look up services.
 *
 * @param <T> domain type of the generated controller
 */
final class InterlisControllerContext<T> {

    final Class<T> domainType
    final Object crudService
    final Object associationQueryService
    final Object associationCommandService
    final Object inverseRelationshipQueryService
    final Object inverseRelationshipCommandService
    final Object grailsApplication
    final ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry runtimeRegistry
    final ch.interlis.generator.grails.runtime.api.security.InterlisAuthorizationPolicy authorizationPolicy

    InterlisControllerContext(Class<T> domainType,
                              Object crudService,
                              Object associationQueryService,
                              Object associationCommandService,
                              Object inverseRelationshipQueryService,
                              Object inverseRelationshipCommandService,
                              Object grailsApplication,
                              ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry runtimeRegistry,
                              ch.interlis.generator.grails.runtime.api.security.InterlisAuthorizationPolicy authorizationPolicy) {
        this.domainType = domainType
        this.crudService = crudService
        this.associationQueryService = associationQueryService
        this.associationCommandService = associationCommandService
        this.inverseRelationshipQueryService = inverseRelationshipQueryService
        this.inverseRelationshipCommandService = inverseRelationshipCommandService
        this.grailsApplication = grailsApplication
        this.runtimeRegistry = runtimeRegistry
        this.authorizationPolicy = authorizationPolicy
    }
}

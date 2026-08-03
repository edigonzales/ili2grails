package ch.interlis.generator.grails.runtime.registry

import ch.interlis.generator.grails.runtime.api.registry.AssociationRegistry
import ch.interlis.generator.grails.runtime.api.registry.DomainRegistry
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry
import ch.interlis.generator.grails.runtime.api.registry.RuntimeClassResolver
import ch.interlis.generator.grails.runtime.GeneratedRegistryAccessor
import org.springframework.beans.factory.FactoryBean

/**
 * Builds the {@link InterlisRuntimeRegistry} from the generated registry
 * classes of the host application.
 *
 * <p>The generated registries are static singletons in the fixed package
 * {@code ch.interlis.generator.grails.generated}; the plugin resolves them
 * once at startup and fails with a readable message when they are missing.</p>
 */
final class InterlisRuntimeRegistryBeanFactory implements FactoryBean<InterlisRuntimeRegistry> {

    static final String GENERATED_REGISTRY_PACKAGE = 'ch.interlis.generator.grails.generated'

    def grailsApplication

    private volatile InterlisRuntimeRegistry instance

    @Override
    InterlisRuntimeRegistry getObject() throws Exception {
        if (instance == null) {
            synchronized (this) {
                if (instance == null) {
                    instance = buildRegistry()
                }
            }
        }
        return instance
    }

    @Override
    Class<?> getObjectType() {
        return InterlisRuntimeRegistry
    }

    @Override
    boolean isSingleton() {
        return true
    }

    private InterlisRuntimeRegistry buildRegistry() {
        DomainRegistry domainRegistry = requireContract(
            DomainRegistry, GeneratedRegistryAccessor.uiRegistryInstance(), 'InterlisUiRegistry')
        AssociationRegistry associationRegistry = requireContract(
            AssociationRegistry, GeneratedRegistryAccessor.associationRegistryInstance(),
            'InterlisAssociationRegistry')
        RuntimeClassResolver resolver = new GrailsRuntimeClassResolver(grailsApplication)
        return new InterlisRuntimeRegistry(domainRegistry, associationRegistry, resolver)
    }

    private static <T> T requireContract(Class<T> contract, Object instance, String simpleName) {
        if (!contract.isInstance(instance)) {
            throw new IllegalStateException(
                "Generated registry ${GENERATED_REGISTRY_PACKAGE}.${simpleName} " +
                "does not implement ${contract.name}")
        }
        return contract.cast(instance)
    }
}

package ch.interlis.generator.grails.runtime.registry

import ch.interlis.generator.grails.runtime.api.registry.RuntimeClassResolver

/**
 * Resolves qualified class names through the Grails application classloader
 * and caches the results.
 */
final class GrailsRuntimeClassResolver implements RuntimeClassResolver {

    private final def grailsApplication
    private final Map<String, Class<?>> cache = new java.util.concurrent.ConcurrentHashMap<>()

    GrailsRuntimeClassResolver(def grailsApplication) {
        this.grailsApplication = grailsApplication
    }

    @Override
    Class<?> resolve(String qualifiedClassName) {
        if (qualifiedClassName == null || qualifiedClassName.isBlank()) {
            return null
        }
        return cache.computeIfAbsent(qualifiedClassName, { String name ->
            resolveOnce(name)
        })
    }

    private Class<?> resolveOnce(String qualifiedClassName) {
        ClassLoader classLoader = grailsApplication?.classLoader
            ?: Thread.currentThread().contextClassLoader
        try {
            return classLoader?.loadClass(qualifiedClassName)
        } catch (Exception ignored) {
            return null
        }
    }
}

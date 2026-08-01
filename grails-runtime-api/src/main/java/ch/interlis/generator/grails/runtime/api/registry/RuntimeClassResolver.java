package ch.interlis.generator.grails.runtime.api.registry;

/**
 * Resolves qualified class names to runtime classes. Implementations cache
 * the resolution; the runtime API itself stays classloader agnostic.
 */
public interface RuntimeClassResolver {

    Class<?> resolve(String qualifiedClassName);
}

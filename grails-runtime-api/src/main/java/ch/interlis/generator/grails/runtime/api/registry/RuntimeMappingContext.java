package ch.interlis.generator.grails.runtime.api.registry;

/**
 * Minimal mapping context consumed by registry validation. Implemented by the
 * Grails runtime against {@code GrailsApplication.mappingContext}; kept as an
 * interface so the API module stays dependency neutral.
 */
public interface RuntimeMappingContext {

    boolean hasDomainClass(String qualifiedClassName);

    boolean hasProperty(String qualifiedClassName, String propertyName);

    boolean isAssociationProperty(String qualifiedClassName, String propertyName);

    boolean hasEnumValue(String enumClassName, String valueName);
}

package ch.interlis.generator.grails.runtime.display

import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor
import ch.interlis.generator.grails.runtime.api.display.InterlisDisplayLabelResolver

/**
 * Default display label resolver: uses the generated display fields and falls
 * back to the record id.
 */
final class DefaultInterlisDisplayLabelResolver implements InterlisDisplayLabelResolver {

    @Override
    String labelFor(Object domainInstance, DomainDescriptor descriptor) {
        if (domainInstance == null) {
            return ''
        }
        List<String> displayFields = descriptor?.display()?.displayFields() ?: []
        for (String field : displayFields) {
            Object value = readProperty(domainInstance, field)
            if (value != null && !value.toString().isBlank()) {
                return value.toString()
            }
        }
        return readProperty(domainInstance, 'id')?.toString() ?: ''
    }

    private static Object readProperty(Object instance, String propertyName) {
        if (instance == null || propertyName == null || propertyName.isBlank()) {
            return null
        }
        try {
            return instance."${propertyName}"
        } catch (MissingPropertyException ignored) {
            return null
        } catch (Exception ignored) {
            return null
        }
    }
}

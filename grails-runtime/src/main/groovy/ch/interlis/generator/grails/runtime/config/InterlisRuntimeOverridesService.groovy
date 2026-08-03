package ch.interlis.generator.grails.runtime.config

import ch.interlis.generator.grails.runtime.api.config.RuntimeUiOverrides
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipMode
import groovy.util.logging.Slf4j

/**
 * Applies typed runtime UI overrides to generated descriptors.
 *
 * <p>Overrides may only restrict generated behavior: the generated writable
 * capability can be downgraded (read-only, off) but never upgraded. Applying
 * an override always creates a new descriptor instance.</p>
 */
@Slf4j
final class InterlisRuntimeOverridesService {

    private static final Set<String> ALLOWED_MODES =
        ['auto', 'editable', 'read-only', 'off'] as Set<String>

    def grailsApplication

    RuntimeUiOverrides overridesFor(DomainDescriptor domain) {
        if (grailsApplication == null || domain == null) {
            return RuntimeUiOverrides.none()
        }
        Object rawDomains = grailsApplication.config?.ili2grails?.ui?.domains
        if (rawDomains == null) {
            return RuntimeUiOverrides.none()
        }
        List<?> domains = normalizeList(rawDomains)
        for (int index = 0; index < domains.size(); index++) {
            Map<String, Object> configured = asMap(domains.get(index))
            String configuredIliName = configured.iliName?.toString()
            if (configuredIliName == null || configuredIliName.isBlank()) {
                throw new IllegalArgumentException(
                    "Invalid ili2grails.ui.domains[" + index + "]: iliName is required")
            }
            if (configuredIliName == domain.iliName()) {
                return parseDomainOverrides(configured)
            }
        }
        return RuntimeUiOverrides.none()
    }

    /**
     * Applies relationship overrides (mode/label) to the generated inverse
     * relationship descriptor. The generated writable capability can be
     * downgraded but never upgraded.
     */
    InverseRelationshipDescriptor applyInverseRelationshipOverrides(
        InverseRelationshipDescriptor generated,
        RuntimeUiOverrides overrides) {
        if (generated == null) {
            return null
        }
        RuntimeUiOverrides.RelationshipOverride configured = overrides.relationships()[generated.name()]
        if (configured == null || configured.isEmpty()) {
            return generated
        }
        String modeValue = configured.mode() ?: 'auto'
        if (!ALLOWED_MODES.contains(modeValue)) {
            throw new IllegalArgumentException(
                "Invalid relationship mode '${modeValue}' for '${generated.name()}'; " +
                "expected auto, editable, read-only or off")
        }
        InverseRelationshipMode mode = switch (modeValue) {
            case 'editable' -> InverseRelationshipMode.EDITABLE
            case 'read-only' -> InverseRelationshipMode.READ_ONLY
            case 'off' -> InverseRelationshipMode.OFF
            default -> InverseRelationshipMode.AUTO
        }
        String label = configured.label() ?: generated.label()
        if (configured.label() != null && configured.label().isBlank()) {
            throw new IllegalArgumentException(
                "Invalid relationship label for '${generated.name()}': label must not be blank")
        }
        boolean visible = mode != InverseRelationshipMode.OFF
        return new InverseRelationshipDescriptor(
            generated.name(),
            label,
            generated.ownerIliClassName(),
            generated.relatedIliClassName(),
            generated.relatedDomainClassName(),
            generated.relatedControllerName(),
            generated.relatedPropertyName(),
            generated.relatedLabel(),
            generated.mandatory(),
            generated.generatedWritable(),
            visible,
            mode
        )
    }

    private RuntimeUiOverrides parseDomainOverrides(Map<String, Object> configured) {
        String label = configured.label?.toString()
        Map<String, RuntimeUiOverrides.RelationshipOverride> relationships = [:]
        Map<String, Object> rawRelationships = asMap(configured.relationships)
        rawRelationships.each { String name, Object raw ->
            Map<String, Object> relationship = asMap(raw)
            String mode = relationship.mode?.toString()
            String relationshipLabel = relationship.label?.toString()
            if (relationshipLabel != null && relationshipLabel.isBlank()) {
                throw new IllegalArgumentException(
                    "Invalid relationship label for '${name}': label must not be blank")
            }
            relationships[name] = new RuntimeUiOverrides.RelationshipOverride(mode, relationshipLabel)
        }
        return new RuntimeUiOverrides(label, null, null, relationships)
    }

    private static List<?> normalizeList(Object value) {
        if (value == null) {
            return []
        }
        if (value instanceof Collection) {
            return value as List
        }
        if (value instanceof Map) {
            return value.isEmpty() ? [] : [value]
        }
        throw new IllegalArgumentException(
            "Expected a list or map configuration value but got " + value.class.name)
    }

    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? value as Map<String, Object> : [:]
    }
}

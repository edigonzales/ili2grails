package ch.interlis.generator.grails.runtime


import org.locationtech.jts.geom.Geometry

import java.time.temporal.TemporalAccessor
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Builds the display-only view model for a generated domain workspace.
 *
 * Relationship and association semantics deliberately remain in the existing
 * relationship/association runtime. This class only turns already whitelisted
 * metadata and loaded objects into GSP-friendly display models.
 */
final class InterlisWorkspaceSupport {

    private InterlisWorkspaceSupport() {
    }

    static Map<String, Object> showModel(def grailsApplication,
                                         Class domainType,
                                         Object instance,
                                         Map<String, Object> descriptor) {
        if (instance == null) {
            return [
                workspaceDisplayLabel   : "",
                workspaceDomainLabel    : descriptor?.label?.toString() ?: domainType?.simpleName,
                workspaceDetailSections : [],
                workspaceRelationshipLinks: []
            ]
        }

        return [
            workspaceDisplayLabel      : displayLabel(
                grailsApplication,
                instance,
                descriptor?.list?.displayFields instanceof Collection
                    ? descriptor.list.displayFields as Collection<String>
                    : []
            ),
            workspaceDomainLabel       : descriptor?.label?.toString() ?: domainType?.simpleName,
            workspaceDetailSections    : detailSections(grailsApplication, instance, descriptor),
            workspaceRelationshipLinks : relationshipLinks(grailsApplication, instance, descriptor)
        ]
    }

    static String displayLabel(Object value) {
        return displayLabel(null, value, [])
    }

    static String displayLabel(def grailsApplication, Object value) {
        return displayLabel(grailsApplication, value, [])
    }

    static String displayLabel(def grailsApplication,
                               Object value,
                               Collection<String> displayFields) {
        if (value == null) {
            return ""
        }
        String label
        if (displayFields) {
            label = hasConfiguredDisplayValue(value, displayFields)
                ? InterlisRelationshipOptions.optionLabel(value, displayFields)
                : null
        } else {
            label = grailsApplication != null
                ? InterlisRelationshipOptions.displayLabel(grailsApplication, value)
                : InterlisRelationshipOptions.displayLabel(value)
        }
        if (label != null && !label.isBlank()) {
            return label
        }
        Object id = readProperty(value, "id")
        return id == null ? "" : "#${id}"
    }

    static String renderValue(Object value) {
        return renderValue(null, value)
    }

    static String renderValue(def grailsApplication, Object value) {
        if (value == null) {
            return ""
        }
        if (value instanceof Enum) {
            return ((Enum) value).name()
        }
        if (value instanceof TemporalAccessor) {
            return value.toString()
        }
        if (value instanceof Date) {
            return value.format("yyyy-MM-dd HH:mm:ss")
        }
        if (value instanceof Collection) {
            return ((Collection) value).collect { Object item -> renderValue(grailsApplication, item) }
                .findAll { String item -> item != null && !item.isBlank() }
                .join(", ")
        }
        if (value instanceof Geometry) {
            return value.geometryType
        }
        String relationshipLabel = grailsApplication != null
            ? InterlisRelationshipOptions.displayLabel(grailsApplication, value)
            : InterlisRelationshipOptions.displayLabel(value)
        if (relationshipLabel != null) {
            return relationshipLabel
        }
        return value.toString()
    }

    /**
     * Creates the small, display-only contract consumed by workspace-table.gsp.
     * The caller owns query and domain semantics; this method only normalises
     * presentation data and keeps insertion order stable for deterministic views.
     */
    static Map<String, Object> tableSection(String key,
                                             String title,
                                             Collection<Map<String, Object>> columns,
                                             Collection<Map<String, Object>> rows,
                                             String emptyMessage = null) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Workspace table section key must not be blank")
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Workspace table section title must not be blank for ${key}")
        }
        List<Map<String, Object>> safeColumns = (columns ?: []).collect { Map<String, Object> column ->
            new LinkedHashMap<String, Object>(column ?: [:])
        }
        List<Map<String, Object>> safeRows = (rows ?: []).collect { Map<String, Object> row ->
            new LinkedHashMap<String, Object>(row ?: [:])
        }
        Map<String, Object> result = [
            key          : key,
            title        : title,
            columns      : Collections.unmodifiableList(safeColumns),
            rows         : Collections.unmodifiableList(safeRows),
            count        : safeRows.size(),
            emptyMessage : emptyMessage ?: "Für diesen Bereich sind keine Einträge vorhanden."
        ]
        return Collections.unmodifiableMap(result)
    }

    /** Creates a row with values and optional per-cell navigation links. */
    static Map<String, Object> tableRow(Map<String, Object> values,
                                        Map<String, Map<String, Object>> links = [:]) {
        Map<String, Object> safeValues = new LinkedHashMap<String, Object>(values ?: [:])
        Map<String, Map<String, Object>> safeLinks = new LinkedHashMap<>()
        (links ?: [:]).each { String key, Map<String, Object> link ->
            safeLinks[key] = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(link ?: [:]))
        }
        return Collections.unmodifiableMap([
            values: Collections.unmodifiableMap(safeValues),
            links : Collections.unmodifiableMap(safeLinks)
        ])
    }

    private static List<Map<String, Object>> detailSections(def grailsApplication,
                                                             Object instance,
                                                             Map<String, Object> descriptor) {
        List<Map<String, Object>> sections = descriptor?.detail?.sections instanceof Collection
            ? descriptor.detail.sections as List<Map<String, Object>>
            : []
        return sections.collect { Map<String, Object> section ->
            List<Map<String, Object>> fields = (section.fields ?: []).collect { Object rawField ->
                String fieldName = rawField.toString()
                Object value = readProperty(instance, fieldName)
                Map<String, Object> field = [
                    name : fieldName,
                    label: fieldLabel(descriptor, fieldName),
                    value: renderValue(grailsApplication, value)
                ]
                Map<String, Object> link = relationshipLinkForField(
                    grailsApplication, instance, descriptor, fieldName, value
                )
                if (link != null) {
                    field.link = link
                }
                field
            }
            [title: section.title?.toString() ?: localizedMessage(
                grailsApplication, "ili2grails.workspace.details", "Details", "Details"), fields: fields]
        }.findAll { Map<String, Object> section -> !section.fields.isEmpty() }
    }

    private static Map<String, Object> relationshipLinkForField(def grailsApplication,
                                                                 Object instance,
                                                                 Map<String, Object> descriptor,
                                                                 String fieldName,
                                                                 Object value) {
        if (value == null || value instanceof Collection) {
            return null
        }
        Map<String, Object> relationships = descriptor?.relationships instanceof Map
            ? descriptor.relationships as Map<String, Object>
            : [:]
        Map<String, Object> relationship = relationships[fieldName] instanceof Map
            ? relationships[fieldName] as Map<String, Object>
            : null
        if (relationship == null) {
            return null
        }
        Map<String, Object> registry = registryEntry(value, relationship.targetClass?.toString())
        String id = readProperty(value, "id")?.toString()
        String controller = registry?.controller?.toString()
        if (registry == null || id == null || controller == null || controller.isBlank()) {
            return null
        }
        return [
            controller: controller,
            action    : "show",
            id        : id,
            label     : renderValue(grailsApplication, value)
        ]
    }

    private static String localizedMessage(def grailsApplication,
                                           String code,
                                           String germanDefault,
                                           String englishDefault) {
        String language = grailsApplication?.config?.ili2grails?.language?.toString()
        String fallback = language == "en" ? englishDefault : germanDefault
        try {
            def source = grailsApplication?.mainContext?.getBean(
                "org.springframework.context.MessageSource"
            )
            return source?.getMessage(code, null, fallback, Locale.forLanguageTag(language ?: "de-CH")) ?: fallback
        } catch (Exception ignored) {
            return fallback
        }
    }

    private static List<Map<String, Object>> relationshipLinks(def grailsApplication,
                                                                Object instance,
                                                                Map<String, Object> descriptor) {
        Set<String> integratedFields = descriptor?.detail?.sections instanceof Collection
            ? descriptor.detail.sections.collectMany { Map<String, Object> section ->
                (section.fields ?: []).collect { it.toString() }
            } as Set<String>
            : [] as Set<String>
        Map<String, Object> relationshipMeta = descriptor?.relationships instanceof Map
            ? descriptor.relationships as Map<String, Object>
            : [:]
        relationshipMeta.collect { String fieldName, Object rawMeta ->
            if (integratedFields.contains(fieldName)) {
                return null
            }
            Map<String, Object> meta = rawMeta instanceof Map ? rawMeta as Map<String, Object> : [:]
            Object target = readProperty(instance, fieldName)
            if (target instanceof Collection) {
                return null
            }
            Map<String, Object> registryEntry = registryEntry(target, meta.targetClass?.toString())
            if (registryEntry == null) {
                return null
            }
            if (target == null) {
                return [
                    name       : fieldName,
                    label      : meta.label?.toString() ?: humanize(fieldName),
                    valueLabel : "",
                    id         : null,
                    controller : registryEntry.controller?.toString(),
                    empty      : true
                ]
            }

            String targetId = readProperty(target, "id")?.toString()
            [
                name       : fieldName,
                label      : meta.label?.toString() ?: humanize(fieldName),
                valueLabel : InterlisRelationshipOptions.optionLabel(grailsApplication, target),
                id         : targetId,
                controller : registryEntry?.controller?.toString(),
                empty      : false
            ]
        }.findAll { it != null } as List<Map<String, Object>>
    }

    private static String fieldLabel(Map<String, Object> descriptor, String fieldName) {
        Map<String, Object> fieldMeta = descriptor?.fieldMeta instanceof Map
            ? descriptor.fieldMeta[fieldName] as Map<String, Object>
            : [:]
        return fieldMeta?.label?.toString() ?: humanize(fieldName)
    }

    private static Map<String, Object> registryEntry(Object target, String targetClassName) {
        Class candidate = target?.class
        while (candidate != null) {
            Map<String, Object> exact = GeneratedRegistryAccessor.uiRegistryType().legacyDomains().find {
                it.domainClassName?.toString() == candidate.name
            } as Map<String, Object>
            if (exact != null) {
                return exact
            }
            candidate = candidate.superclass
        }
        if (targetClassName == null) {
            return null
        }
        return GeneratedRegistryAccessor.uiRegistryType().legacyDomains().find {
            it.domainClassName?.toString() == targetClassName
                || it.className?.toString() == targetClassName
        } as Map<String, Object>
    }

    private static Object readProperty(Object value, String propertyName) {
        if (value == null || propertyName == null) {
            return null
        }
        try {
            if (value.hasProperty(propertyName) == null) {
                return null
            }
            return value."${propertyName}"
        } catch (Exception ignored) {
            return null
        }
    }

    private static boolean hasConfiguredDisplayValue(Object value, Collection<String> displayFields) {
        return (displayFields ?: []).any { String fieldName ->
            Object fieldValue = readProperty(value, fieldName)
            fieldValue != null && !fieldValue.toString().isBlank()
        }
    }

    private static String humanize(String value) {
        if (value == null) {
            return ""
        }
        return value.replaceAll("([a-z])([A-Z])", '$1 $2')
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .findAll { !it.isBlank() }
            .collect { String part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1) }
            .join(' ')
    }
}

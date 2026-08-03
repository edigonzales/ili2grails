package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry

import java.util.Locale

final class InterlisNavigationSupport {

    private static final List<String> EXCLUDE_LOGICAL_NAMES = ["urlMappings", "assets", "interlisUi"]

    private InterlisNavigationSupport() {
    }

    static List<Map<String, Object>> menuEntries(def grailsApplication,
                                                 InterlisRuntimeRegistry runtimeRegistry) {
        return navigationModel(grailsApplication, runtimeRegistry).allEntries as List<Map<String, Object>>
    }

    static Map<String, Object> navigationModel(def grailsApplication,
                                               InterlisRuntimeRegistry runtimeRegistry) {
        Map<String, Object> controllers = controllerIndex(grailsApplication)
        List<Map<String, Object>> workspaces = workspaceEntries(grailsApplication, controllers)
        Set<String> representedControllers = new LinkedHashSet<>()
        workspaces.each { Map<String, Object> workspace ->
            representedControllers.add(workspace.controller.toString())
        }
        List<Map<String, Object>> domains = []

        runtimeRegistry.domains().each { DomainDescriptor registryEntry ->
            String controller = text(registryEntry.controllerName())
            if (controller == null) {
                return
            }
            // Mark hidden registry entries as represented as well, so that an
            // association domain is not reintroduced by the fallback scan.
            representedControllers.add(controller)
            if (!registryEntry.navigationVisible()) {
                return
            }
            def artefact = controllers[controller]
            if (artefact == null || !shouldIncludeArtefact(artefact)) {
                return
            }
            domains << domainEntry(registryEntry, artefact)
        }

        List<Map<String, Object>> fallback = []
        (grailsApplication?.controllerClasses ?: []).each { def artefact ->
            String logicalName = artefact?.logicalPropertyName?.toString()
            if (representedControllers.contains(logicalName) || !shouldIncludeArtefact(artefact)) {
                return
            }
            Class domainType = domainTypeForController(artefact)
            if (domainType != null && !showController(artefact, domainType, runtimeRegistry)) {
                return
            }
            fallback << [
                controller: logicalName,
                namespace : artefact.namespace?.toString(),
                label     : defaultLabel(artefact, domainType, runtimeRegistry),
                className : artefact.shortName?.toString()?.replaceFirst(/Controller$/, ''),
                modelName : null,
                topicName : null,
                topicLabel: null,
                iliName   : null,
                domainClassName: domainType?.name,
                associationDomain: false,
                navigationVisible: true,
                fallback  : true
            ]
        }

        domains = sortEntries(domains)
        fallback = sortEntries(fallback)
        List<Map<String, Object>> models = groupByModel(domains)
        List<Map<String, Object>> allEntries = new ArrayList<>(domains)
        allEntries.addAll(fallback)
        allEntries.addAll(workspaces)

        return [
        models      : models,
        domains     : domains,
        fallback    : fallback,
        workspaces  : workspaces,
        allEntries  : allEntries,
        singleModel : models.size() == 1
        ]
    }

    static List<Map<String, Object>> searchDomains(Map<String, Object> model, Object rawQuery) {
        String query = text(rawQuery)?.toLowerCase(Locale.ROOT)
        List<Map<String, Object>> domains = (model?.domains ?: []) as List<Map<String, Object>>
        if (query == null) {
            return domains
        }
        return domains.findAll { Map<String, Object> domain ->
            [domain.label, domain.className, domain.topicName, domain.modelName, domain.iliName]
                .find { value -> text(value)?.toLowerCase(Locale.ROOT)?.contains(query) } != null
        }
    }

    static Class domainTypeForController(def controllerArtefact) {
        if (controllerArtefact == null) {
            return null
        }
        try {
            def controllerClass = controllerArtefact.clazz
            if (controllerClass == null) {
                return null
            }
            def field = controllerClass.getDeclaredField("interlisDomainClassName")
            field.accessible = true
            String qualifiedName = field.get(null)?.toString()
            if (qualifiedName == null || qualifiedName.isBlank()) {
                return null
            }
            return controllerArtefact.grailsApplication?.getDomainClass(qualifiedName)?.clazz
        } catch (NoSuchFieldException ignored) {
            return null
        } catch (Exception ignored) {
            return null
        }
    }

    static boolean showController(def controllerArtefact, Class domainType,
                                  InterlisRuntimeRegistry runtimeRegistry) {
        if (domainType == null) {
            return showUnknownController(controllerArtefact)
        }
        return InterlisAssociationRegistrySupport.showInNavigation(runtimeRegistry, domainType)
    }

    static String defaultLabel(def controllerArtefact, Class domainType,
                               InterlisRuntimeRegistry runtimeRegistry = null) {
        if (controllerArtefact == null) {
            return "Unbekannt"
        }
        String shortName = controllerArtefact.shortName?.toString()
        if (shortName != null && shortName.endsWith("Controller")) {
            shortName = shortName.substring(0, shortName.length() - "Controller".length())
        }
        if (shortName == null || shortName.isBlank()) {
            shortName = "Unbekannt"
        }
        try {
            DomainDescriptor domain = domainType == null || runtimeRegistry == null
                ? null
                : runtimeRegistry.domainByClassName(domainType.name).orElse(null)
            if (domain?.iliName() != null) {
                String iliName = domain.iliName()
                int lastDot = iliName.lastIndexOf('.')
                return lastDot >= 0 ? iliName.substring(lastDot + 1) : iliName
            }
        } catch (Exception ignored) {
        }
        return shortName
    }

    private static Map<String, Object> domainEntry(DomainDescriptor registryEntry, def artefact) {
        String topicName = text(registryEntry.topicName())
        String modelName = text(registryEntry.modelName()) ?: "Unbekanntes Modell"
        String label = text(registryEntry.label()) ?: defaultLabel(artefact, null)
        return [
            controller       : text(registryEntry.controllerName()),
            namespace        : artefact.namespace?.toString(),
            label            : label,
            className        : text(registryEntry.className()) ?: label,
            modelName        : modelName,
            topicName        : topicName,
            topicLabel       : topicLabel(topicName),
            iliName          : text(registryEntry.iliName()),
            domainClassName  : text(registryEntry.domainClassName()),
            associationDomain: registryEntry.kind() == DomainKind.ASSOCIATION,
            navigationVisible: registryEntry.navigationVisible(),
            fallback         : false
        ]
    }

    private static Map<String, Object> controllerIndex(def grailsApplication) {
        Map<String, Object> result = [:]
        (grailsApplication?.controllerClasses ?: []).each { def artefact ->
            String logicalName = artefact?.logicalPropertyName?.toString()
            if (logicalName != null && !logicalName.isBlank()) {
                result[logicalName] = artefact
            }
        }
        return result
    }

    private static List<Map<String, Object>> workspaceEntries(def grailsApplication,
                                                               Map<String, Object> controllers) {
        Object rawWorkspaces = grailsApplication?.config?.ili2grails?.ui?.workspaces
        // ConfigSlurper/Grails represents a missing nested YAML path as an
        // empty ConfigObject (a Map), not necessarily as null. Treat both as
        // the optional, unconfigured state; non-empty maps remain invalid.
        if (rawWorkspaces == null || (rawWorkspaces instanceof Map && rawWorkspaces.isEmpty())) {
            return []
        }
        if (!(rawWorkspaces instanceof Collection)) {
            throw invalidWorkspaceConfiguration(null, "muss eine Liste von Einträgen sein")
        }

        Set<String> ids = new LinkedHashSet<>()
        Set<String> controllerNames = new LinkedHashSet<>()
        List<Map<String, Object>> entries = []
        rawWorkspaces.eachWithIndex { Object rawWorkspace, int index ->
            if (!(rawWorkspace instanceof Map)) {
                throw invalidWorkspaceConfiguration("Eintrag #${index + 1}", "muss eine Map sein")
            }
            Map workspace = rawWorkspace as Map
            String id = text(workspace.id)
            String label = text(workspace.label)
            String controller = text(workspace.controller)
            String action = text(workspace.action)
            String context = "id=${id ?: "<leer>"}, controller=${controller ?: "<leer>"}"
            if (id == null || label == null || controller == null || action == null) {
                throw invalidWorkspaceConfiguration(context, "benötigt id, label, controller und action")
            }
            if (!id.matches(/[A-Za-z0-9][A-Za-z0-9_-]*/)) {
                throw invalidWorkspaceConfiguration(context, "id enthält ungültige Zeichen")
            }
            if (!controller.matches(/[A-Za-z][A-Za-z0-9_-]*/)
                || !action.matches(/[A-Za-z][A-Za-z0-9_]*/)) {
                throw invalidWorkspaceConfiguration(context, "controller oder action enthält ungültige Zeichen")
            }
            if (!ids.add(id)) {
                throw invalidWorkspaceConfiguration(context, "id ist nicht eindeutig")
            }
            if (!controllerNames.add(controller)) {
                throw invalidWorkspaceConfiguration(context, "controller ist nicht eindeutig")
            }
            if (!controllers.containsKey(controller)) {
                throw invalidWorkspaceConfiguration(context, "Controller ist nicht registriert")
            }
            entries << [
                kind       : "workspace",
                id         : id,
                label      : label,
                controller : controller,
                action     : action,
                fallback   : false,
                navigationVisible: true
            ]
        }
        return entries.sort { left, right -> compareValues([left.label, left.id], [right.label, right.id]) }
    }

    private static IllegalArgumentException invalidWorkspaceConfiguration(String context,
                                                                            String reason) {
        String suffix = context == null ? "" : " (${context})"
        return new IllegalArgumentException(
            "Ungültige ili2grails.ui.workspaces-Konfiguration${suffix}: ${reason}"
        )
    }

    private static List<Map<String, Object>> sortEntries(List<Map<String, Object>> entries) {
        return entries.sort { left, right ->
            compareValues(
                [left.modelName, left.topicName, left.label, left.iliName],
                [right.modelName, right.topicName, right.label, right.iliName]
            )
        }
    }

    private static List<Map<String, Object>> groupByModel(List<Map<String, Object>> domains) {
        Map<String, Map<String, Object>> models = [:]
        domains.each { Map<String, Object> domain ->
            String modelName = text(domain.modelName) ?: "Unbekanntes Modell"
            Map<String, Object> model = models[modelName]
            if (model == null) {
                model = [name: modelName, topics: []]
                models[modelName] = model
            }
            String topicName = text(domain.topicName) ?: ""
            Map<String, Object> topic = (model.topics as List<Map<String, Object>>)
                .find { it.name == topicName }
            if (topic == null) {
                topic = [name: topicName, label: topicLabel(topicName), domains: []]
                (model.topics as List<Map<String, Object>>) << topic
            }
            (topic.domains as List<Map<String, Object>>) << domain
        }
        return models.values().sort { left, right ->
            compareText(left.name, right.name)
        }.collect { Map<String, Object> model ->
            model.topics = (model.topics as List<Map<String, Object>>).sort { left, right ->
                compareText(left.name, right.name)
            }
            model.topics.each { Map<String, Object> topic ->
                topic.domains = sortEntries(topic.domains as List<Map<String, Object>>)
            }
            model
        }
    }

    private static String topicLabel(String topicName) {
        if (topicName == null || topicName.isBlank()) {
            return "Ohne Topic"
        }
        int lastDot = topicName.lastIndexOf('.')
        return lastDot >= 0 ? topicName.substring(lastDot + 1) : topicName
    }

    private static int compareValues(List<Object> left, List<Object> right) {
        for (int index = 0; index < left.size(); index++) {
            int comparison = compareText(left[index], right[index])
            if (comparison != 0) {
                return comparison
            }
        }
        return 0
    }

    private static int compareText(Object left, Object right) {
        String leftText = text(left)?.toLowerCase(Locale.ROOT) ?: ""
        String rightText = text(right)?.toLowerCase(Locale.ROOT) ?: ""
        return leftText <=> rightText
    }

    private static String text(Object value) {
        String text = value?.toString()?.trim()
        return text == null || text.isBlank() ? null : text
    }

    private static boolean shouldIncludeArtefact(def artefact) {
        if (artefact == null) {
            return false
        }
        String logicalName = artefact.logicalPropertyName?.toString()
        if (logicalName == null || logicalName.isBlank()) {
            return false
        }
        if (EXCLUDE_LOGICAL_NAMES.contains(logicalName)) {
            return false
        }
        try {
            def artefactClass = artefact.clazz
            if (artefactClass != null && InterlisCrudControllerSupport.isAssignableFrom(artefactClass)) {
                return true
            }
        } catch (Exception ignored) {
        }
        return logicalName != null
    }

    private static boolean showUnknownController(def artefact) {
        return true
    }
}

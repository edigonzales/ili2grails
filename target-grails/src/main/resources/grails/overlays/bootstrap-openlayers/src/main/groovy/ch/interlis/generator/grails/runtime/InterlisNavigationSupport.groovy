package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.generated.InterlisAssociationRegistry

final class InterlisNavigationSupport {

    private static final List<String> EXCLUDE_LOGICAL_NAMES = ["urlMappings", "assets"]

    private InterlisNavigationSupport() {
    }

    static List<Map<String, Object>> menuEntries(def grailsApplication) {
        List<Map<String, Object>> entries = []
        def controllerClasses = grailsApplication?.controllerClasses
        if (controllerClasses == null) {
            return entries
        }
        controllerClasses.each { def artefact ->
            if (!shouldIncludeArtefact(artefact)) {
                return
            }
            Class domainType = domainTypeForController(artefact)
            boolean visible = domainType != null
                ? InterlisAssociationRegistrySupport.showInNavigation(domainType)
                : showUnknownController(artefact)
            if (!visible) {
                return
            }
            String label = defaultLabel(artefact, domainType)
            entries.add([
                controller: artefact.logicalPropertyName?.toString(),
                namespace : artefact.namespace?.toString(),
                label     : label
            ])
        }
        entries.sort { it.label?.toLowerCase() }
        return entries
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

    static boolean showController(def controllerArtefact, Class domainType) {
        if (domainType == null) {
            return showUnknownController(controllerArtefact)
        }
        return InterlisAssociationRegistrySupport.showInNavigation(domainType)
    }

    static String defaultLabel(def controllerArtefact, Class domainType) {
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
            def entity = InterlisAssociationRegistry.ENTITIES[domainType?.name]
            if (entity?.iliName != null) {
                String iliName = entity.iliName.toString()
                int lastDot = iliName.lastIndexOf('.')
                return lastDot >= 0 ? iliName.substring(lastDot + 1) : iliName
            }
        } catch (Exception ignored) {
        }
        return shortName
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

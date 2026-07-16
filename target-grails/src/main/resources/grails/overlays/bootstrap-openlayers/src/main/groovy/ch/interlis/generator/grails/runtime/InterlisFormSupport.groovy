package ch.interlis.generator.grails.runtime

/**
 * Small, managed form runtime shared by generated Bootstrap CRUD forms.
 *
 * The component deliberately keeps the form server rendered: it normalizes
 * submit intent, copies descriptor sections into a view model and carries the
 * contextual state required by a PRG redirect.
 */
final class InterlisFormSupport {

    static final String SAVE = "save"
    static final String SAVE_AND_CONTINUE = "saveAndContinue"
    private static final Set<String> ALLOWED_SUBMIT_MODES =
        Collections.unmodifiableSet([SAVE, SAVE_AND_CONTINUE] as LinkedHashSet<String>)

    private InterlisFormSupport() {
    }

    static String submitMode(Object rawMode) {
        String candidate = rawMode?.toString()
        return ALLOWED_SUBMIT_MODES.contains(candidate) ? candidate : SAVE
    }

    static boolean saveAndContinue(Object rawMode) {
        return submitMode(rawMode) == SAVE_AND_CONTINUE
    }

    static List<Map<String, Object>> formSections(Map<String, Object> descriptor) {
        Object rawSections = descriptor?.form?.sections
        if (!(rawSections instanceof Collection)) {
            return [[title: "Allgemein", fields: []]]
        }
        return rawSections.collect { Object rawSection ->
            Map<String, Object> section = rawSection instanceof Map
                ? rawSection as Map<String, Object>
                : [:]
            [
                title : section.title?.toString() ?: "Allgemein",
                fields: section.fields instanceof Collection
                    ? section.fields.collect { it.toString() }.unique()
                    : []
            ]
        }
    }

    static Map<String, Object> formViewModel(Map<String, Object> descriptor,
                                             Map<String, Object> values = [:]) {
        Map<String, Object> model = [
            formSections: formSections(descriptor),
            submitModes : [SAVE, SAVE_AND_CONTINUE]
        ]
        if (values != null) {
            model.putAll(values)
        }
        return model
    }

    static Map<String, Object> continueRedirect(Object instance, Map<String, Object> contextState) {
        Map<String, Object> redirect = [action: "edit", id: instance?.id]
        Map<String, Object> contextParams = contextParams(contextState)
        if (!contextParams.isEmpty()) {
            redirect.params = contextParams
        }
        return redirect
    }

    static Map<String, Object> contextParams(Map<String, Object> contextState) {
        if (contextState == null || contextState.isEmpty()) {
            return [:]
        }
        Map<String, Object> result = [:]
        if (contextState.contextId != null) {
            result.associationContext = contextState.contextId
        }
        if (contextState.ownerId != null) {
            result.associationOwnerId = contextState.ownerId
        }
        return result
    }
}

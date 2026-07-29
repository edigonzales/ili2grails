package ch.interlis.generator.grails.runtime

import java.util.Locale

/** Resolves managed UI messages using the language selected at generation time. */
final class InterlisMessageSupport {

    private InterlisMessageSupport() {
    }

    static String text(def grailsApplication,
                       String code,
                       String defaultMessage,
                       Object[] args = null) {
        if (grailsApplication == null || code == null) {
            return defaultMessage
        }
        try {
            def source = grailsApplication.mainContext.getBean(
                "org.springframework.context.MessageSource"
            )
            String resolved = source.getMessage(code, args, defaultMessage, configuredLocale(grailsApplication))
            return resolved ?: defaultMessage
        } catch (Exception ignored) {
            return defaultMessage
        }
    }

    static Locale configuredLocale(def grailsApplication) {
        String language = grailsApplication?.config?.ili2grails?.language?.toString()
        return Locale.forLanguageTag(language ?: "de-CH")
    }
}

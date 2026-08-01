package ch.interlis.generator.grails.runtime

import java.text.MessageFormat
import java.util.Locale

/** Resolves managed UI messages using the language selected at generation time. */
final class InterlisMessageSupport {

    private InterlisMessageSupport() {
    }

    static String text(def grailsApplication,
                       String code,
                       String defaultMessage,
                       Object[] args = null) {
        Locale locale = configuredLocale(grailsApplication)
        if (grailsApplication == null || code == null) {
            return formatDefault(defaultMessage, args, locale)
        }
        try {
            def source = grailsApplication.mainContext.getBean(
                "org.springframework.context.MessageSource"
            )
            String resolved = source.getMessage(code, args, defaultMessage, locale)
            return resolved ?: formatDefault(defaultMessage, args, locale)
        } catch (Exception ignored) {
            return formatDefault(defaultMessage, args, locale)
        }
    }

    static Locale configuredLocale(def grailsApplication) {
        String language = grailsApplication?.config?.ili2grails?.language?.toString()
        return Locale.forLanguageTag(language ?: "de-CH")
    }

    private static String formatDefault(String defaultMessage, Object[] args, Locale locale) {
        if (defaultMessage == null || args == null || args.length == 0) {
            return defaultMessage
        }
        return new MessageFormat(defaultMessage, locale ?: Locale.forLanguageTag("de-CH")).format(args)
    }
}

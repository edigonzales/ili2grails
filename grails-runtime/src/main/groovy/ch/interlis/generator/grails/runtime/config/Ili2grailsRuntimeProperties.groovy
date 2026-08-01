package ch.interlis.generator.grails.runtime.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Typed runtime configuration of the ili2grails runtime plugin.
 *
 * <p>Priority: typed runtime properties, then the legacy
 * {@code ili2grails.ui} configuration as a compatible override path, then
 * plugin defaults. Conflicts are diagnosed, never silently resolved.</p>
 */
@ConfigurationProperties(prefix = 'ili2grails.runtime')
class Ili2grailsRuntimeProperties {

    boolean strictDescriptorValidation = true
    boolean applySecurityHeaders = true
    int defaultPageSize = 20
    int maximumPageSize = 100
    String defaultLocale = 'de-CH'
    UiProperties ui = new UiProperties()

    static class UiProperties {
        String appTitle = 'INTERLIS CRUD'
        String appLogo
        String appLogoIcon = 'grid'
    }
}

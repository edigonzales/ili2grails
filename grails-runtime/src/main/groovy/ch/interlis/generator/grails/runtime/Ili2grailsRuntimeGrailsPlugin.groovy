package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.RuntimeVersionContract
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry
import ch.interlis.generator.grails.runtime.api.registry.RegistryValidationReport
import ch.interlis.generator.grails.runtime.config.InterlisRuntimeOverridesService
import ch.interlis.generator.grails.runtime.registry.InterlisRuntimeRegistryValidator
import ch.interlis.generator.grails.runtime.registry.InterlisRuntimeSafetyState
import grails.plugins.Plugin
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.GenericBeanDefinition

/**
 * ili2grails runtime plugin: services, controller, taglib, views, assets,
 * i18n and default policies for generated applications.
 */
@Slf4j
class Ili2grailsRuntimeGrailsPlugin extends Plugin {

    def grailsVersion = '7.0.0 > *'
    def profiles = ['web']
    def title = 'ili2grails Runtime'
    def description = 'Runtime services and generic UI support for ili2grails generated applications.'
    def documentation = 'https://github.com/edigonzales/ili2grails'

    Closure doWithSpring() {
        { ->
            // Bean names are the injection contract: services inject by
            // property name, so applications override a default by providing
            // their own bean with the same name (application beans win over
            // plugin beans in Spring).
            authorizationPolicy(
                ch.interlis.generator.grails.runtime.policy.AllowAllInterlisAuthorizationPolicy)
            lifecycleHooks(
                ch.interlis.generator.grails.runtime.lifecycle.NoopInterlisLifecycleHooks)
            interlisDisplayLabelResolver(
                ch.interlis.generator.grails.runtime.display.DefaultInterlisDisplayLabelResolver)
            recordLoader(
                ch.interlis.generator.grails.runtime.persistence.GormRuntimeRecordLoader)
            runtimeRegistry(
                ch.interlis.generator.grails.runtime.registry.InterlisRuntimeRegistryBeanFactory)
            overridesService(
                ch.interlis.generator.grails.runtime.config.InterlisRuntimeOverridesService) {
                grailsApplication = ref('grailsApplication')
            }
            interlisRuntimeRegistryValidator(
                ch.interlis.generator.grails.runtime.registry.InterlisRuntimeRegistryValidator) {
                grailsApplication = ref('grailsApplication')
            }
            runtimeSafetyState(
                ch.interlis.generator.grails.runtime.registry.InterlisRuntimeSafetyState)
        }
    }

    void doWithApplicationContext() {
        // Verify the runtime API contract on the classpath. Incompatible
        // versions must fail with a readable message, never silently.
        String apiVersion
        try {
            apiVersion = RuntimeVersionContract.RUNTIME_API_VERSION
        } catch (LinkageError missingApi) {
            throw new IllegalStateException(
                "The ili2grails runtime plugin requires the ili2grails-runtime-api " +
                "module on the classpath; found an incompatible or missing version.",
                missingApi)
        }
        log.info(
            "ili2grails runtime plugin active (runtime-api version {})",
            apiVersion)

        validateGeneratedRegistries()
    }

    private void validateGeneratedRegistries() {
        boolean strict = resolveStrictDescriptorValidation()
        InterlisRuntimeSafetyState safetyState =
            applicationContext.getBean(InterlisRuntimeSafetyState)
        try {
            InterlisRuntimeRegistry registry = applicationContext.getBean(InterlisRuntimeRegistry)
            InterlisRuntimeRegistryValidator validator =
                applicationContext.getBean(InterlisRuntimeRegistryValidator)
            RegistryValidationReport report = validator.validate(registry)
            // Technische Durchsetzung (Spezifikation §16): Strict-Modus wirft,
            // Non-strict-Modus startet technisch read-only. Es gibt keinen
            // Per-Feature-Downgrade.
            safetyState.initialize(report, strict)
            if (report.hasBlockingDiagnostics()) {
                String summary = report.blockingDiagnostics()
                    .collect { "${it.code().name()}: ${it.message()}" }
                    .join('\n  - ')
                if (!strict) {
                    log.warn(
                        "Runtime descriptor validation has blocking diagnostics.\n" +
                        "  - ${summary}\n" +
                        "Strict mode is disabled; all generated write operations are disabled.\n" +
                        "Read-only navigation remains available.")
                }
            } else if (!report.diagnostics().isEmpty()) {
                log.info(
                    "ili2grails registry validation complete ({} diagnostics)",
                    report.diagnostics().size())
            }
        } catch (IllegalStateException startupFailure) {
            throw startupFailure
        } catch (Exception validationFailure) {
            if (strict) {
                throw new IllegalStateException(
                    "ili2grails registry validation failed: ${validationFailure.message}",
                    validationFailure)
            }
            // Auch ein nicht ausführbarer Validator führt zu read-only
            // (fail-closed), damit nie ungeprüft geschrieben wird.
            safetyState.initialize(new RegistryValidationReport([
                new ch.interlis.generator.grails.runtime.api.registry.RegistryDiagnostic(
                    ch.interlis.generator.grails.runtime.api.registry.RegistryDiagnosticCode.VALIDATION_INCOMPLETE,
                    null, "registry validation could not run: ${validationFailure.message}",
                    [:], true)
            ]), strict)
            log.warn(
                "ili2grails registry validation could not run: ${validationFailure.message}; " +
                "all generated write operations are disabled (read-only mode).")
        }
    }

    private boolean resolveStrictDescriptorValidation() {
        Object configured = applicationContext?.environment?.getProperty(
            'ili2grails.runtime.strict-descriptor-validation')
        if (configured != null && configured.toString().isBlank() == false) {
            return Boolean.parseBoolean(configured.toString())
        }
        return true
    }
}

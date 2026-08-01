package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.RuntimeVersionContract
import ch.interlis.generator.grails.runtime.api.display.InterlisDisplayLabelResolver
import ch.interlis.generator.grails.runtime.api.lifecycle.InterlisLifecycleHooks
import ch.interlis.generator.grails.runtime.api.persistence.RuntimeRecordLoader
import ch.interlis.generator.grails.runtime.api.registry.DomainRegistry
import ch.interlis.generator.grails.runtime.api.registry.AssociationRegistry
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry
import ch.interlis.generator.grails.runtime.api.registry.RuntimeClassResolver
import ch.interlis.generator.grails.runtime.api.security.InterlisAuthorizationPolicy
import grails.plugins.Plugin
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.beans.factory.config.BeanDefinition

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
            allowAllInterlisAuthorizationPolicy(
                ch.interlis.generator.grails.runtime.policy.AllowAllInterlisAuthorizationPolicy)
            noopInterlisLifecycleHooks(
                ch.interlis.generator.grails.runtime.lifecycle.NoopInterlisLifecycleHooks)
            defaultInterlisDisplayLabelResolver(
                ch.interlis.generator.grails.runtime.display.DefaultInterlisDisplayLabelResolver)
            gormRuntimeRecordLoader(
                ch.interlis.generator.grails.runtime.persistence.GormRuntimeRecordLoader)
            interlisRuntimeRegistry(
                ch.interlis.generator.grails.runtime.registry.InterlisRuntimeRegistryBeanFactory)
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
    }
}

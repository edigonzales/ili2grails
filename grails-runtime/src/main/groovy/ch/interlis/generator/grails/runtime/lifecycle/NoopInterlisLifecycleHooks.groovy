package ch.interlis.generator.grails.runtime.lifecycle

import ch.interlis.generator.grails.runtime.api.lifecycle.InterlisLifecycleHooks

/**
 * Default lifecycle hooks: no-op. Hooks never replace security checks.
 */
final class NoopInterlisLifecycleHooks implements InterlisLifecycleHooks {
}

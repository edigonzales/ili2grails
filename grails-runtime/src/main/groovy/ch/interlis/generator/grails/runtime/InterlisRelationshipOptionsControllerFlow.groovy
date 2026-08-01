package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.controller.InterlisControllerContext
import ch.interlis.generator.grails.runtime.controller.InterlisSecurityHeaderSupport
import grails.converters.JSON

/**
 * Relationship options flow: the shared option picker endpoint for to-one
 * relationship fields.
 */
final class InterlisRelationshipOptionsControllerFlow<T> {

    def relationshipOptions(InterlisCrudControllerSupport<T> controller,
                            InterlisControllerContext<T> context) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        Map<String, Object> page = controller.relationshipOptionPage(
            controller.params.field?.toString(),
            controller.normalizedQuery(controller.params.q),
            controller.boundedMax(controller.params.int("max")),
            controller.safeOffset(controller.params.int("offset"))
        )
        controller.render page as JSON
    }
}

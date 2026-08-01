package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.command.CommandCode
import ch.interlis.generator.grails.runtime.api.command.CommandStatus
import ch.interlis.generator.grails.runtime.api.command.InverseRelationshipCommandResult
import ch.interlis.generator.grails.runtime.controller.InterlisControllerContext
import ch.interlis.generator.grails.runtime.controller.InterlisControllerResponseSupport
import ch.interlis.generator.grails.runtime.controller.InterlisSecurityHeaderSupport
import grails.converters.JSON
import groovy.util.logging.Slf4j

import static org.springframework.http.HttpStatus.*

/**
 * Inverse relationship flow of the generated CRUD controller: collection
 * pages, options and the assign command.
 */
@Slf4j
final class InterlisInverseRelationshipControllerFlow<T> {

    def relationshipCollectionPage(InterlisCrudControllerSupport<T> controller,
                                   InterlisControllerContext<T> context,
                                   Long id) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        T instance = context.crudService.get(id) as T
        if (instance == null) {
            InterlisControllerResponseSupport.notFound(
                controller, context.grailsApplication, controller.modelKey())
            return
        }
        String relationshipName = controller.params.relationship?.toString()
        try {
            Map<String, Object> page = context.inverseRelationshipQueryService.page(
                context.domainType,
                instance.id as java.io.Serializable,
                relationshipName,
                controller.normalizedQuery(controller.params.q),
                controller.boundedMax(controller.params.int("max")),
                controller.safeOffset(controller.params.int("offset")),
                controller.params.sort?.toString(),
                controller.params.order?.toString()
            )
            controller.render page as JSON
        } catch (InterlisInverseRelationshipSupport.InverseRelationshipNotFoundException e) {
            controller.response.status = BAD_REQUEST.value()
            controller.render([error: e.message] as JSON)
        } catch (IllegalArgumentException e) {
            controller.response.status = INTERNAL_SERVER_ERROR.value()
            controller.render([
                code: "CONFIGURATION_INVALID",
                error: "Ungültige Beziehungs-Konfiguration: ${e.message}"
            ] as JSON)
        } catch (Exception e) {
            log.error(
                "relationshipCollectionPage failed for ${context.domainType.simpleName}#${id} " +
                    "relationship ${relationshipName}: ${e.message}",
                e
            )
            controller.response.status = INTERNAL_SERVER_ERROR.value()
            controller.render([error: "Beziehungsdaten konnten nicht geladen werden."] as JSON)
        }
    }

    def relationshipCollectionOptions(InterlisCrudControllerSupport<T> controller,
                                      InterlisControllerContext<T> context,
                                      Long id) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        T instance = context.crudService.get(id) as T
        if (instance == null) {
            InterlisControllerResponseSupport.notFound(
                controller, context.grailsApplication, controller.modelKey())
            return
        }
        String relationshipName = controller.params.relationship?.toString()
        try {
            Map<String, Object> page = context.inverseRelationshipQueryService.optionPage(
                context.domainType,
                instance.id as java.io.Serializable,
                relationshipName,
                controller.normalizedQuery(controller.params.q),
                controller.boundedMax(controller.params.int("max")),
                controller.safeOffset(controller.params.int("offset"))
            )
            controller.render page as JSON
        } catch (InterlisInverseRelationshipSupport.InverseRelationshipNotFoundException e) {
            controller.response.status = BAD_REQUEST.value()
            controller.render([results: [], pagination: [more: false, total: 0, nextOffset: 0], error: e.message] as JSON)
        } catch (IllegalArgumentException e) {
            controller.response.status = INTERNAL_SERVER_ERROR.value()
            controller.render([
                results: [],
                pagination: [more: false, total: 0, nextOffset: 0],
                code: "CONFIGURATION_INVALID",
                error: "Ungültige Beziehungs-Konfiguration: ${e.message}"
            ] as JSON)
        } catch (Exception e) {
            log.warn(
                "relationshipCollectionOptions failed for ${context.domainType.simpleName}#${id} " +
                    "relationship ${relationshipName}: ${e.message}",
                e
            )
            controller.render([results: [], pagination: [more: false, total: 0, nextOffset: 0]] as JSON)
        }
    }

    def relationshipAssign(InterlisCrudControllerSupport<T> controller,
                           InterlisControllerContext<T> context,
                           Long id) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        T instance = context.crudService.get(id) as T
        if (instance == null) {
            InterlisControllerResponseSupport.notFound(
                controller, context.grailsApplication, controller.modelKey())
            return
        }
        InverseRelationshipCommandResult result
        try {
            result = context.inverseRelationshipCommandService.assign(
                context.domainType,
                instance.id as java.io.Serializable,
                controller.params.relationship?.toString(),
                controller.params.long("targetId") as java.io.Serializable,
                controller.params.boolean("confirmReassignment")
            )
        } catch (Exception e) {
            log.error(
                "relationshipAssign failed for ${context.domainType.simpleName}#${id}: ${e.message}",
                e
            )
            result = InverseRelationshipCommandResult.failure(
                500, CommandStatus.SERVER_ERROR, CommandCode.INTERNAL_ERROR,
                "Die Zuordnung konnte nicht verarbeitet werden."
            )
        }
        InterlisControllerResponseSupport.respondInverseRelationshipCommand(controller, instance, result)
    }
}

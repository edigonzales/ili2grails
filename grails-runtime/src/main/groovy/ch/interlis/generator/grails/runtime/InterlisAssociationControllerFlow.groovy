package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.command.AssociationCommandResult
import ch.interlis.generator.grails.runtime.api.command.CommandCode
import ch.interlis.generator.grails.runtime.api.command.CommandStatus
import ch.interlis.generator.grails.runtime.controller.InterlisControllerContext
import ch.interlis.generator.grails.runtime.controller.InterlisControllerResponseSupport
import ch.interlis.generator.grails.runtime.controller.InterlisSecurityHeaderSupport
import grails.converters.JSON
import groovy.util.logging.Slf4j

import static org.springframework.http.HttpStatus.*

/**
 * Association flow of the generated CRUD controller: association page,
 * options and the quick-link create/delete commands.
 */
@Slf4j
final class InterlisAssociationControllerFlow<T> {

    def associationPage(InterlisCrudControllerSupport<T> controller,
                        InterlisControllerContext<T> context,
                        Long id) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        T instance = context.crudService.get(id) as T
        if (instance == null) {
            InterlisControllerResponseSupport.notFound(
                controller, context.grailsApplication, controller.modelKey())
            return
        }
        String contextId = controller.params.context?.toString()
        if (contextId == null || contextId.isBlank()) {
            controller.response.status = BAD_REQUEST.value()
            controller.render([error: "context parameter required"] as JSON)
            return
        }
        try {
            Map<String, Object> page = context.associationQueryService.page(
                context.domainType,
                instance.id as java.io.Serializable,
                contextId,
                controller.boundedMax(controller.params.int("max")),
                controller.safeOffset(controller.params.int("offset")),
                controller.params.sort?.toString(),
                controller.params.order?.toString()
            )
            controller.render page as JSON
        } catch (InterlisAssociationRegistrySupport.AssociationContextNotFoundException e) {
            controller.response.status = BAD_REQUEST.value()
            controller.render([error: e.message] as JSON)
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            controller.response.status = BAD_REQUEST.value()
            controller.render([error: e.message] as JSON)
        } catch (Exception e) {
            log.error("associationPage failed for ${context.domainType.simpleName}#${id} context ${contextId}: ${e.message}", e)
            controller.response.status = INTERNAL_SERVER_ERROR.value()
            controller.render([error: "Fehler beim Laden der Assoziationsdaten."] as JSON)
        }
    }

    def associationOptions(InterlisCrudControllerSupport<T> controller,
                           InterlisControllerContext<T> context,
                           Long id) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        T instance = context.crudService.get(id) as T
        if (instance == null) {
            InterlisControllerResponseSupport.notFound(
                controller, context.grailsApplication, controller.modelKey())
            return
        }
        String contextId = controller.params.context?.toString()
        String roleName = controller.params.role?.toString()
        if (contextId == null || contextId.isBlank() || roleName == null || roleName.isBlank()) {
            controller.response.status = BAD_REQUEST.value()
            controller.render([results: [], pagination: [more: false, total: 0, nextOffset: 0]] as JSON)
            return
        }
        try {
            Map<String, Object> page = context.associationQueryService.optionPage(
                context.domainType,
                contextId,
                roleName,
                controller.normalizedQuery(controller.params.q),
                controller.boundedMax(controller.params.int("max")),
                controller.safeOffset(controller.params.int("offset"))
            )
            controller.render page as JSON
        } catch (InterlisAssociationRegistrySupport.AssociationContextNotFoundException e) {
            controller.response.status = BAD_REQUEST.value()
            controller.render([error: e.message] as JSON)
        } catch (InterlisAssociationRegistrySupport.AssociationOwnershipException e) {
            controller.response.status = BAD_REQUEST.value()
            controller.render([error: e.message] as JSON)
        } catch (Exception e) {
            log.warn("associationOptions failed for ${context.domainType.simpleName}#${id} context ${contextId}: ${e.message}", e)
            controller.render([results: [], pagination: [more: false, total: 0, nextOffset: 0]] as JSON)
        }
    }

    def associationCreate(InterlisCrudControllerSupport<T> controller,
                          InterlisControllerContext<T> context,
                          Long id) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        if (!controller.runtimeWriteAllowed()) {
            respondReadOnly(controller)
            return
        }
        T instance = context.crudService.get(id) as T
        if (instance == null) {
            InterlisControllerResponseSupport.notFound(
                controller, context.grailsApplication, controller.modelKey())
            return
        }
        String contextId = controller.params.context?.toString()
        String targetRoleName = controller.params.role?.toString()
        Long targetId = controller.params.long("targetId")
        if (contextId == null || contextId.isBlank()) {
            controller.response.status = BAD_REQUEST.value()
            controller.render([success: false, status: 400, code: "MISSING_CONTEXT",
                message: InterlisMessageSupport.text(context.grailsApplication,
                    "ili2grails.association.error.MISSING_CONTEXT", "Der Assoziationskontext fehlt.")] as JSON)
            return
        }
        AssociationCommandResult result
        try {
            result = context.associationCommandService.createQuickLink(
                context.domainType,
                instance.id as java.io.Serializable,
                contextId,
                targetRoleName,
                targetId as java.io.Serializable
            )
        } catch (Exception e) {
            log.error("associationCreate failed for ${context.domainType.simpleName}#${id} context ${contextId}: ${e.message}", e)
            result = AssociationCommandResult.failure(
                500, CommandStatus.SERVER_ERROR, CommandCode.INTERNAL_ERROR,
                InterlisMessageSupport.text(context.grailsApplication,
                    "ili2grails.association.error.INTERNAL_ERROR", "Die Zuordnung konnte nicht erstellt werden."))
        }
        InterlisControllerResponseSupport.respondAssociationCommand(controller, instance, result)
    }

    def associationDelete(InterlisCrudControllerSupport<T> controller,
                          InterlisControllerContext<T> context,
                          Long id) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        if (!controller.runtimeWriteAllowed()) {
            respondReadOnly(controller)
            return
        }
        T instance = context.crudService.get(id) as T
        if (instance == null) {
            InterlisControllerResponseSupport.notFound(
                controller, context.grailsApplication, controller.modelKey())
            return
        }
        String contextId = controller.params.context?.toString()
        Long associationId = controller.params.long("associationId")
        if (contextId == null || contextId.isBlank() || associationId == null) {
            controller.response.status = BAD_REQUEST.value()
            controller.render([success: false, status: 400, code: "MISSING_PARAMS",
                message: InterlisMessageSupport.text(context.grailsApplication,
                    "ili2grails.association.error.MISSING_PARAMS", "Kontext und Assoziations-ID werden benötigt.")] as JSON)
            return
        }
        AssociationCommandResult result
        try {
            result = context.associationCommandService.deleteLink(
                context.domainType,
                instance.id as java.io.Serializable,
                contextId,
                associationId as java.io.Serializable
            )
        } catch (Exception e) {
            log.error("associationDelete failed for ${context.domainType.simpleName}#${id} context ${contextId}: ${e.message}", e)
            result = AssociationCommandResult.failure(
                500, CommandStatus.SERVER_ERROR, CommandCode.INTERNAL_ERROR,
                InterlisMessageSupport.text(context.grailsApplication,
                    "ili2grails.association.error.INTERNAL_ERROR", "Die Zuordnung kann nicht entfernt werden."))
        }
        InterlisControllerResponseSupport.respondAssociationCommand(controller, instance, result)
    }
}

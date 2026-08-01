package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.security.DomainOperation
import ch.interlis.generator.grails.runtime.api.security.DomainOperationContext
import ch.interlis.generator.grails.runtime.controller.InterlisControllerContext
import ch.interlis.generator.grails.runtime.controller.InterlisControllerResponseSupport
import ch.interlis.generator.grails.runtime.controller.InterlisSecurityHeaderSupport
import grails.converters.JSON
import grails.validation.ValidationException
import groovy.util.logging.Slf4j

import static org.springframework.http.HttpStatus.*

/**
 * Form flow of the generated CRUD controller: create, save, edit, update and
 * delete with validation forms and association context handling.
 */
@Slf4j
final class InterlisFormControllerFlow<T> {

    def create(InterlisCrudControllerSupport<T> controller,
               InterlisControllerContext<T> context) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        if (!controller.runtimeWriteAllowed()) {
            respondReadOnly(controller)
            return
        }
        if (!context.authorizationPolicy.canCreate(domainOperation(context, DomainOperation.CREATE))) {
            InterlisControllerResponseSupport.respondForbidden(controller,
                "Keine Berechtigung für diese Aktion.")
            return
        }
        T instance = context.domainType.newInstance(controller.domainBindParams()) as T
        Map<String, Object> contextState = controller.associationContextState(instance)
        if (contextState == null) {
            InterlisControllerResponseSupport.respondAssociationError(
                controller, BAD_REQUEST.value(), "invalid_association_context",
                "Der Kontext des Datensatzes ist ungültig.")
            return
        }
        if (!contextState.isEmpty()) {
            controller.applyAssociationContext(instance, contextState)
        }
        InterlisGeometryBinder.bindGeometryFromParams(
            instance, controller.params, controller.geometryMeta(), context.grailsApplication, controller)
        Map<String, Object> model = controller.formModelWithContext(instance, contextState)
        model.put(controller.modelKey(), instance)
        controller.render view: "create", model: model
    }

    def save(InterlisCrudControllerSupport<T> controller,
             InterlisControllerContext<T> context) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        if (!controller.runtimeWriteAllowed()) {
            respondReadOnly(controller)
            return
        }
        if (!context.authorizationPolicy.canCreate(domainOperation(context, DomainOperation.CREATE))) {
            InterlisControllerResponseSupport.respondForbidden(controller,
                "Keine Berechtigung für diese Aktion.")
            return
        }
        String submitMode = InterlisFormSupport.submitMode(controller.params.submitMode)
        T instance = context.domainType.newInstance(controller.domainBindParams()) as T
        Map<String, Object> contextState = controller.loadContextStateFromParams()
        if (contextState == null) {
            InterlisControllerResponseSupport.respondAssociationError(
                controller, BAD_REQUEST.value(), "invalid_association_context",
                "Der Kontext des Datensatzes ist ungültig.")
            return
        }
        if (!contextState.isEmpty()) {
            controller.applyAssociationContext(instance, contextState)
        }
        InterlisGeometryBinder.bindGeometryFromParams(
            instance, controller.params, controller.geometryMeta(), context.grailsApplication, controller)
        if (instance.hasErrors()) {
            controller.renderValidationForm("create", instance, contextState)
            return
        }

        try {
            context.crudService.save(instance)
        } catch (ValidationException ignored) {
            controller.renderValidationForm("create", instance, contextState)
            return
        }

        controller.request.withFormat {
            form multipartForm {
                InterlisControllerResponseSupport.flashNotification(controller, "success",
                    controller.message(
                        code: "default.created.message",
                        args: [controller.message(
                            code: controller.modelKey() + ".label",
                            default: context.domainType.simpleName), instance.id]
                    ))
                Map<String, Object> redirectTarget = controller.successfulSaveRedirect(
                    instance, contextState, submitMode
                )
                if (redirectTarget != null) {
                    controller.redirect redirectTarget
                } else {
                    controller.redirect instance
                }
            }
            "*" { controller.respond instance, [status: CREATED] }
        }
    }

    def edit(InterlisCrudControllerSupport<T> controller,
             InterlisControllerContext<T> context,
             Long id) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        if (!controller.runtimeWriteAllowed()) {
            respondReadOnly(controller)
            return
        }
        if (!context.authorizationPolicy.canView(domainOperation(context, DomainOperation.VIEW))) {
            InterlisControllerResponseSupport.respondForbidden(controller,
                "Keine Berechtigung für diese Aktion.")
            return
        }
        T instance = context.crudService.get(id) as T
        if (instance == null) {
            InterlisControllerResponseSupport.notFound(
                controller, context.grailsApplication, controller.modelKey())
            return
        }
        Map<String, Object> contextState = controller.associationContextState(instance, true)
        if (contextState == null) {
            InterlisControllerResponseSupport.respondAssociationError(
                controller, BAD_REQUEST.value(), "invalid_association_context",
                "Der Kontext ist ungültig oder gehört nicht zum Datensatz.")
            return
        }
        controller.respond instance, model: controller.formModelWithContext(instance, contextState)
    }

    def update(InterlisCrudControllerSupport<T> controller,
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
        if (!context.authorizationPolicy.canUpdate(domainOperation(context, DomainOperation.UPDATE), instance)) {
            InterlisControllerResponseSupport.respondForbidden(controller,
                "Keine Berechtigung für diese Aktion.")
            return
        }

        String submitMode = InterlisFormSupport.submitMode(controller.params.submitMode)
        Map<String, Object> contextState = controller.loadContextStateFromParams(instance, true)
        if (contextState == null) {
            InterlisControllerResponseSupport.respondAssociationError(
                controller, BAD_REQUEST.value(), "invalid_association_context",
                "Der Kontext ist ungültig oder gehört nicht zum Datensatz.")
            return
        }
        controller.bindData(instance, controller.domainBindParams())
        InterlisGeometryBinder.bindGeometryFromParams(
            instance, controller.params, controller.geometryMeta(), context.grailsApplication, controller)
        if (!contextState.isEmpty()) {
            controller.applyAssociationContext(instance, contextState)
        }
        if (instance.hasErrors()) {
            controller.renderValidationForm("edit", instance, contextState)
            return
        }

        try {
            context.crudService.save(instance)
        } catch (ValidationException ignored) {
            controller.renderValidationForm("edit", instance, contextState)
            return
        }

        controller.request.withFormat {
            form multipartForm {
                InterlisControllerResponseSupport.flashNotification(controller, "success",
                    controller.message(
                        code: "default.updated.message",
                        args: [controller.message(
                            code: controller.modelKey() + ".label",
                            default: context.domainType.simpleName), instance.id]
                    ))
                Map<String, Object> redirectTarget = controller.successfulSaveRedirect(
                    instance, contextState, submitMode
                )
                if (redirectTarget != null) {
                    controller.redirect redirectTarget
                } else {
                    controller.redirect instance
                }
            }
            "*" { controller.respond instance, [status: OK] }
        }
    }

    def delete(InterlisCrudControllerSupport<T> controller,
               InterlisControllerContext<T> context,
               Long id) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        if (!controller.runtimeWriteAllowed()) {
            respondReadOnly(controller)
            return
        }
        if (id == null) {
            InterlisControllerResponseSupport.notFound(
                controller, context.grailsApplication, controller.modelKey())
            return
        }

        T instance = context.crudService.get(id) as T
        if (instance != null
            && !context.authorizationPolicy.canDelete(domainOperation(context, DomainOperation.DELETE), instance)) {
            InterlisControllerResponseSupport.respondForbidden(controller,
                "Keine Berechtigung für diese Aktion.")
            return
        }

        try {
            context.crudService.delete(id)
        } catch (Exception failure) {
            if (!InterlisCrudControllerSupport.isDeleteIntegrityConflict(failure)) {
                throw failure
            }
            String conflictMessage = InterlisMessageSupport.text(
                context.grailsApplication,
                "ili2grails.runtime.deleteIntegrity",
                "Datensatz ${id} konnte nicht gelöscht werden, weil er noch von anderen Datensätzen verwendet wird.",
                [id] as Object[]
            )
            controller.request.withFormat {
                form multipartForm {
                    InterlisControllerResponseSupport.flashNotification(controller, "danger", conflictMessage)
                    controller.redirect action: "index", method: "GET"
                }
                "*" {
                    controller.response.status = CONFLICT.value()
                    controller.render([error: conflictMessage] as JSON)
                }
            }
            return
        }

        controller.request.withFormat {
            form multipartForm {
                InterlisControllerResponseSupport.flashNotification(controller, "success",
                    controller.message(
                        code: "default.deleted.message",
                        args: [controller.message(
                            code: controller.modelKey() + ".label",
                            default: context.domainType.simpleName), id]
                    ))
                controller.redirect action: "index", method: "GET"
            }
            "*" { controller.render status: NO_CONTENT }
        }
    }

    private static DomainOperationContext domainOperation(InterlisControllerContext<?> context,
                                                          DomainOperation operation) {
        String iliName = null
        try {
            iliName = context.runtimeRegistry?.requireDomain(context.domainType)?.iliName()
        } catch (Exception ignored) {
        }
        return new DomainOperationContext(operation, context.domainType.name, iliName)
    }
}

package ch.interlis.generator.grails.runtime.controller

import ch.interlis.generator.grails.runtime.InterlisMessageSupport
import ch.interlis.generator.grails.runtime.api.command.AssociationCommandResult
import ch.interlis.generator.grails.runtime.api.command.InverseRelationshipCommandResult
import ch.interlis.generator.grails.runtime.presenter.RuntimeResponseMapper
import grails.converters.JSON
import groovy.util.logging.Slf4j

import static org.springframework.http.HttpStatus.*

/**
 * Centralized controller response helpers: flash notifications, JSON
 * rendering, command result conversion and not-found handling.
 */
@Slf4j
final class InterlisControllerResponseSupport {

    private static final List<String> SUPPORTED_FLASH_TYPES = ["success", "info", "warning", "danger"]

    private InterlisControllerResponseSupport() {
    }

    static void respondAssociationCommand(Object controller, Object instance,
                                          AssociationCommandResult result) {
        Map<String, Object> responseMap = RuntimeResponseMapper.toMap(result)
        boolean success = result?.success() == true
        int status = (result?.httpStatus() ?: (success ? 200 : 400)) as int
        String userMessage = result?.message()?.toString()
        controller.request.withFormat {
            form multipartForm {
                if (userMessage != null && !userMessage.isBlank()) {
                    flashNotification(controller, success ? "success" : "danger", userMessage)
                }
                controller.redirect action: "show", id: instance.id, method: "GET"
            }
            "*" {
                controller.response.status = status
                controller.render responseMap as JSON
            }
        }
    }

    static void respondInverseRelationshipCommand(Object controller, Object instance,
                                                  InverseRelationshipCommandResult result) {
        Map<String, Object> responseMap = RuntimeResponseMapper.toMap(result)
        boolean success = result?.success() == true
        int status = (result?.httpStatus() ?: (success ? 200 : 400)) as int
        String userMessage = result?.message()?.toString()
        if (success && userMessage != null && !userMessage.isBlank()) {
            flashNotification(controller, "success", userMessage)
        }
        if (inverseRelationshipJsonRequested(controller)) {
            controller.response.status = status
            controller.render responseMap as JSON
            return
        }
        controller.request.withFormat {
            form multipartForm {
                if (!success && userMessage != null && !userMessage.isBlank()) {
                    flashNotification(controller, "danger", userMessage)
                }
                controller.redirect action: "show", id: instance.id, method: "GET"
            }
            "*" {
                controller.response.status = status
                controller.render responseMap as JSON
            }
        }
    }

    static void respondAssociationError(Object controller, int status, String code, String message) {
        controller.request.withFormat {
            form multipartForm {
                flashNotification(controller, "danger", message)
                controller.redirect action: "index", method: "GET"
            }
            "*" {
                controller.response.status = status
                controller.render([success: false, status: status, code: code, message: message] as JSON)
            }
        }
    }

    static void respondForbidden(Object controller, String message) {
        respondAssociationError(controller, FORBIDDEN.value(), "FORBIDDEN", message)
    }

    static void notFound(Object controller, Object grailsApplication, String modelKey) {
        InterlisSecurityHeaderSupport.apply(controller, controller.response)
        controller.request.withFormat {
            form multipartForm {
                flashNotification(controller, "danger", controller.message(
                    code: "default.not.found.message",
                    args: [controller.message(code: modelKey + ".label",
                        default: controller.domainType()?.simpleName), controller.params.id]
                ))
                controller.redirect action: "index", method: "GET"
            }
            "*" { controller.render status: NOT_FOUND }
        }
    }

    static void flashNotification(Object controller, String type,
                                  String text, String title = null,
                                  Map<String, Object> extras = [:]) {
        if (text == null || text.isBlank()) {
            return
        }
        String normalizedType = SUPPORTED_FLASH_TYPES.contains(type) ? type : "info"
        Map<String, Object> notification = [type: normalizedType, message: text]
        if (title != null && !title.isBlank()) {
            notification.title = title
        }
        ["detail", "actionLabel", "actionUrl", "icon"].each { String key ->
            Object value = extras?.get(key)
            if (value != null && value.toString().trim()) {
                notification[key] = value.toString()
            }
        }
        controller.flash.notification = notification
    }

    static boolean inverseRelationshipJsonRequested(Object controller) {
        if (controller.params.format?.toString()?.equalsIgnoreCase("json")) {
            return true
        }
        String accept = controller.request.getHeader("Accept")
        return accept != null && accept.toLowerCase(java.util.Locale.ROOT).contains("application/json")
    }
}

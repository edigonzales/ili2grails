package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationAttributeDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationCreateMode
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry
import groovy.util.logging.Slf4j



@Slf4j
class InterlisAssociationQueryService {

    static transactional = false

    def grailsApplication
    InterlisRuntimeRegistry runtimeRegistry

    List<Map<String, Object>> sections(Class participantType, Serializable participantId, Integer maxPerSection) {
        if (participantType == null || participantId == null) {
            return []
        }
        Object participant = participantType.get(participantId)
        if (participant == null) {
            return []
        }
        Integer limit = boundedMax(maxPerSection ?: 10)
        List<AssociationContextDescriptor> contexts =
            InterlisAssociationRegistrySupport.contextsForParticipant(runtimeRegistry, participantType)
        if (contexts.isEmpty()) {
            return []
        }
        List<Map<String, Object>> result = []
        contexts.each { AssociationContextDescriptor ctx ->
            try {
                Map<String, Object> section = buildSection(participantType, participant, ctx, limit)
                if (section != null) {
                    result.add(section)
                }
            } catch (Exception e) {
                log.warn("Failed to build association section for context ${ctx.id()} on ${participantType.simpleName}#${participantId}: ${e.message}", e)
            }
        }
        return result
    }

    Map<String, Object> page(Class participantType, Serializable participantId, String contextId,
                             Integer max, Integer offset, String sort, String requestedOrder) {
        AssociationContextDescriptor context =
            InterlisAssociationRegistrySupport.requireContext(runtimeRegistry, participantType, contextId)
        Object participant = participantType.get(participantId)
        if (participant == null) {
            return [total: 0, rows: [], max: max, offset: offset, contextId: contextId]
        }
        Integer pageMax = boundedMax(max ?: 10)
        Integer pageOffset = safeOffset(offset ?: 0)
        Class associationType = InterlisAssociationRegistrySupport.resolveAssociationClass(runtimeRegistry, context)
        if (associationType == null) {
            return [total: 0, rows: [], max: pageMax, offset: pageOffset, contextId: contextId]
        }
        String fixedProperty = context.fixedPropertyName()
        String sortField = safeSort(sort, associationType)
        String sortOrder = safeOrder(requestedOrder)
        AssociationDescriptor associationDescriptor = runtimeRegistry.requireAssociation(context.associationName())
        List<AssociationRoleDescriptor> editableRoleList =
            InterlisAssociationRegistrySupport.editableRoles(associationDescriptor, context)
        def results = associationType.createCriteria().list(max: pageMax, offset: pageOffset) {
            eq(fixedProperty + ".id", participantId)
            editableRoleList.each { AssociationRoleDescriptor roleDesc ->
                fetchMode(roleDesc.propertyName(), org.hibernate.FetchMode.JOIN)
            }
            if (sortField == "id") {
                order("id", sortOrder)
            } else {
                order(sortField, sortOrder)
                order("id", "asc")
            }
        }
        long total = associationType.createCriteria().get {
            eq(fixedProperty + ".id", participantId)
            projections {
                count("id")
            }
        } as Long ?: 0L
        List<Map<String, Object>> rows = results.collect { Object instance ->
            describeAssociationRow(associationDescriptor, context, instance)
        }
        return [
            total: total,
            rows: rows,
            max: pageMax,
            offset: pageOffset,
            contextId: contextId,
            more: (pageOffset + results.size()) < total
        ]
    }

    Map<String, Object> optionPage(Class participantType, String contextId, String roleName,
                                   String query, Integer max, Integer offset) {
        AssociationContextDescriptor context =
            InterlisAssociationRegistrySupport.requireContext(runtimeRegistry, participantType, contextId)
        AssociationDescriptor associationDesc = runtimeRegistry.requireAssociation(context.associationName())
        AssociationRoleDescriptor roleDesc = InterlisAssociationRegistrySupport.role(associationDesc, roleName)
        if (roleDesc == null) {
            return [results: [], pagination: [more: false, total: 0, nextOffset: offset]]
        }
        String targetDomainClass = roleDesc.targetDomainClassName()
        Class targetType = InterlisAssociationRegistrySupport.resolveDomainClass(runtimeRegistry, targetDomainClass)
        if (targetType == null) {
            return [results: [], pagination: [more: false, total: 0, nextOffset: offset]]
        }
        return InterlisRelationshipOptions.optionPageForTargetType(
            grailsApplication, runtimeRegistry, targetType, query,
            boundedMax(max ?: 25), safeOffset(offset ?: 0))
    }

    Map<String, Object> describeAssociationRow(AssociationDescriptor association,
                                               AssociationContextDescriptor context,
                                               Object associationInstance) {
        if (associationInstance == null) {
            return null
        }
        List<Map<String, Object>> counterparts = []
        List<AssociationRoleDescriptor> editableRoleList =
            InterlisAssociationRegistrySupport.editableRoles(association, context)
        editableRoleList.each { AssociationRoleDescriptor roleDesc ->
            String property = roleDesc.propertyName()
            Object target = associationInstance."${property}"
            if (target != null) {
                String targetController = resolveTargetController(roleDesc)
                counterparts.add([
                    role: roleDesc.name(),
                    property: property,
                    id: target.id?.toString(),
                    label: InterlisRelationshipOptions.optionLabel(
                        grailsApplication, runtimeRegistry, target),
                    controller: targetController
                ])
            }
        }
        List<Map<String, Object>> attrList = []
        List<AssociationAttributeDescriptor> attrs = association.attributes()
        if (attrs != null) {
            attrs.each { AssociationAttributeDescriptor attrDesc ->
                String property = attrDesc.propertyName()
                Object value = null
                try {
                    value = associationInstance."${property}"
                } catch (Exception ignored) {
                }
                attrList.add([
                    property: property,
                    label: attrDesc.label() ?: property,
                    value: value
                ])
            }
        }
        String associationDomainClass = association.domainClassName()
        boolean deleteAllowed = context.writable() && context.removable() &&
            context.createMode() == AssociationCreateMode.QUICK
        boolean editAllowed = context.writable() &&
            context.createMode() == AssociationCreateMode.CONTEXTUAL_FORM
        return [
            associationId: associationInstance.id?.toString(),
            associationLabel: buildAssociationLabel(associationInstance, editableRoleList, attrList),
            counterparts: counterparts,
            attributes: attrList,
            deleteAllowed: deleteAllowed,
            editAllowed: editAllowed,
            associationController: resolveAssociationController(association),
            associationDomainClass: associationDomainClass
        ]
    }

    private Map<String, Object> buildSection(Class participantType, Object participant,
                                             AssociationContextDescriptor context, Integer limit) {
        AssociationDescriptor associationDesc = runtimeRegistry.association(context.associationName()).orElse(null)
        if (associationDesc == null) {
            return null
        }
        Class associationType = InterlisAssociationRegistrySupport.resolveAssociationClass(runtimeRegistry, context)
        if (associationType == null) {
            return null
        }
        String fixedProperty = context.fixedPropertyName()
        List<AssociationRoleDescriptor> editableRoleList =
            InterlisAssociationRegistrySupport.editableRoles(associationDesc, context)
        def results = associationType.createCriteria().list(max: limit) {
            eq(fixedProperty + ".id", participant.id)
            editableRoleList.each { AssociationRoleDescriptor roleDesc ->
                fetchMode(roleDesc.propertyName(), org.hibernate.FetchMode.JOIN)
            }
            order("id", "asc")
        }
        long total = associationType.createCriteria().get {
            eq(fixedProperty + ".id", participant.id)
            projections {
                count("id")
            }
        } as Long ?: 0L
        List<Map<String, Object>> rows = results.collect { Object instance ->
            describeAssociationRow(associationDesc, context, instance)
        }
        List<Map<String, String>> columns = buildColumns(associationDesc, context)
        String label = resolveLabel(context)
        String quickTargetRole = null
        if (context.createMode() == AssociationCreateMode.QUICK && editableRoleList.size() == 1) {
            quickTargetRole = editableRoleList.get(0).name()
        }
        return [
            contextId: context.id(),
            label: label,
            messageCode: context.messageCode(),
            presentation: context.presentation(),
            createMode: context.createMode().name(),
            writable: context.writable(),
            removable: context.removable(),
            quickTargetRole: quickTargetRole,
            associationController: resolveAssociationController(associationDesc),
            domId: domId(context.id()),
            total: total,
            max: limit,
            offset: 0,
            more: total > limit,
            rows: rows,
            columns: columns,
            emptyMessage: resolveEmptyMessage(context)
        ]
    }

    private List<Map<String, String>> buildColumns(AssociationDescriptor associationDesc,
                                                   AssociationContextDescriptor context) {
        List<Map<String, String>> columns = []
        List<AssociationRoleDescriptor> editableRoleList =
            InterlisAssociationRegistrySupport.editableRoles(associationDesc, context)
        editableRoleList.each { AssociationRoleDescriptor roleDesc ->
            columns.add([
                key: roleDesc.name(),
                label: roleDesc.label() ?: roleDesc.name()
            ])
        }
        List<AssociationAttributeDescriptor> attrs = associationDesc.attributes()
        if (attrs != null) {
            attrs.each { AssociationAttributeDescriptor attrDesc ->
                columns.add([
                    key: attrDesc.propertyName(),
                    label: attrDesc.label() ?: attrDesc.propertyName()
                ])
            }
        }
        return columns
    }

    private String resolveLabel(AssociationContextDescriptor context) {
        String code = context.messageCode()
        if (code != null) {
            try {
                String message = grailsApplication.mainContext.getBean(
                    "org.springframework.context.MessageSource"
                ).getMessage(code, null, null, InterlisMessageSupport.configuredLocale(grailsApplication))
                if (message != null && message != code) {
                    return message
                }
            } catch (Exception ignored) {
            }
        }
        return context.defaultLabel() ?: context.id() ?: ""
    }

    private String resolveEmptyMessage(AssociationContextDescriptor context) {
        String associationName = context.associationName()
        if (associationName == null) {
            return InterlisMessageSupport.text(
                grailsApplication,
                "ili2grails.association.empty",
                "Keine Einträge vorhanden."
            )
        }
        String normalizedName = associationName.replaceAll('[^a-zA-Z0-9]', '')
        String code = "interlis.association.${normalizedName}.empty"
        try {
            String message = grailsApplication.mainContext.getBean(
                "org.springframework.context.MessageSource"
                ).getMessage(code, null, null, InterlisMessageSupport.configuredLocale(grailsApplication))
            if (message != null && message != code) {
                return message
            }
        } catch (Exception ignored) {
        }
        return InterlisMessageSupport.text(
            grailsApplication,
            "ili2grails.association.empty",
            "Keine Einträge vorhanden."
        )
    }

    private String buildAssociationLabel(Object associationInstance,
                                         List<AssociationRoleDescriptor> editableRoles,
                                         List<Map<String, Object>> attrs) {
        List<String> parts = []
        editableRoles.each { AssociationRoleDescriptor roleDesc ->
            String property = roleDesc.propertyName()
            try {
                Object target = associationInstance."${property}"
                String label = target != null
                    ? InterlisRelationshipOptions.optionLabel(grailsApplication, runtimeRegistry, target)
                    : null
                if (label != null && !label.isBlank()) {
                    parts.add(label)
                }
            } catch (Exception ignored) {
            }
        }
        if (!parts.isEmpty()) {
            return parts.join(" - ")
        }
        attrs.each { Map<String, Object> attrDesc ->
            try {
                Object value = associationInstance."${attrDesc.property}"
                if (value != null) {
                    parts.add(value.toString())
                }
            } catch (Exception ignored) {
            }
        }
        return parts.isEmpty() ? (associationInstance.id?.toString() ?: "") : parts.join(" - ")
    }

    private String resolveTargetController(AssociationRoleDescriptor roleDesc) {
        String targetDomainClass = roleDesc.targetDomainClassName()
        if (targetDomainClass == null) {
            return null
        }
        int lastDot = targetDomainClass.lastIndexOf('.')
        String className = lastDot >= 0 ? targetDomainClass.substring(lastDot + 1) : targetDomainClass
        return className[0].toLowerCase() + className[1..-1]
    }

    private String resolveAssociationController(AssociationDescriptor association) {
        String className = association.controllerName()
        if (className == null || className.isBlank()) {
            return null
        }
        return className[0].toLowerCase() + className[1..-1]
    }

    private String domId(String contextId) {
        if (contextId == null) {
            return "assoc-section"
        }
        return "assoc-" + contextId.replaceAll('[^a-zA-Z0-9]', '-')
    }

    private Integer boundedMax(Integer value) {
        Integer requested = value ?: 10
        return Math.min(Math.max(requested, 1), 100)
    }

    private Integer safeOffset(Integer value) {
        return Math.max(value ?: 0, 0)
    }

    private String safeSort(Object value, Class domainType) {
        String requested = value?.toString()?.trim()
        if (requested == null || requested.isBlank()) {
            return "id"
        }
        try {
            if (domainType.declaredFields.any { it.name == requested } ||
                domainType.fields.any { it.name == requested }) {
                return requested
            }
        } catch (Exception ignored) {
        }
        return "id"
    }

    private String safeOrder(Object value) {
        String requested = value?.toString()?.toLowerCase(java.util.Locale.ROOT)
        return requested == "desc" ? "desc" : "asc"
    }
}

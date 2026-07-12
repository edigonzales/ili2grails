package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.generated.InterlisAssociationRegistry

class InterlisAssociationQueryService {

    static transactional = false

    def grailsApplication

    List<Map<String, Object>> sections(Class participantType, Serializable participantId, Integer maxPerSection) {
        if (participantType == null || participantId == null) {
            return []
        }
        Object participant = participantType.get(participantId)
        if (participant == null) {
            return []
        }
        Integer limit = boundedMax(maxPerSection ?: 10)
        List<Map<String, Object>> contexts = InterlisAssociationRegistrySupport.contextsForParticipant(participantType)
        if (contexts.isEmpty()) {
            return []
        }
        List<Map<String, Object>> result = []
        contexts.each { Map<String, Object> ctx ->
            try {
                Map<String, Object> section = buildSection(participantType, participant, ctx, limit)
                if (section != null) {
                    result.add(section)
                }
            } catch (Exception e) {
                log.warn("Failed to build association section for context ${ctx.id} on ${participantType.simpleName}#${participantId}: ${e.message}", e)
            }
        }
        return result
    }

    Map<String, Object> page(Class participantType, Serializable participantId, String contextId,
                             Integer max, Integer offset, String sort, String requestedOrder) {
        Map<String, Object> context = InterlisAssociationRegistrySupport.requireContext(participantType, contextId)
        Object participant = participantType.get(participantId)
        if (participant == null) {
            return [total: 0, rows: [], max: max, offset: offset, contextId: contextId]
        }
        Integer pageMax = boundedMax(max ?: 10)
        Integer pageOffset = safeOffset(offset ?: 0)
        Class associationType = InterlisAssociationRegistrySupport.resolveAssociationClass(grailsApplication, context)
        if (associationType == null) {
            return [total: 0, rows: [], max: pageMax, offset: pageOffset, contextId: contextId]
        }
        String fixedProperty = context.fixedProperty
        String sortField = safeSort(sort, associationType)
        String sortOrder = safeOrder(requestedOrder)
        Map<String, Object> associationDescriptor = InterlisAssociationRegistry.ASSOCIATIONS[context.associationName]
        List<Map<String, Object>> editableRoleList = InterlisAssociationRegistrySupport.editableRoles(associationDescriptor, context)
        def results = associationType.createCriteria().list(max: pageMax, offset: pageOffset) {
            eq(fixedProperty + ".id", participantId)
            editableRoleList.each { Map<String, Object> roleDesc ->
                fetchMode(roleDesc.property, org.hibernate.FetchMode.JOIN)
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
        Map<String, Object> context = InterlisAssociationRegistrySupport.requireContext(participantType, contextId)
        Map<String, Object> associationDesc = InterlisAssociationRegistry.ASSOCIATIONS[context.associationName]
        Map<String, Object> roleDesc = InterlisAssociationRegistrySupport.role(associationDesc, roleName)
        if (roleDesc == null) {
            return [results: [], pagination: [more: false, total: 0, nextOffset: offset]]
        }
        String targetDomainClass = roleDesc.targetDomainClass
        Class targetType = InterlisAssociationRegistrySupport.resolveDomainClass(grailsApplication, targetDomainClass)
        if (targetType == null) {
            return [results: [], pagination: [more: false, total: 0, nextOffset: offset]]
        }
        return InterlisRelationshipOptions.optionPageForTargetType(grailsApplication, targetType, query,
            boundedMax(max ?: 25), safeOffset(offset ?: 0))
    }

    Map<String, Object> describeAssociationRow(Map<String, Object> association, Map<String, Object> context,
                                               Object associationInstance) {
        if (associationInstance == null) {
            return null
        }
        List<Map<String, Object>> counterparts = []
        List<Map<String, Object>> editableRoleList = InterlisAssociationRegistrySupport.editableRoles(association, context)
        editableRoleList.each { Map<String, Object> roleDesc ->
            String property = roleDesc.property
            Object target = associationInstance."${property}"
            if (target != null) {
                String targetController = resolveTargetController(roleDesc)
                counterparts.add([
                    role: roleDesc.name,
                    property: property,
                    id: target.id?.toString(),
                    label: InterlisRelationshipOptions.optionLabel(target),
                    controller: targetController
                ])
            }
        }
        List<Map<String, Object>> attrList = []
        List<Map<String, Object>> attrs = association.attributes as List<Map<String, Object>>
        if (attrs != null) {
            attrs.each { Map<String, Object> attrDesc ->
                String property = attrDesc.property
                Object value = null
                try {
                    value = associationInstance."${property}"
                } catch (Exception ignored) {
                }
                attrList.add([
                    property: property,
                    label: attrDesc.label ?: property,
                    value: value
                ])
            }
        }
        String associationDomainClass = association.domainClassQualifiedName
        boolean deleteAllowed = (context.writable == true) &&
            (context.removable == true) &&
            (context.createMode == "QUICK")
        boolean editAllowed = (context.writable == true) &&
            (context.createMode == "CONTEXTUAL_FORM" || context.createMode == "NARY_CONTEXTUAL_FORM")
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
                                             Map<String, Object> context, Integer limit) {
        Map<String, Object> associationDesc = InterlisAssociationRegistry.ASSOCIATIONS[context.associationName]
        if (associationDesc == null) {
            return null
        }
        Class associationType = InterlisAssociationRegistrySupport.resolveAssociationClass(grailsApplication, context)
        if (associationType == null) {
            return null
        }
        String fixedProperty = context.fixedProperty
        List<Map<String, Object>> editableRoleList = InterlisAssociationRegistrySupport.editableRoles(associationDesc, context)
        def results = associationType.createCriteria().list(max: limit) {
            eq(fixedProperty + ".id", participant.id)
            editableRoleList.each { Map<String, Object> roleDesc ->
                fetchMode(roleDesc.property, org.hibernate.FetchMode.JOIN)
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
        if (context.createMode == "QUICK" && editableRoleList.size() == 1) {
            quickTargetRole = editableRoleList.get(0).name
        }
        return [
            contextId: context.id,
            label: label,
            messageCode: context.messageCode,
            presentation: context.presentation,
            createMode: context.createMode,
            writable: context.writable ?: false,
            removable: context.removable ?: false,
            quickTargetRole: quickTargetRole,
            associationController: resolveAssociationController(associationDesc),
            domId: domId(context.id),
            total: total,
            max: limit,
            offset: 0,
            more: total > limit,
            rows: rows,
            columns: columns,
            emptyMessage: resolveEmptyMessage(context)
        ]
    }

    private List<Map<String, String>> buildColumns(Map<String, Object> associationDesc, Map<String, Object> context) {
        List<Map<String, String>> columns = []
        List<Map<String, Object>> editableRoleList = InterlisAssociationRegistrySupport.editableRoles(associationDesc, context)
        editableRoleList.each { Map<String, Object> roleDesc ->
            columns.add([
                key: roleDesc.name,
                label: roleDesc.label ?: roleDesc.name
            ])
        }
        List<Map<String, Object>> attrs = associationDesc.attributes as List<Map<String, Object>>
        if (attrs != null) {
            attrs.each { Map<String, Object> attrDesc ->
                columns.add([
                    key: attrDesc.property,
                    label: attrDesc.label ?: attrDesc.property
                ])
            }
        }
        return columns
    }

    private String resolveLabel(Map<String, Object> context) {
        String code = context.messageCode
        if (code != null) {
            try {
                String message = grailsApplication.mainContext.getBean(
                    "org.springframework.context.MessageSource"
                ).getMessage(code, null, null, java.util.Locale.getDefault())
                if (message != null && message != code) {
                    return message
                }
            } catch (Exception ignored) {
            }
        }
        return context.defaultLabel ?: context.id ?: ""
    }

    private String resolveEmptyMessage(Map<String, Object> context) {
        String associationName = context.associationName
        if (associationName == null) {
            return "Keine Einträge vorhanden."
        }
        String normalizedName = associationName.replaceAll('[^a-zA-Z0-9]', '')
        String code = "interlis.association.${normalizedName}.empty"
        try {
            String message = grailsApplication.mainContext.getBean(
                "org.springframework.context.MessageSource"
            ).getMessage(code, null, null, java.util.Locale.getDefault())
            if (message != null && message != code) {
                return message
            }
        } catch (Exception ignored) {
        }
        return "Keine Einträge vorhanden."
    }

    private String buildAssociationLabel(Object associationInstance,
                                         List<Map<String, Object>> editableRoles,
                                         List<Map<String, Object>> attrs) {
        List<String> parts = []
        editableRoles.each { Map<String, Object> roleDesc ->
            String property = roleDesc.property
            try {
                Object target = associationInstance."${property}"
                String label = target != null ? InterlisRelationshipOptions.optionLabel(target) : null
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

    private String resolveTargetController(Map<String, Object> roleDesc) {
        String targetDomainClass = roleDesc.targetDomainClass
        if (targetDomainClass == null) {
            return null
        }
        int lastDot = targetDomainClass.lastIndexOf('.')
        String className = lastDot >= 0 ? targetDomainClass.substring(lastDot + 1) : targetDomainClass
        return className[0].toLowerCase() + className[1..-1]
    }

    private String resolveAssociationController(Map<String, Object> association) {
        String className = association.domainClassName
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

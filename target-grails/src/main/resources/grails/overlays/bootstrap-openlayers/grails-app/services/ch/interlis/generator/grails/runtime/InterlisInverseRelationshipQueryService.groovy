package ch.interlis.generator.grails.runtime

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class InterlisInverseRelationshipQueryService {

    static transactional = false

    def grailsApplication
    def grailsLinkGenerator

    List<Map<String, Object>> sections(Class ownerType, Serializable ownerId, Integer maxPerSection) {
        if (ownerType == null || ownerId == null) {
            return []
        }
        Object owner = ownerType.get(ownerId)
        if (owner == null) {
            return []
        }
        int limit = boundedMax(maxPerSection ?: 10)
        return InterlisInverseRelationshipSupport.descriptors(grailsApplication, ownerType).collect {
            Map<String, Object> descriptor ->
                buildSection(owner, descriptor, limit)
        }.findAll { it != null } as List<Map<String, Object>>
    }

    Map<String, Object> page(Class ownerType,
                             Serializable ownerId,
                             String relationshipName,
                             Integer max,
                             Integer offset) {
        return page(ownerType, ownerId, relationshipName, null, max, offset)
    }

    Map<String, Object> page(Class ownerType,
                             Serializable ownerId,
                             String relationshipName,
                             String query,
                             Integer max,
                             Integer offset) {
        Map<String, Object> descriptor = InterlisInverseRelationshipSupport.requireDescriptor(
            grailsApplication,
            ownerType,
            relationshipName
        )
        if (descriptor.visible != true) {
            throw new InterlisInverseRelationshipSupport.InverseRelationshipNotFoundException(
                "Inverse relationship '${relationshipName}' is disabled"
            )
        }
        return relationshipPage(
            ownerId,
            descriptor,
            query?.trim(),
            boundedMax(max ?: 10),
            safeOffset(offset ?: 0)
        )
    }

    Map<String, Object> optionPage(Class ownerType,
                                   Serializable ownerId,
                                   String relationshipName,
                                   String query,
                                   Integer max,
                                   Integer offset) {
        Map<String, Object> descriptor = InterlisInverseRelationshipSupport.requireDescriptor(
            grailsApplication,
            ownerType,
            relationshipName
        )
        if (descriptor.visible != true || descriptor.writable != true) {
            return [results: [], pagination: [more: false, total: 0, nextOffset: safeOffset(offset ?: 0)]]
        }
        Class relatedType = InterlisInverseRelationshipSupport.resolveRelatedClass(
            grailsApplication,
            descriptor
        )
        return InterlisRelationshipOptions.optionPageForInverseRelationship(
            grailsApplication,
            relatedType,
            descriptor.relatedProperty?.toString(),
            ownerId,
            query,
            boundedMax(max ?: 25),
            safeOffset(offset ?: 0)
        )
    }

    private Map<String, Object> buildSection(Object owner,
                                             Map<String, Object> descriptor,
                                             int limit) {
        Map<String, Object> page = relationshipPage(
            owner.id as Serializable,
            descriptor,
            null,
            limit,
            0
        )
        return [
            ownerId: owner.id?.toString(),
            name: descriptor.name,
            label: descriptor.label,
            relatedLabel: descriptor.relatedLabel,
            writable: descriptor.writable == true,
            mandatory: descriptor.mandatory == true,
            domId: domId(descriptor.name?.toString()),
            total: page.total,
            max: page.max,
            offset: page.offset,
            more: page.more,
            rows: page.rows
        ]
    }

    private Map<String, Object> relationshipPage(Serializable ownerId,
                                                 Map<String, Object> descriptor,
                                                 String query,
                                                 int max,
                                                 int offset) {
        Class relatedType = InterlisInverseRelationshipSupport.resolveRelatedClass(
            grailsApplication,
            descriptor
        )
        String relatedProperty = descriptor.relatedProperty?.toString()
        if (relatedType == null || relatedProperty == null || relatedProperty.isBlank()) {
            return [total: 0, rows: [], max: max, offset: offset, more: false]
        }
        List<String> searchColumns = query
            ? InterlisRelationshipOptions.inverseRelationshipSearchFields(grailsApplication, relatedType)
            : []
        def results = relatedType.createCriteria().list(max: max, offset: offset, sort: "id", order: "asc") {
            and {
                eq(relatedProperty + ".id", ownerId)
                if (query) {
                    if (searchColumns.isEmpty()) {
                        eq("id", null)
                    } else {
                        or {
                            searchColumns.each { String column ->
                                ilike(column, "%" + query + "%")
                            }
                        }
                    }
                }
            }
        }
        Number total = relatedType.createCriteria().get {
            and {
                eq(relatedProperty + ".id", ownerId)
                if (query) {
                    if (searchColumns.isEmpty()) {
                        eq("id", null)
                    } else {
                        or {
                            searchColumns.each { String column ->
                                ilike(column, "%" + query + "%")
                            }
                        }
                    }
                }
            }
            projections {
                count("id")
            }
        } as Number ?: 0
        String controller = InterlisInverseRelationshipSupport.controllerForClass(relatedType)
        List<Map<String, Object>> rows = (results as List<Object>).collect { Object related ->
            [
                id: related.id?.toString(),
                label: InterlisRelationshipOptions.optionLabel(grailsApplication, related),
                controller: controller,
                url: relatedUrl(controller, related.id)
            ]
        }
        return [
            total: total.longValue(),
            rows: rows,
            max: max,
            offset: offset,
            more: offset + rows.size() < total.longValue()
        ]
    }

    private int boundedMax(Integer max) {
        return Math.max(1, Math.min(max ?: 10, 100))
    }

    private int safeOffset(Integer offset) {
        return Math.max(offset ?: 0, 0)
    }

    private String domId(String value) {
        return "inverse-" + (value ?: "relationship").replaceAll("[^A-Za-z0-9_-]", "-")
    }

    private String relatedUrl(String controller, Object id) {
        if (controller == null || id == null || grailsLinkGenerator == null) {
            return null
        }
        try {
            return grailsLinkGenerator.link(
                controller: controller,
                action: "show",
                id: id,
                absolute: false
            )
        } catch (IllegalStateException missingServerUrl) {
            // Integration tests and non-request callers have no server URL.
            // Standard generated CRUD mappings still provide this relative route.
            return "/${controller}/show/${URLEncoder.encode(id.toString(), StandardCharsets.UTF_8)}"
        }
    }
}

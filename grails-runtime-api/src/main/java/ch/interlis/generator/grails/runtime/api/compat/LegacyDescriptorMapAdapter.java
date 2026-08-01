package ch.interlis.generator.grails.runtime.api.compat;

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationAttributeDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind;
import ch.interlis.generator.grails.runtime.api.descriptor.EntityDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.FieldDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.GeometryDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.RelationshipDescriptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy compatibility adapter converting typed descriptors back to the
 * pre-P1 map shape consumed by the copied runtime during migration.
 *
 * @deprecated forRemoval = true — the map contracts are replaced by the typed
 *     descriptor API. New runtime code must never use this adapter.
 */
@Deprecated(forRemoval = true)
public final class LegacyDescriptorMapAdapter {

    private LegacyDescriptorMapAdapter() {
    }

    public static Map<String, Object> toLegacyDomainMap(DomainDescriptor descriptor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("domainClassName", descriptor.domainClassName());
        map.put("controller", descriptor.controllerName());
        map.put("iliName", descriptor.iliName());
        map.put("modelName", descriptor.modelName());
        map.put("topicName", descriptor.topicName());
        map.put("className", descriptor.className());
        map.put("label", descriptor.label());
        map.put("navigationVisible", descriptor.navigationVisible());
        map.put("associationDomain", descriptor.kind() == DomainKind.ASSOCIATION);
        return map;
    }

    public static Map<String, Object> toLegacyAssociationMap(AssociationDescriptor descriptor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("associationName", descriptor.associationName());
        map.put("iliClassName", descriptor.iliClassName());
        map.put("domainClassName", simpleName(descriptor.domainClassName()));
        map.put("domainClassQualifiedName", descriptor.domainClassName());
        map.put("controllerName", descriptor.controllerName());
        map.put("viewPath", descriptor.viewPath());
        map.put("physicalTable", descriptor.physicalTable());
        map.put("physicalSqlName", descriptor.physicalSqlName());
        map.put("storageKind", descriptor.storageKind() == null ? null : descriptor.storageKind().name());
        map.put("writable", descriptor.writable());
        map.put("showInNavigation", descriptor.showInNavigation());
        List<Object> roles = new ArrayList<>();
        for (AssociationRoleDescriptor role : descriptor.roles()) {
            roles.add(toLegacyRoleMap(role));
        }
        map.put("roles", roles);
        List<Object> attributes = new ArrayList<>();
        for (AssociationAttributeDescriptor attribute : descriptor.attributes()) {
            attributes.add(toLegacyAttributeMap(attribute));
        }
        map.put("attributes", attributes);
        map.put("diagnostics", new ArrayList<>(descriptor.diagnostics()));
        return map;
    }

    public static Map<String, Object> toLegacyRoleMap(AssociationRoleDescriptor descriptor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", descriptor.name());
        map.put("label", descriptor.label());
        map.put("property", descriptor.propertyName());
        map.put("targetIliClass", descriptor.targetIliClassName());
        map.put("targetDomainClass", descriptor.targetDomainClassName());
        map.put("min", descriptor.minCardinality());
        map.put("max", descriptor.maxCardinality());
        map.put("mandatory", descriptor.mandatory());
        map.put("ordered", descriptor.ordered());
        map.put("external", descriptor.external());
        map.put("composition", descriptor.composition());
        return map;
    }

    public static Map<String, Object> toLegacyAttributeMap(AssociationAttributeDescriptor descriptor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("iliName", descriptor.iliName());
        map.put("property", descriptor.propertyName());
        map.put("type", descriptor.javaType());
        map.put("coreType", descriptor.coreType() == null ? null : descriptor.coreType().name());
        map.put("label", descriptor.label());
        map.put("mandatory", descriptor.mandatory());
        map.put("maxLength", descriptor.maxLength());
        map.put("unit", descriptor.unit());
        map.put("enumType", descriptor.enumType());
        map.put("geometry", descriptor.geometry());
        return map;
    }

    public static Map<String, Object> toLegacyContextMap(AssociationContextDescriptor descriptor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", descriptor.id());
        map.put("associationName", descriptor.associationName());
        map.put("participantDomainClass", descriptor.participantDomainClassName());
        map.put("fixedRole", descriptor.fixedRoleName());
        map.put("fixedProperty", descriptor.fixedPropertyName());
        map.put("editableRoles", new ArrayList<>(descriptor.editableRoleNames()));
        map.put("editableProperties", new ArrayList<>(descriptor.editablePropertyNames()));
        map.put("defaultLabel", descriptor.defaultLabel());
        map.put("messageCode", descriptor.messageCode());
        map.put("presentation", descriptor.presentation());
        map.put("createMode", descriptor.createMode() == null ? null : descriptor.createMode().name());
        map.put("writable", descriptor.writable());
        map.put("removable", descriptor.removable());
        map.put("showAssociationObjectLink", descriptor.showAssociationObjectLink());
        map.put("perspectiveMin", descriptor.perspectiveMin());
        map.put("perspectiveMax", descriptor.perspectiveMax());
        map.put("diagnostics", new ArrayList<>(descriptor.diagnostics()));
        return map;
    }

    public static Map<String, Object> toLegacyEntityMap(EntityDescriptor descriptor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("iliName", descriptor.iliName());
        map.put("kind", descriptor.kind() == DomainKind.ASSOCIATION ? "ASSOCIATION" : descriptor.kind().name());
        map.put("showInNavigation", descriptor.showInNavigation());
        return map;
    }

    public static Map<String, Object> toLegacyFieldMap(FieldDescriptor descriptor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", descriptor.name());
        map.put("iliName", descriptor.iliName());
        map.put("javaType", descriptor.javaType());
        map.put("coreType", descriptor.coreType() == null ? null : descriptor.coreType().name());
        map.put("kind", descriptor.kind() == null ? null : descriptor.kind().name());
        map.put("label", descriptor.label());
        map.put("mandatory", descriptor.mandatory());
        map.put("maxLength", descriptor.maxLength());
        map.put("minValue", descriptor.minValue());
        map.put("maxValue", descriptor.maxValue());
        map.put("precision", descriptor.precision());
        map.put("scale", descriptor.scale());
        map.put("unit", descriptor.unit());
        map.put("enumType", descriptor.enumType());
        return map;
    }

    public static Map<String, Object> toLegacyRelationshipMap(RelationshipDescriptor descriptor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", descriptor.name());
        map.put("propertyName", descriptor.propertyName());
        map.put("targetDomainClassName", descriptor.targetDomainClassName());
        map.put("semanticKind", descriptor.semanticKind());
        map.put("label", descriptor.label());
        map.put("sourceAttribute", descriptor.sourceAttribute());
        map.put("targetRoleName", descriptor.targetRoleName());
        map.put("mandatory", descriptor.mandatory());
        return map;
    }

    public static Map<String, Object> toLegacyInverseRelationshipMap(InverseRelationshipDescriptor descriptor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", descriptor.name());
        map.put("label", descriptor.label());
        map.put("ownerIliClassName", descriptor.ownerIliClassName());
        map.put("relatedIliName", descriptor.relatedIliClassName());
        map.put("relatedDomainClass", descriptor.relatedDomainClassName());
        map.put("relatedController", descriptor.relatedControllerName());
        map.put("relatedProperty", descriptor.relatedPropertyName());
        map.put("relatedLabel", descriptor.relatedLabel());
        map.put("writable", descriptor.writable());
        map.put("visible", descriptor.visible());
        map.put("mode", descriptor.mode() == null ? null : descriptor.mode().name().toLowerCase(java.util.Locale.ROOT));
        map.put("mandatory", false);
        return map;
    }

    public static Map<String, Object> toLegacyGeometryMap(GeometryDescriptor descriptor) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fieldName", descriptor.fieldName());
        map.put("srid", descriptor.srid());
        map.put("kind", descriptor.kind());
        map.put("hasZ", descriptor.hasZ());
        map.put("hasM", descriptor.hasM());
        map.put("allowEmpty", descriptor.allowEmpty());
        return map;
    }

    private static String simpleName(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return null;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot < 0 ? qualifiedName : qualifiedName.substring(lastDot + 1);
    }
}

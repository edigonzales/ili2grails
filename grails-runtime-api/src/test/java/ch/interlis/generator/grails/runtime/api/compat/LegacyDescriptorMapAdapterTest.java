package ch.interlis.generator.grails.runtime.api.compat;

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationCreateMode;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class LegacyDescriptorMapAdapterTest {

    @Test
    void domainMapMatchesPreP1KeyShape() {
        DomainDescriptor descriptor = new DomainDescriptor(
            "M.T.P", "M", "T", "com.example.P", "p", "P", "P", DomainKind.CLASS,
            true, null, Map.of(), Map.of(), Map.of(), Map.of());
        Map<String, Object> map = LegacyDescriptorMapAdapter.toLegacyDomainMap(descriptor);
        assertThat(map).containsEntry("domainClassName", "com.example.P")
            .containsEntry("controller", "p")
            .containsEntry("iliName", "M.T.P")
            .containsEntry("modelName", "M")
            .containsEntry("topicName", "T")
            .containsEntry("className", "P")
            .containsEntry("label", "P")
            .containsEntry("navigationVisible", true)
            .containsEntry("associationDomain", false);
    }

    @Test
    void associationMapKeepsLegacyKeysAndEnumNames() {
        AssociationRoleDescriptor role = new AssociationRoleDescriptor(
            "roleP", "P", "propertyP", "M.T.P", "com.example.P",
            0, 1, false, false, false, false);
        AssociationDescriptor descriptor = new AssociationDescriptor(
            "M.T.Assoc", "M.T.Assoc", "com.example.Assoc", "assoc", "assoc",
            "assoc", "assoc", AssociationStorageKind.LINK_ENTITY, true, true,
            List.of(role), List.of(), List.of());
        Map<String, Object> map = LegacyDescriptorMapAdapter.toLegacyAssociationMap(descriptor);
        assertThat(map).containsEntry("associationName", "M.T.Assoc")
            .containsEntry("domainClassName", "Assoc")
            .containsEntry("domainClassQualifiedName", "com.example.Assoc")
            .containsEntry("controllerName", "assoc")
            .containsEntry("storageKind", "LINK_ENTITY")
            .containsEntry("writable", true);
        List<?> roles = (List<?>) map.get("roles");
        assertThat(roles).hasSize(1);
        Map<?, ?> roleMap = (Map<?, ?>) roles.get(0);
        assertThat(roleMap.get("name")).isEqualTo("roleP");
        assertThat(roleMap.get("property")).isEqualTo("propertyP");
        assertThat(roleMap.get("targetIliClass")).isEqualTo("M.T.P");
        assertThat(roleMap.get("targetDomainClass")).isEqualTo("com.example.P");
        assertThat(roleMap.get("min")).isEqualTo(0);
        assertThat(roleMap.get("max")).isEqualTo(1);
    }

    @Test
    void contextMapKeepsLegacyKeys() {
        AssociationContextDescriptor context = new AssociationContextDescriptor(
            "ctx-1", "M.T.Assoc", "com.example.P", "roleP", "propertyP",
            List.of("roleT"), List.of("propertyT"), "Label", "code", "QUICK_LINK",
            AssociationCreateMode.QUICK, true, true, true, 0, -1, List.of());
        Map<String, Object> map = LegacyDescriptorMapAdapter.toLegacyContextMap(context);
        assertThat(map).containsEntry("id", "ctx-1")
            .containsEntry("associationName", "M.T.Assoc")
            .containsEntry("participantDomainClass", "com.example.P")
            .containsEntry("fixedRole", "roleP")
            .containsEntry("fixedProperty", "propertyP")
            .containsEntry("createMode", "QUICK")
            .containsEntry("presentation", "QUICK_LINK")
            .containsEntry("writable", true)
            .containsEntry("perspectiveMax", -1);
        assertThat(map.get("editableRoles")).isEqualTo(List.of("roleT"));
        assertThat(map.get("editableProperties")).isEqualTo(List.of("propertyT"));
    }

    @Test
    void entityMapKeepsAssociationKindMarker() {
        var entity = new ch.interlis.generator.grails.runtime.api.descriptor.EntityDescriptor(
            "M.T.Assoc", DomainKind.ASSOCIATION, false);
        Map<String, Object> map = LegacyDescriptorMapAdapter.toLegacyEntityMap(entity);
        assertThat(map).containsEntry("iliName", "M.T.Assoc")
            .containsEntry("kind", "ASSOCIATION")
            .containsEntry("showInNavigation", false);
    }
}

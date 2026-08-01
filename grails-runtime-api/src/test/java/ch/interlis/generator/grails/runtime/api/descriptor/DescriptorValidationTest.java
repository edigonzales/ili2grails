package ch.interlis.generator.grails.runtime.api.descriptor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DescriptorValidationTest {

    @Test
    void domainDescriptorRequiresIliNameAndDomainClassName() {
        assertThatThrownBy(() -> new DomainDescriptor(
            null, "M", "T", "com.example.C", "c", "C", "Label",
            DomainKind.CLASS, true, null, Map.of(), Map.of(), Map.of(), Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("iliName");
        assertThatThrownBy(() -> new DomainDescriptor(
            "M.T.C", "M", "T", "", "c", "C", "Label",
            DomainKind.CLASS, true, null, Map.of(), Map.of(), Map.of(), Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("domainClassName");
    }

    @Test
    void domainDescriptorCollectionsAreImmutable() {
        Map<String, FieldDescriptor> fields = new LinkedHashMap<>();
        fields.put("name", new FieldDescriptor("name", "name", "String",
            RuntimeCoreType.TEXT, FieldKind.SCALAR, "Name", false, null, null, null, null, null, null, null));
        DomainDescriptor descriptor = new DomainDescriptor(
            "M.T.C", "M", "T", "com.example.C", "c", "C", "Label",
            DomainKind.CLASS, true, null, fields, Map.of(), Map.of(), Map.of());
        assertThatThrownBy(() -> descriptor.fields().put("x", null))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> descriptor.fields().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void domainDescriptorRejectsNullMapValues() {
        Map<String, FieldDescriptor> fields = new LinkedHashMap<>();
        fields.put("name", null);
        assertThatThrownBy(() -> new DomainDescriptor(
            "M.T.C", "M", "T", "com.example.C", "c", "C", "Label",
            DomainKind.CLASS, true, null, fields, Map.of(), Map.of(), Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void associationRoleCardinalityMustNotInvert() {
        assertThatThrownBy(() -> new AssociationRoleDescriptor(
            "roleA", "A", "propertyA", "M.T.B", "com.example.B",
            3, 1, false, false, false, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxCardinality");
        assertThatThrownBy(() -> new AssociationRoleDescriptor(
            "roleA", "A", "propertyA", "M.T.B", "com.example.B",
            -2, 1, false, false, false, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("-1");
    }

    @Test
    void associationRoleAllowsUnboundedSentinel() {
        AssociationRoleDescriptor role = new AssociationRoleDescriptor(
            "roleA", "A", "propertyA", "M.T.B", "com.example.B",
            0, -1, false, false, false, false);
        assertThat(role.isUnbounded()).isTrue();
    }

    @Test
    void associationRejectsDuplicateRoleNames() {
        AssociationRoleDescriptor first = new AssociationRoleDescriptor(
            "roleA", "A", "propertyA", "M.T.B", "com.example.B",
            0, 1, false, false, false, false);
        AssociationRoleDescriptor duplicate = new AssociationRoleDescriptor(
            "roleA", "A2", "propertyA2", "M.T.C", "com.example.C",
            0, 1, false, false, false, false);
        assertThatThrownBy(() -> new AssociationDescriptor(
            "M.T.Assoc", "M.T.Assoc", "com.example.Assoc", "assoc", "assoc",
            "assoc", "assoc", AssociationStorageKind.LINK_ENTITY, true, true,
            List.of(first, duplicate), List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate");
    }

    @Test
    void writableUnmappedAssociationIsRejected() {
        AssociationRoleDescriptor role = new AssociationRoleDescriptor(
            "roleA", "A", "propertyA", "M.T.B", "com.example.B",
            0, 1, false, false, false, false);
        assertThatThrownBy(() -> new AssociationDescriptor(
            "M.T.Assoc", "M.T.Assoc", "com.example.Assoc", "assoc", "assoc",
            null, null, AssociationStorageKind.UNMAPPED, true, true,
            List.of(role), List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("storage kind");
    }

    @Test
    void contextRequiresCreateModeWhenWritable() {
        assertThatThrownBy(() -> new AssociationContextDescriptor(
            "ctx-1", "M.T.Assoc", "com.example.P", "roleA", "propertyA",
            List.of("roleB"), List.of("propertyB"), "Label", "code", "QUICK_LINK",
            AssociationCreateMode.NONE, true, true, true, 0, -1, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("create mode");
    }

    @Test
    void contextQuickModeRequiresFixedRole() {
        assertThatThrownBy(() -> new AssociationContextDescriptor(
            "ctx-1", "M.T.Assoc", "com.example.P", null, "propertyA",
            List.of("roleB"), List.of("propertyB"), "Label", "code", "QUICK_LINK",
            AssociationCreateMode.QUICK, true, true, true, 0, -1, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fixed role");
    }

    @Test
    void inverseRelationshipWritabilityDowngradesButNeverUpgrades() {
        InverseRelationshipDescriptor writable = new InverseRelationshipDescriptor(
            "children", "Kinder", "M.Owner", "M.Related", "com.example.Related",
            "related", "owner", "Besitzer", true, true, InverseRelationshipMode.AUTO);
        assertThat(writable.writable()).isTrue();

        InverseRelationshipDescriptor downgraded = new InverseRelationshipDescriptor(
            "children", "Kinder", "M.Owner", "M.Related", "com.example.Related",
            "related", "owner", "Besitzer", true, true, InverseRelationshipMode.READ_ONLY);
        assertThat(downgraded.writable()).isFalse();

        InverseRelationshipDescriptor off = new InverseRelationshipDescriptor(
            "children", "Kinder", "M.Owner", "M.Related", "com.example.Related",
            "related", "owner", "Besitzer", true, false, InverseRelationshipMode.OFF);
        assertThat(off.writable()).isFalse();

        InverseRelationshipDescriptor unsafeUpgrade = new InverseRelationshipDescriptor(
            "children", "Kinder", "M.Owner", "M.Related", "com.example.Related",
            "related", "owner", "Besitzer", false, true, InverseRelationshipMode.EDITABLE);
        assertThat(unsafeUpgrade.writable()).isFalse();
    }

    @Test
    void formDescriptorRejectsDuplicateSectionTitles() {
        FormSectionDescriptor a = new FormSectionDescriptor("Basisdaten", List.of("name"));
        FormSectionDescriptor b = new FormSectionDescriptor("Basisdaten", List.of("code"));
        assertThatThrownBy(() -> new FormDescriptor(List.of(a, b)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate");
    }
}

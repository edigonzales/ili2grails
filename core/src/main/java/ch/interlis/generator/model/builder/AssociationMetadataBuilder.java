package ch.interlis.generator.model.builder;

import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.AttributeMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable Builder für {@link AssociationMetadata}.
 */
public final class AssociationMetadataBuilder {

    private String name;
    private String associationClass;
    private String physicalTable;
    private String physicalSqlName;
    private final List<AssociationRoleMetadataBuilder> roleBuilders = new ArrayList<>();
    private final Map<String, AttributeMetadataBuilder> attributeBuilders = new LinkedHashMap<>();

    public AssociationMetadataBuilder(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.associationClass = name;
    }

    public static AssociationMetadataBuilder from(AssociationMetadata association) {
        AssociationMetadataBuilder builder = new AssociationMetadataBuilder(association.getName());
        builder.associationClass = association.getAssociationClass();
        builder.physicalTable = association.getPhysicalTable();
        builder.physicalSqlName = association.getPhysicalSqlName();
        for (AssociationRoleMetadata role : association.getRoles()) {
            builder.roleBuilders.add(AssociationRoleMetadataBuilder.from(role));
        }
        for (AttributeMetadata attribute : association.getAllAttributes()) {
            builder.attributeBuilders.put(attribute.getName(), AttributeMetadataBuilder.from(attribute));
        }
        return builder;
    }

    public String name() {
        return name;
    }

    public String associationClass() {
        return associationClass;
    }

    public void replaceRoles(List<AssociationRoleMetadataBuilder> roles) {
        roleBuilders.clear();
        roleBuilders.addAll(roles);
    }

    public void replaceAttributes(Map<String, AttributeMetadataBuilder> attributes) {
        attributeBuilders.clear();
        attributeBuilders.putAll(attributes);
    }

    public AssociationMetadataBuilder associationClass(String associationClass) {
        this.associationClass = associationClass;
        return this;
    }

    public AssociationMetadataBuilder physicalTable(String physicalTable) {
        this.physicalTable = physicalTable;
        return this;
    }

    public AssociationMetadataBuilder physicalSqlName(String physicalSqlName) {
        this.physicalSqlName = physicalSqlName;
        return this;
    }

    public AssociationMetadataBuilder role(AssociationRoleMetadataBuilder role) {
        Objects.requireNonNull(role, "role");
        for (int index = 0; index < roleBuilders.size(); index++) {
            AssociationRoleMetadataBuilder existing = roleBuilders.get(index);
            if (existing.name().equals(role.name())) {
                roleBuilders.set(index, role);
                return this;
            }
        }
        roleBuilders.add(role);
        return this;
    }

    public AssociationRoleMetadataBuilder roleBuilder(String roleName) {
        Objects.requireNonNull(roleName, "roleName");
        AssociationRoleMetadataBuilder existing = roleBuilders.stream()
            .filter(role -> roleName.equals(role.name()))
            .findFirst()
            .orElse(null);
        if (existing != null) {
            return existing;
        }
        AssociationRoleMetadataBuilder created = new AssociationRoleMetadataBuilder(roleName);
        roleBuilders.add(created);
        return created;
    }

    public AssociationMetadataBuilder attribute(AttributeMetadataBuilder attribute) {
        Objects.requireNonNull(attribute, "attribute");
        if (attributeBuilders.containsKey(attribute.name())) {
            throw new IllegalArgumentException(
                "Duplicate attribute name '" + attribute.name() + "' in association '" + name + "'");
        }
        attributeBuilders.put(attribute.name(), attribute);
        return this;
    }

    public List<AssociationRoleMetadataBuilder> roleBuilders() {
        return java.util.Collections.unmodifiableList(roleBuilders);
    }

    public Map<String, AttributeMetadataBuilder> attributeBuilders() {
        return java.util.Collections.unmodifiableMap(attributeBuilders);
    }

    /** Unvalidierter Build; Abschluss über die ModelMetadataFactory. */
    public AssociationMetadata buildUnchecked() {
        List<AssociationRoleMetadata> roles = new ArrayList<>();
        for (AssociationRoleMetadataBuilder roleBuilder : roleBuilders) {
            roles.add(roleBuilder.buildUnchecked());
        }
        Map<String, AttributeMetadata> attributes = new LinkedHashMap<>();
        attributeBuilders.forEach((attributeName, builder) ->
            attributes.put(attributeName, builder.buildUnchecked()));
        return new AssociationMetadata(
            name,
            associationClass,
            physicalTable,
            physicalSqlName,
            roles,
            attributes
        );
    }
}

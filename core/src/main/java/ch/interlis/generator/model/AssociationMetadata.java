package ch.interlis.generator.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable INTERLIS-Association innerhalb der Core-IR.
 */
public final class AssociationMetadata {

    private final String name;
    private final String associationClass;
    private final String physicalTable;
    private final String physicalSqlName;
    private final List<AssociationRoleMetadata> roles;
    private final Map<String, AttributeMetadata> attributes;

    public AssociationMetadata(String name,
                        String associationClass,
                        String physicalTable,
                        String physicalSqlName,
                        List<AssociationRoleMetadata> roles,
                        Map<String, AttributeMetadata> attributes) {
        this.name = Objects.requireNonNull(name, "name");
        this.associationClass = associationClass;
        this.physicalTable = physicalTable;
        this.physicalSqlName = physicalSqlName;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.attributes = attributes == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    public static ch.interlis.generator.model.builder.AssociationMetadataBuilder builder(String name) {
        return new ch.interlis.generator.model.builder.AssociationMetadataBuilder(name);
    }

    public ch.interlis.generator.model.builder.AssociationMetadataBuilder toBuilder() {
        return ch.interlis.generator.model.builder.AssociationMetadataBuilder.from(this);
    }

    public Optional<AssociationRoleMetadata> findRole(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return roles.stream()
            .filter(role -> name.equals(role.getName()))
            .findFirst();
    }

    public AssociationRoleMetadata getRole(String name) {
        return findRole(name).orElse(null);
    }

    public String getName() {
        return name;
    }

    public String getAssociationClass() {
        return associationClass;
    }

    public String getPhysicalTable() {
        return physicalTable;
    }

    public String getPhysicalSqlName() {
        return physicalSqlName;
    }

    public List<AssociationRoleMetadata> getRoles() {
        return roles;
    }

    public Collection<AttributeMetadata> getAllAttributes() {
        return attributes.values();
    }

    public Map<String, AttributeMetadata> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "AssociationMetadata{" +
            "name='" + name + '\'' +
            ", roles=" + roles.size() +
            ", attributes=" + attributes.size() +
            '}';
    }
}

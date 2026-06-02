package ch.interlis.generator.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * INTERLIS-Association als eigener Core-IR-Baustein.
 */
public class AssociationMetadata {

    private String name;
    private String associationClass;
    private String physicalTable;
    private String physicalSqlName;
    private List<AssociationRoleMetadata> roles = new ArrayList<>();
    private Map<String, AttributeMetadata> attributes = new LinkedHashMap<>();

    public AssociationMetadata(String name) {
        this.name = name;
        this.associationClass = name;
    }

    public void addRole(AssociationRoleMetadata role) {
        Objects.requireNonNull(role, "role");
        for (int i = 0; i < roles.size(); i++) {
            AssociationRoleMetadata existing = roles.get(i);
            if (sameRole(existing, role)) {
                roles.set(i, role);
                return;
            }
        }
        roles.add(role);
    }

    public AssociationRoleMetadata getRole(String name) {
        if (name == null) {
            return null;
        }
        return roles.stream()
            .filter(role -> name.equals(role.getName()))
            .findFirst()
            .orElse(null);
    }

    public void addAttribute(AttributeMetadata attribute) {
        Objects.requireNonNull(attribute, "attribute");
        attributes.put(attribute.getName(), attribute);
    }

    public Collection<AttributeMetadata> getAllAttributes() {
        return attributes.values();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAssociationClass() {
        return associationClass;
    }

    public void setAssociationClass(String associationClass) {
        this.associationClass = associationClass;
    }

    public String getPhysicalTable() {
        return physicalTable;
    }

    public void setPhysicalTable(String physicalTable) {
        this.physicalTable = physicalTable;
    }

    public String getPhysicalSqlName() {
        return physicalSqlName;
    }

    public void setPhysicalSqlName(String physicalSqlName) {
        this.physicalSqlName = physicalSqlName;
    }

    public List<AssociationRoleMetadata> getRoles() {
        return roles;
    }

    public void setRoles(List<AssociationRoleMetadata> roles) {
        this.roles = roles != null ? new ArrayList<>(roles) : new ArrayList<>();
    }

    public Map<String, AttributeMetadata> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, AttributeMetadata> attributes) {
        this.attributes = attributes != null ? new LinkedHashMap<>(attributes) : new LinkedHashMap<>();
    }

    private boolean sameRole(AssociationRoleMetadata left, AssociationRoleMetadata right) {
        return Objects.equals(left.getName(), right.getName())
            && Objects.equals(left.getTargetClass(), right.getTargetClass())
            && Objects.equals(left.getSourceAttribute(), right.getSourceAttribute());
    }
}

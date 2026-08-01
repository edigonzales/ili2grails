package ch.interlis.generator.grails.runtime.api.descriptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable descriptor of a generated domain entry (class, structure or
 * association domain).
 */
public record DomainDescriptor(
    String iliName,
    String modelName,
    String topicName,
    String domainClassName,
    String controllerName,
    String className,
    String label,
    DomainKind kind,
    boolean navigationVisible,
    DisplayDescriptor display,
    Map<String, FieldDescriptor> fields,
    Map<String, RelationshipDescriptor> relationships,
    Map<String, InverseRelationshipDescriptor> inverseRelationships,
    Map<String, GeometryDescriptor> geometries
) {

    public DomainDescriptor {
        iliName = DescriptorValidation.requireText(iliName, "iliName");
        domainClassName = DescriptorValidation.requireText(domainClassName, "domainClassName");
        kind = kind == null ? DomainKind.CLASS : kind;
        display = display == null ? new DisplayDescriptor(null, List.of(), List.of()) : display;
        fields = DescriptorValidation.immutableLinkedCopy(fields, "fields");
        relationships = DescriptorValidation.immutableLinkedCopy(relationships, "relationships");
        inverseRelationships = DescriptorValidation.immutableLinkedCopy(
            inverseRelationships, "inverseRelationships");
        geometries = DescriptorValidation.immutableLinkedCopy(geometries, "geometries");
    }

    public Optional<FieldDescriptor> field(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(fields.get(name));
    }

    public Optional<RelationshipDescriptor> relationship(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(relationships.get(name));
    }
}

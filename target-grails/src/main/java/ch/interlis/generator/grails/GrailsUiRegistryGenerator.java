package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind;
import ch.interlis.generator.grails.runtime.api.descriptor.FieldDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.FieldKind;
import ch.interlis.generator.grails.runtime.api.descriptor.GeometryDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipMode;
import ch.interlis.generator.grails.runtime.api.descriptor.RelationshipDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeCoreType;
import ch.interlis.generator.grails.source.GroovySourceWriter;
import ch.interlis.generator.grails.project.plan.PlannedProjectFile;
import ch.interlis.generator.model.ModelMetadata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates the deterministic typed UI registry consumed by the runtime
 * plugin.
 *
 * <p>The registry implements {@link
 * ch.interlis.generator.grails.runtime.api.registry.DomainRegistry} and holds
 * immutable {@link DomainDescriptor} instances.</p>
 */
public final class GrailsUiRegistryGenerator {

    static final String GENERATED_PACKAGE = "ch.interlis.generator.grails.generated";
    static final String REGISTRY_CLASS_NAME = "InterlisUiRegistry";

    private static final String RELATIVE_PATH =
        "src/main/groovy/ch/interlis/generator/grails/generated/" + REGISTRY_CLASS_NAME + ".groovy";

    private final GroovySourceWriter source = new GroovySourceWriter();

    /**
     * Reine Planungsfunktion (Spezifikation §41.4): kein Write.
     */
    public PlannedProjectFile plan(RuntimeDescriptorPlan plan,
                                   GenerationConfig config,
                                   TargetNameRegistry registry) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(registry, "registry");
        return PlannedProjectFile.text(
            Path.of(RELATIVE_PATH),
            ch.interlis.generator.grails.project.GrailsProjectFileOwner.GENERATOR_MANAGED,
            renderRegistry(plan),
            "generated typed UI registry");
    }

    String renderRegistry(RuntimeDescriptorPlan plan) {
        List<DomainDescriptor> domains = plan.domains().stream()
            .sorted(java.util.Comparator.comparing(
                DomainDescriptor::iliName, java.util.Comparator.nullsLast(String::compareTo)))
            .toList();

        StringBuilder source = new StringBuilder();
        source.append("package ").append(GENERATED_PACKAGE).append("\n\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.DisplayDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.FieldDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.FieldKind\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.GeometryDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipMode\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.RelationshipDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeCoreType\n");
        source.append("import ch.interlis.generator.grails.runtime.api.registry.DomainRegistry\n\n");

        source.append("final class ").append(REGISTRY_CLASS_NAME)
            .append(" implements DomainRegistry {\n\n");

        source.append("    static final List<DomainDescriptor> DOMAINS = ");
        if (domains.isEmpty()) {
            source.append("[].asImmutable()\n\n");
        } else {
            source.append("[\n");
            for (int index = 0; index < domains.size(); index++) {
                source.append(renderDomain(domains.get(index), 2));
                source.append(index < domains.size() - 1 ? ",\n" : "\n");
            }
            source.append("    ].asImmutable()\n\n");
        }

        source.append("    static final ").append(REGISTRY_CLASS_NAME)
            .append(" INSTANCE = new ").append(REGISTRY_CLASS_NAME).append("(DOMAINS)\n\n");

        source.append("    private final Map<String, DomainDescriptor> byIliName\n");
        source.append("    private final Map<String, DomainDescriptor> byClassName\n");
        source.append("    private final Map<String, List<DomainDescriptor>> byModelName\n\n");

        source.append("    private ").append(REGISTRY_CLASS_NAME)
            .append("(List<DomainDescriptor> domains) {\n");
        source.append("        Map<String, DomainDescriptor> iliNames = new LinkedHashMap<>()\n");
        source.append("        Map<String, DomainDescriptor> classNames = new LinkedHashMap<>()\n");
        source.append("        Map<String, List<DomainDescriptor>> modelNames = new LinkedHashMap<>()\n");
        source.append("        domains.each { DomainDescriptor domain ->\n");
        source.append("            iliNames.put(domain.iliName(), domain)\n");
        source.append("            if (domain.domainClassName() != null) {\n");
        source.append("                classNames.put(domain.domainClassName(), domain)\n");
        source.append("            }\n");
        source.append("            String model = domain.modelName() ?: ''\n");
        source.append("            modelNames.put(model, (modelNames[model] ?: []) + domain)\n");
        source.append("        }\n");
        source.append("        byIliName = Collections.unmodifiableMap(iliNames)\n");
        source.append("        byClassName = Collections.unmodifiableMap(classNames)\n");
        source.append("        byModelName = Collections.unmodifiableMap(modelNames)\n");
        source.append("    }\n\n");

        source.append("    @Override\n");
        source.append("    Collection<DomainDescriptor> domains() { DOMAINS }\n\n");
        source.append("    @Override\n");
        source.append("    Optional<DomainDescriptor> byIliName(String name) {\n");
        source.append("        return Optional.ofNullable(byIliName[name])\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    Optional<DomainDescriptor> byDomainClassName(String qualifiedClassName) {\n");
        source.append("        return Optional.ofNullable(byClassName[qualifiedClassName])\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    List<DomainDescriptor> byModel(String modelName) {\n");
        source.append("        return byModelName[modelName] ?: []\n");
        source.append("    }\n\n");

        source.append("}\n");
        return source.toString();
    }

    private String renderDomain(DomainDescriptor domain, int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        StringBuilder builder = new StringBuilder();
        builder.append(indent).append("new DomainDescriptor(\n");
        builder.append(indent).append("    ").append(source.stringLiteral(domain.iliName())).append(",\n");
        builder.append(indent).append("    ").append(source.stringLiteral(domain.modelName())).append(",\n");
        builder.append(indent).append("    ").append(source.stringLiteral(domain.topicName())).append(",\n");
        builder.append(indent).append("    ").append(source.stringLiteral(domain.domainClassName())).append(",\n");
        builder.append(indent).append("    ").append(source.stringLiteral(domain.controllerName())).append(",\n");
        builder.append(indent).append("    ").append(source.stringLiteral(domain.className())).append(",\n");
        builder.append(indent).append("    ").append(source.stringLiteral(domain.label())).append(",\n");
        builder.append(indent).append("    ").append(source.enumLiteral(DomainKind.class, domain.kind())).append(",\n");
        builder.append(indent).append("    ").append(domain.navigationVisible()).append(",\n");
        builder.append(indent).append("    ").append(renderDisplay(domain.display(), indentLevel + 1)).append(",\n");
        builder.append(indent).append("    ").append(renderFieldMap(domain.fields(), indentLevel + 1)).append(",\n");
        builder.append(indent).append("    ").append(renderRelationshipMap(domain.relationships(), indentLevel + 1)).append(",\n");
        builder.append(indent).append("    ").append(renderInverseMap(domain.inverseRelationships(), indentLevel + 1)).append(",\n");
        builder.append(indent).append("    ").append(renderGeometryMap(domain.geometries(), indentLevel + 1)).append("\n");
        builder.append(indent).append(")");
        return builder.toString();
    }

    private String renderDisplay(ch.interlis.generator.grails.runtime.api.descriptor.DisplayDescriptor display,
                                 int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        return "new DisplayDescriptor(\n"
            + indent + "    " + source.stringLiteral(display.label()) + ",\n"
            + indent + "    " + source.listOfStrings(display.displayFields()) + ",\n"
            + indent + "    " + source.listOfStrings(display.searchFields()) + "\n"
            + indent + ")";
    }

    private String renderFieldMap(Map<String, FieldDescriptor> fields, int indentLevel) {
        if (fields.isEmpty()) {
            return "[:]";
        }
        String indent = "    ".repeat(indentLevel);
        StringBuilder builder = new StringBuilder("[\n");
        List<Map.Entry<String, FieldDescriptor>> entries = new java.util.ArrayList<>(fields.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, FieldDescriptor> entry = entries.get(index);
            builder.append(indent).append("    ").append(source.stringLiteral(entry.getKey()))
                .append(": ").append(renderField(entry.getValue(), indentLevel + 1));
            builder.append(index < entries.size() - 1 ? ",\n" : "\n");
        }
        builder.append(indent).append("]");
        return builder.toString();
    }

    private String renderField(FieldDescriptor field, int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        return "new FieldDescriptor(\n"
            + indent + "    " + source.stringLiteral(field.name()) + ",\n"
            + indent + "    " + source.stringLiteral(field.iliName()) + ",\n"
            + indent + "    " + source.stringLiteral(field.javaType()) + ",\n"
            + indent + "    " + source.enumLiteral(RuntimeCoreType.class, field.coreType()) + ",\n"
            + indent + "    " + source.enumLiteral(FieldKind.class, field.kind()) + ",\n"
            + indent + "    " + source.stringLiteral(field.label()) + ",\n"
            + indent + "    " + field.mandatory() + ",\n"
            + indent + "    " + source.nullableInteger(field.maxLength()) + ",\n"
            + indent + "    " + source.stringLiteral(field.minValue()) + ",\n"
            + indent + "    " + source.stringLiteral(field.maxValue()) + ",\n"
            + indent + "    " + source.nullableInteger(field.precision()) + ",\n"
            + indent + "    " + source.nullableInteger(field.scale()) + ",\n"
            + indent + "    " + source.stringLiteral(field.unit()) + ",\n"
            + indent + "    " + source.stringLiteral(field.enumType()) + "\n"
            + indent + ")";
    }

    private String renderRelationshipMap(Map<String, RelationshipDescriptor> relationships,
                                         int indentLevel) {
        if (relationships.isEmpty()) {
            return "[:]";
        }
        String indent = "    ".repeat(indentLevel);
        StringBuilder builder = new StringBuilder("[\n");
        List<Map.Entry<String, RelationshipDescriptor>> entries =
            new java.util.ArrayList<>(relationships.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, RelationshipDescriptor> entry = entries.get(index);
            builder.append(indent).append("    ").append(source.stringLiteral(entry.getKey()))
                .append(": ").append(renderRelationship(entry.getValue(), indentLevel + 1));
            builder.append(index < entries.size() - 1 ? ",\n" : "\n");
        }
        builder.append(indent).append("]");
        return builder.toString();
    }

    private String renderRelationship(RelationshipDescriptor relationship, int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        return "new RelationshipDescriptor(\n"
            + indent + "    " + source.stringLiteral(relationship.name()) + ",\n"
            + indent + "    " + source.stringLiteral(relationship.propertyName()) + ",\n"
            + indent + "    " + source.stringLiteral(relationship.targetDomainClassName()) + ",\n"
            + indent + "    " + source.stringLiteral(relationship.semanticKind()) + ",\n"
            + indent + "    " + source.stringLiteral(relationship.label()) + ",\n"
            + indent + "    " + source.stringLiteral(relationship.sourceAttribute()) + ",\n"
            + indent + "    " + source.stringLiteral(relationship.targetRoleName()) + ",\n"
            + indent + "    " + relationship.mandatory() + "\n"
            + indent + ")";
    }

    private String renderInverseMap(Map<String, InverseRelationshipDescriptor> inverses,
                                    int indentLevel) {
        if (inverses.isEmpty()) {
            return "[:]";
        }
        String indent = "    ".repeat(indentLevel);
        StringBuilder builder = new StringBuilder("[\n");
        List<Map.Entry<String, InverseRelationshipDescriptor>> entries =
            new java.util.ArrayList<>(inverses.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, InverseRelationshipDescriptor> entry = entries.get(index);
            builder.append(indent).append("    ").append(source.stringLiteral(entry.getKey()))
                .append(": ").append(renderInverse(entry.getValue(), indentLevel + 1));
            builder.append(index < entries.size() - 1 ? ",\n" : "\n");
        }
        builder.append(indent).append("]");
        return builder.toString();
    }

    private String renderInverse(InverseRelationshipDescriptor inverse, int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        return "new InverseRelationshipDescriptor(\n"
            + indent + "    " + source.stringLiteral(inverse.name()) + ",\n"
            + indent + "    " + source.stringLiteral(inverse.label()) + ",\n"
            + indent + "    " + source.stringLiteral(inverse.ownerIliClassName()) + ",\n"
            + indent + "    " + source.stringLiteral(inverse.relatedIliClassName()) + ",\n"
            + indent + "    " + source.stringLiteral(inverse.relatedDomainClassName()) + ",\n"
            + indent + "    " + source.stringLiteral(inverse.relatedControllerName()) + ",\n"
            + indent + "    " + source.stringLiteral(inverse.relatedPropertyName()) + ",\n"
            + indent + "    " + source.stringLiteral(inverse.relatedLabel()) + ",\n"
            + indent + "    " + inverse.mandatory() + ",\n"
            + indent + "    " + inverse.generatedWritable() + ",\n"
            + indent + "    " + inverse.visible() + ",\n"
            + indent + "    " + source.enumLiteral(InverseRelationshipMode.class, inverse.mode()) + "\n"
            + indent + ")";
    }

    private String renderGeometryMap(Map<String, GeometryDescriptor> geometries, int indentLevel) {
        if (geometries.isEmpty()) {
            return "[:]";
        }
        String indent = "    ".repeat(indentLevel);
        StringBuilder builder = new StringBuilder("[\n");
        List<Map.Entry<String, GeometryDescriptor>> entries =
            new java.util.ArrayList<>(geometries.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, GeometryDescriptor> entry = entries.get(index);
            builder.append(indent).append("    ").append(source.stringLiteral(entry.getKey()))
                .append(": ").append(renderGeometry(entry.getValue(), indentLevel + 1));
            builder.append(index < entries.size() - 1 ? ",\n" : "\n");
        }
        builder.append(indent).append("]");
        return builder.toString();
    }

    private String renderGeometry(GeometryDescriptor geometry, int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        return "new GeometryDescriptor(\n"
            + indent + "    " + source.stringLiteral(geometry.fieldName()) + ",\n"
            + indent + "    " + source.nullableInteger(geometry.srid()) + ",\n"
            + indent + "    " + source.stringLiteral(geometry.kind()) + ",\n"
            + indent + "    " + source.nullableBoolean(geometry.hasZ()) + ",\n"
            + indent + "    " + source.nullableBoolean(geometry.hasM()) + ",\n"
            + indent + "    " + source.nullableBoolean(geometry.allowEmpty()) + "\n"
            + indent + ")";
    }
}

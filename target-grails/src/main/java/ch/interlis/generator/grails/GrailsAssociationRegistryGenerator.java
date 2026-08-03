package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationAttributeDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationCreateMode;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind;
import ch.interlis.generator.grails.project.GrailsProjectFileOwner;
import ch.interlis.generator.grails.project.plan.PlannedProjectFile;
import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeCoreType;
import ch.interlis.generator.grails.source.GroovySourceWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Generates the deterministic typed Groovy association registry for the
 * generated Grails application.
 *
 * <p>The registry implements {@link
 * ch.interlis.generator.grails.runtime.api.registry.AssociationRegistry} and
 * holds immutable descriptor instances.</p>
 */
public final class GrailsAssociationRegistryGenerator {

    static final String GENERATED_PACKAGE = "ch.interlis.generator.grails.generated";
    static final String REGISTRY_CLASS_NAME = "InterlisAssociationRegistry";

    private static final String RELATIVE_PATH =
        "src/main/groovy/ch/interlis/generator/grails/generated/" + REGISTRY_CLASS_NAME + ".groovy";

    private final GroovySourceWriter source = new GroovySourceWriter();

    /**
     * Reine Planungsfunktion (Spezifikation §41.3): kein Write.
     */
    public PlannedProjectFile plan(RuntimeDescriptorPlan plan, GenerationConfig config) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(config, "config");
        return PlannedProjectFile.text(
            Path.of(RELATIVE_PATH),
            ch.interlis.generator.grails.project.GrailsProjectFileOwner.GENERATOR_MANAGED,
            renderRegistry(plan),
            "generated typed association registry");
    }

    String renderRegistry(RuntimeDescriptorPlan plan) {
        List<AssociationDescriptor> associations = new ArrayList<>(plan.associations());
        associations.sort(Comparator.comparing(
            AssociationDescriptor::associationName, Comparator.nullsLast(String::compareTo)));
        List<AssociationContextDescriptor> contexts = new ArrayList<>(plan.contexts());
        contexts.sort(Comparator.comparing(
            AssociationContextDescriptor::id, Comparator.nullsLast(String::compareTo)));

        Map<String, List<String>> contextIdsByParticipant = new TreeMap<>();
        for (AssociationContextDescriptor context : contexts) {
            String participant = context.participantDomainClassName();
            if (participant != null && !participant.isBlank()) {
                contextIdsByParticipant
                    .computeIfAbsent(participant, key -> new ArrayList<>())
                    .add(context.id());
            }
        }
        contextIdsByParticipant.values().forEach(java.util.Collections::sort);

        Map<String, AssociationDescriptor> associationsByName = new LinkedHashMap<>();
        for (AssociationDescriptor association : associations) {
            associationsByName.put(association.associationName(), association);
        }
        Map<String, AssociationContextDescriptor> contextsById = new LinkedHashMap<>();
        for (AssociationContextDescriptor context : contexts) {
            contextsById.put(context.id(), context);
        }
        StringBuilder source = new StringBuilder();
        source.append("package ").append(GENERATED_PACKAGE).append("\n\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.AssociationAttributeDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.AssociationCreateMode\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind\n");
        source.append("import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeCoreType\n");
        source.append("import ch.interlis.generator.grails.runtime.api.registry.AssociationRegistry\n\n");

        source.append("final class ").append(REGISTRY_CLASS_NAME)
            .append(" implements AssociationRegistry {\n\n");

        source.append("    static final Map<String, AssociationDescriptor> ASSOCIATIONS = ");
        source.append(renderDescriptorMap(associationsByName, a -> renderAssociation((AssociationDescriptor) a, 2), 1)).append("\n\n");
        source.append("    static final Map<String, AssociationContextDescriptor> CONTEXTS = ");
        source.append(renderDescriptorMap(contextsById, c -> renderContext((AssociationContextDescriptor) c, 2), 1)).append("\n\n");
        source.append("    static final Map<String, List<String>> CONTEXT_IDS_BY_PARTICIPANT = ");
        source.append(renderStringListMap(contextIdsByParticipant, 1)).append("\n\n");
        source.append("    static final ").append(REGISTRY_CLASS_NAME)
            .append(" INSTANCE = new ").append(REGISTRY_CLASS_NAME)
            .append("(ASSOCIATIONS, CONTEXTS)\n\n");

        source.append("    private final Map<String, AssociationDescriptor> associationsByName\n");
        source.append("    private final Map<String, AssociationContextDescriptor> contextsById\n\n");

        source.append("    private ").append(REGISTRY_CLASS_NAME)
            .append("(Map<String, AssociationDescriptor> associations, Map<String, AssociationContextDescriptor> contexts) {\n");
        source.append("        associationsByName = Collections.unmodifiableMap(new LinkedHashMap<>(associations))\n");
        source.append("        contextsById = Collections.unmodifiableMap(new LinkedHashMap<>(contexts))\n");
        source.append("    }\n\n");

        source.append("    @Override\n");
        source.append("    Collection<AssociationDescriptor> associations() { ASSOCIATIONS.values() }\n\n");
        source.append("    @Override\n");
        source.append("    Optional<AssociationDescriptor> association(String name) {\n");
        source.append("        return Optional.ofNullable(associationsByName[name])\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    Collection<AssociationContextDescriptor> contexts() { CONTEXTS.values() }\n\n");
        source.append("    @Override\n");
        source.append("    Optional<AssociationContextDescriptor> context(String id) {\n");
        source.append("        return Optional.ofNullable(contextsById[id])\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    List<AssociationContextDescriptor> contextsForParticipant(String domainClassName) {\n");
        source.append("        return (CONTEXT_IDS_BY_PARTICIPANT[domainClassName] ?: [])\n");
        source.append("            .collect { String id -> contextsById[id] }\n");
        source.append("            .findAll { it != null }\n");
        source.append("    }\n\n");

        source.append("}\n");
        return source.toString();
    }

    private String renderDescriptorMap(Map<String, ? extends Object> values,
                                       java.util.function.Function<Object, String> renderer,
                                       int indentLevel) {
        if (values.isEmpty()) {
            return "[:]";
        }
        String indent = "    ".repeat(indentLevel);
        StringBuilder builder = new StringBuilder("[\n");
        List<Map.Entry<String, ? extends Object>> entries = new ArrayList<>(values.entrySet());
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, ? extends Object> entry = entries.get(index);
            builder.append(indent).append("    ").append(source.stringLiteral(entry.getKey()))
                .append(": ").append(renderer.apply(entry.getValue()));
            builder.append(index < entries.size() - 1 ? ",\n" : "\n");
        }
        builder.append(indent).append("]");
        return builder.toString();
    }

    private String renderStringListMap(Map<String, List<String>> values, int indentLevel) {
        if (values.isEmpty()) {
            return "[:]";
        }
        String indent = "    ".repeat(indentLevel);
        StringBuilder builder = new StringBuilder("[\n");
        List<Map.Entry<String, List<String>>> entries = new ArrayList<>(values.entrySet());
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, List<String>> entry = entries.get(index);
            builder.append(indent).append("    ").append(source.stringLiteral(entry.getKey()))
                .append(": ").append(source.listOfStrings(entry.getValue()));
            builder.append(index < entries.size() - 1 ? ",\n" : "\n");
        }
        builder.append(indent).append("]");
        return builder.toString();
    }

    private String renderAssociation(AssociationDescriptor association, int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        return "new AssociationDescriptor(\n"
            + indent + "    " + source.stringLiteral(association.associationName()) + ",\n"
            + indent + "    " + source.stringLiteral(association.iliClassName()) + ",\n"
            + indent + "    " + source.stringLiteral(association.domainClassName()) + ",\n"
            + indent + "    " + source.stringLiteral(association.controllerName()) + ",\n"
            + indent + "    " + source.stringLiteral(association.viewPath()) + ",\n"
            + indent + "    " + source.stringLiteral(association.physicalTable()) + ",\n"
            + indent + "    " + source.stringLiteral(association.physicalSqlName()) + ",\n"
            + indent + "    " + source.enumLiteral(AssociationStorageKind.class, association.storageKind()) + ",\n"
            + indent + "    " + association.writable() + ",\n"
            + indent + "    " + association.showInNavigation() + ",\n"
            + indent + "    " + renderRoleList(association.roles(), indentLevel + 1) + ",\n"
            + indent + "    " + renderAttributeList(association.attributes(), indentLevel + 1) + ",\n"
            + indent + "    " + source.listOfStrings(association.diagnostics()) + "\n"
            + indent + ")";
    }

    private String renderRoleList(List<AssociationRoleDescriptor> roles, int indentLevel) {
        if (roles.isEmpty()) {
            return "[]";
        }
        String indent = "    ".repeat(indentLevel);
        StringBuilder builder = new StringBuilder("[\n");
        for (int index = 0; index < roles.size(); index++) {
            builder.append(indent).append("    ").append(renderRole(roles.get(index), indentLevel + 1));
            builder.append(index < roles.size() - 1 ? ",\n" : "\n");
        }
        builder.append(indent).append("]");
        return builder.toString();
    }

    private String renderRole(AssociationRoleDescriptor role, int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        return "new AssociationRoleDescriptor(\n"
            + indent + "    " + source.stringLiteral(role.name()) + ",\n"
            + indent + "    " + source.stringLiteral(role.label()) + ",\n"
            + indent + "    " + source.stringLiteral(role.propertyName()) + ",\n"
            + indent + "    " + source.stringLiteral(role.targetIliClassName()) + ",\n"
            + indent + "    " + source.stringLiteral(role.targetDomainClassName()) + ",\n"
            + indent + "    " + role.minCardinality() + ",\n"
            + indent + "    " + role.maxCardinality() + ",\n"
            + indent + "    " + role.mandatory() + ",\n"
            + indent + "    " + role.ordered() + ",\n"
            + indent + "    " + role.external() + ",\n"
            + indent + "    " + role.composition() + "\n"
            + indent + ")";
    }

    private String renderAttributeList(List<AssociationAttributeDescriptor> attributes,
                                       int indentLevel) {
        if (attributes.isEmpty()) {
            return "[]";
        }
        String indent = "    ".repeat(indentLevel);
        StringBuilder builder = new StringBuilder("[\n");
        for (int index = 0; index < attributes.size(); index++) {
            builder.append(indent).append("    ").append(renderAttribute(attributes.get(index), indentLevel + 1));
            builder.append(index < attributes.size() - 1 ? ",\n" : "\n");
        }
        builder.append(indent).append("]");
        return builder.toString();
    }

    private String renderAttribute(AssociationAttributeDescriptor attribute, int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        return "new AssociationAttributeDescriptor(\n"
            + indent + "    " + source.stringLiteral(attribute.iliName()) + ",\n"
            + indent + "    " + source.stringLiteral(attribute.propertyName()) + ",\n"
            + indent + "    " + source.stringLiteral(attribute.javaType()) + ",\n"
            + indent + "    " + source.enumLiteral(RuntimeCoreType.class, attribute.coreType()) + ",\n"
            + indent + "    " + source.stringLiteral(attribute.label()) + ",\n"
            + indent + "    " + attribute.mandatory() + ",\n"
            + indent + "    " + source.nullableInteger(attribute.maxLength()) + ",\n"
            + indent + "    " + source.stringLiteral(attribute.unit()) + ",\n"
            + indent + "    " + source.stringLiteral(attribute.enumType()) + ",\n"
            + indent + "    " + attribute.geometry() + "\n"
            + indent + ")";
    }

    private String renderContext(AssociationContextDescriptor context, int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        return "new AssociationContextDescriptor(\n"
            + indent + "    " + source.stringLiteral(context.id()) + ",\n"
            + indent + "    " + source.stringLiteral(context.associationName()) + ",\n"
            + indent + "    " + source.stringLiteral(context.participantDomainClassName()) + ",\n"
            + indent + "    " + source.stringLiteral(context.fixedRoleName()) + ",\n"
            + indent + "    " + source.stringLiteral(context.fixedPropertyName()) + ",\n"
            + indent + "    " + source.listOfStrings(context.editableRoleNames()) + ",\n"
            + indent + "    " + source.listOfStrings(context.editablePropertyNames()) + ",\n"
            + indent + "    " + source.stringLiteral(context.defaultLabel()) + ",\n"
            + indent + "    " + source.stringLiteral(context.messageCode()) + ",\n"
            + indent + "    " + source.stringLiteral(context.presentation()) + ",\n"
            + indent + "    " + source.enumLiteral(AssociationCreateMode.class, context.createMode()) + ",\n"
            + indent + "    " + context.writable() + ",\n"
            + indent + "    " + context.removable() + ",\n"
            + indent + "    " + context.showAssociationObjectLink() + ",\n"
            + indent + "    " + context.perspectiveMin() + ",\n"
            + indent + "    " + context.perspectiveMax() + ",\n"
            + indent + "    " + source.listOfStrings(context.diagnostics()) + "\n"
            + indent + ")";
    }

}

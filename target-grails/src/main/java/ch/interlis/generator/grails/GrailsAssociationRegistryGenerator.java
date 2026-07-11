package ch.interlis.generator.grails;

import ch.interlis.generator.model.ModelMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Generates the deterministic Groovy association registry for the generated
 * Grails application.
 *
 * <p>The registry is emitted into the fixed package
 * {@code ch.interlis.generator.grails.generated} so that the copied runtime can
 * reference it without dynamic imports. Output ordering is stable, values are
 * escaped for Groovy, {@code null} is emitted as {@code null} and unbounded
 * cardinalities keep their {@code -1} sentinel.
 */
public final class GrailsAssociationRegistryGenerator {

    static final String GENERATED_PACKAGE = "ch.interlis.generator.grails.generated";
    static final String REGISTRY_CLASS_NAME = "InterlisAssociationRegistry";

    private static final String RELATIVE_PATH =
        "src/main/groovy/ch/interlis/generator/grails/generated/" + REGISTRY_CLASS_NAME + ".groovy";

    public void generate(ModelMetadata metadata,
                         GenerationConfig config,
                         TargetNameRegistry registry,
                         GrailsAssociationPlanner planner) throws IOException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(planner, "planner");

        Path target = targetPath(config);
        Files.createDirectories(target.getParent());
        Files.writeString(target, renderRegistry(planner.plans(), config), StandardCharsets.UTF_8);
    }

    Path targetPath(GenerationConfig config) {
        return config.getOutputDir().resolve(RELATIVE_PATH);
    }

    String renderRegistry(List<GrailsAssociationPlan> plans, GenerationConfig config) {
        List<GrailsAssociationPlan> sortedPlans = new ArrayList<>(plans == null ? List.of() : plans);
        sortedPlans.sort(Comparator.comparing(
            GrailsAssociationPlan::associationName, Comparator.nullsLast(String::compareTo)));

        Map<String, Map<String, Object>> associations = new LinkedHashMap<>();
        Map<String, Map<String, Object>> contexts = new TreeMap<>();
        Map<String, List<String>> contextIdsByParticipant = new TreeMap<>();
        Map<String, Map<String, Object>> entities = new TreeMap<>();

        for (GrailsAssociationPlan plan : sortedPlans) {
            associations.put(plan.associationName(), associationDescriptor(plan));

            for (GrailsAssociationContextPlan context : plan.contexts()) {
                contexts.put(context.contextId(), contextDescriptor(plan, context));
                String participant = context.participantDomainQualifiedName();
                if (participant != null) {
                    contextIdsByParticipant
                        .computeIfAbsent(participant, key -> new ArrayList<>())
                        .add(context.contextId());
                }
            }

            if (plan.associationDomainQualifiedName() != null) {
                Map<String, Object> entity = new LinkedHashMap<>();
                entity.put("iliName", plan.associationIliClassName());
                entity.put("kind", "ASSOCIATION");
                entity.put("showInNavigation", resolveShowInNavigation(plan, config));
                entities.put(plan.associationDomainQualifiedName(), entity);
            }
        }
        contextIdsByParticipant.values().forEach(java.util.Collections::sort);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(GENERATED_PACKAGE).append("\n\n");
        sb.append("final class ").append(REGISTRY_CLASS_NAME).append(" {\n\n");

        sb.append("    static final Map<String, Map<String, Object>> ASSOCIATIONS = ")
            .append(renderMapOfMaps(associations, 1)).append("\n\n");
        sb.append("    static final Map<String, Map<String, Object>> CONTEXTS = ")
            .append(renderMapOfMaps(contexts, 1)).append("\n\n");
        sb.append("    static final Map<String, List<String>> CONTEXT_IDS_BY_PARTICIPANT = ")
            .append(renderMapOfStringLists(contextIdsByParticipant, 1)).append("\n\n");
        sb.append("    static final Map<String, Map<String, Object>> ENTITIES = ")
            .append(renderMapOfMaps(entities, 1)).append("\n\n");

        sb.append("    static Map<String, Object> association(String associationName) {\n");
        sb.append("        return ASSOCIATIONS[associationName]\n");
        sb.append("    }\n\n");

        sb.append("    static Map<String, Object> context(String contextId) {\n");
        sb.append("        return CONTEXTS[contextId]\n");
        sb.append("    }\n\n");

        sb.append("    static List<Map<String, Object>> contextsForParticipant(String domainClassName) {\n");
        sb.append("        return (CONTEXT_IDS_BY_PARTICIPANT[domainClassName] ?: [])\n");
        sb.append("            .collect { String id -> CONTEXTS[id] }\n");
        sb.append("            .findAll { it != null }\n");
        sb.append("    }\n\n");

        sb.append("    static boolean showInNavigation(String domainClassName) {\n");
        sb.append("        Map entity = ENTITIES[domainClassName]\n");
        sb.append("        return entity == null || entity.showInNavigation != false\n");
        sb.append("    }\n\n");

        sb.append("    private ").append(REGISTRY_CLASS_NAME).append("() {\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private boolean resolveShowInNavigation(GrailsAssociationPlan plan, GenerationConfig config) {
        String navigation = config.getAssociationNavigation();
        if (GenerationConfig.ASSOCIATION_NAVIGATION_SHOW.equals(navigation)) {
            return true;
        }
        if (GenerationConfig.ASSOCIATION_NAVIGATION_HIDE.equals(navigation)) {
            return false;
        }
        if (!config.isHideContextualAssociationControllers()) {
            return true;
        }
        return plan.showInNavigation();
    }

    private Map<String, Object> associationDescriptor(GrailsAssociationPlan plan) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("associationName", plan.associationName());
        descriptor.put("iliClassName", plan.associationIliClassName());
        descriptor.put("domainClassName", plan.associationDomainClassName());
        descriptor.put("domainClassQualifiedName", plan.associationDomainQualifiedName());
        descriptor.put("controllerName", plan.associationControllerName());
        descriptor.put("viewPath", plan.associationViewPath());
        descriptor.put("physicalTable", plan.physicalTable());
        descriptor.put("physicalSqlName", plan.physicalSqlName());
        descriptor.put("storageKind", plan.storageKind() != null ? plan.storageKind().name() : null);
        descriptor.put("writable", plan.writable());
        descriptor.put("showInNavigation", plan.showInNavigation());

        List<Object> roles = new ArrayList<>();
        for (GrailsAssociationRolePlan role : plan.roles()) {
            roles.add(roleDescriptor(role));
        }
        descriptor.put("roles", roles);

        List<Object> attributes = new ArrayList<>();
        for (GrailsAssociationAttributePlan attribute : plan.attributes()) {
            attributes.add(attributeDescriptor(attribute));
        }
        descriptor.put("attributes", attributes);
        descriptor.put("diagnostics", new ArrayList<Object>(plan.diagnostics()));
        return descriptor;
    }

    private Map<String, Object> roleDescriptor(GrailsAssociationRolePlan role) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("name", role.roleName());
        descriptor.put("label", role.roleLabel());
        descriptor.put("property", role.domainPropertyName());
        descriptor.put("targetIliClass", role.targetIliClassName());
        descriptor.put("targetDomainClass", role.targetDomainQualifiedName());
        descriptor.put("min", role.minCardinality());
        descriptor.put("max", role.maxCardinality());
        descriptor.put("mandatory", role.mandatory());
        descriptor.put("ordered", role.ordered());
        descriptor.put("external", role.external());
        descriptor.put("composition", role.composition());
        return descriptor;
    }

    private Map<String, Object> attributeDescriptor(GrailsAssociationAttributePlan attribute) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("iliName", attribute.iliName());
        descriptor.put("property", attribute.domainPropertyName());
        descriptor.put("type", attribute.javaType());
        descriptor.put("coreType", attribute.coreType());
        descriptor.put("label", attribute.label());
        descriptor.put("mandatory", attribute.mandatory());
        descriptor.put("maxLength", attribute.maxLength());
        descriptor.put("unit", attribute.unit());
        descriptor.put("enumType", attribute.enumType());
        descriptor.put("geometry", attribute.geometry());
        return descriptor;
    }

    private Map<String, Object> contextDescriptor(GrailsAssociationPlan plan,
                                                  GrailsAssociationContextPlan context) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("id", context.contextId());
        descriptor.put("associationName", plan.associationName());
        descriptor.put("participantDomainClass", context.participantDomainQualifiedName());
        descriptor.put("fixedRole", context.fixedRoleName());
        descriptor.put("fixedProperty", context.fixedRolePropertyName());
        descriptor.put("editableRoles", new ArrayList<Object>(context.editableRoleNames()));
        descriptor.put("editableProperties", new ArrayList<Object>(context.editableRolePropertyNames()));
        descriptor.put("defaultLabel", context.defaultLabel());
        descriptor.put("messageCode", context.messageCode());
        descriptor.put("presentation",
            context.presentationKind() != null ? context.presentationKind().name() : null);
        descriptor.put("createMode",
            context.createMode() != null ? context.createMode().name() : null);
        descriptor.put("writable", context.writable());
        descriptor.put("removable", context.removable());
        descriptor.put("showAssociationObjectLink", context.showAssociationObjectLink());
        descriptor.put("perspectiveMin", context.perspectiveMinCardinality());
        descriptor.put("perspectiveMax", context.perspectiveMaxCardinality());
        descriptor.put("diagnostics", new ArrayList<Object>(context.diagnostics()));
        return descriptor;
    }

    // ------------------------------------------------------------------
    // Rendering primitives
    // ------------------------------------------------------------------

    private String renderMapOfMaps(Map<String, Map<String, Object>> map, int indentLevel) {
        if (map.isEmpty()) {
            return "[:]";
        }
        String indent = indent(indentLevel);
        String childIndent = indent(indentLevel + 1);
        StringBuilder sb = new StringBuilder("[\n");
        List<Map.Entry<String, Map<String, Object>>> entries = new ArrayList<>(map.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Map<String, Object>> entry = entries.get(i);
            sb.append(childIndent)
                .append(quote(entry.getKey()))
                .append(": ")
                .append(renderMap(entry.getValue(), indentLevel + 1));
            sb.append(i < entries.size() - 1 ? ",\n" : "\n");
        }
        sb.append(indent).append("]");
        return sb.toString();
    }

    private String renderMapOfStringLists(Map<String, List<String>> map, int indentLevel) {
        if (map.isEmpty()) {
            return "[:]";
        }
        String indent = indent(indentLevel);
        String childIndent = indent(indentLevel + 1);
        StringBuilder sb = new StringBuilder("[\n");
        List<Map.Entry<String, List<String>>> entries = new ArrayList<>(map.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, List<String>> entry = entries.get(i);
            sb.append(childIndent)
                .append(quote(entry.getKey()))
                .append(": ")
                .append(renderList(new ArrayList<>(entry.getValue()), indentLevel + 1));
            sb.append(i < entries.size() - 1 ? ",\n" : "\n");
        }
        sb.append(indent).append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String renderMap(Map<String, Object> map, int indentLevel) {
        if (map.isEmpty()) {
            return "[:]";
        }
        String indent = indent(indentLevel);
        String childIndent = indent(indentLevel + 1);
        StringBuilder sb = new StringBuilder("[\n");
        List<Map.Entry<String, Object>> entries = new ArrayList<>(map.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Object> entry = entries.get(i);
            sb.append(childIndent)
                .append(entry.getKey())
                .append(": ")
                .append(renderValue(entry.getValue(), indentLevel + 1));
            sb.append(i < entries.size() - 1 ? ",\n" : "\n");
        }
        sb.append(indent).append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String renderList(List<Object> list, int indentLevel) {
        if (list.isEmpty()) {
            return "[]";
        }
        String indent = indent(indentLevel);
        String childIndent = indent(indentLevel + 1);
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < list.size(); i++) {
            sb.append(childIndent).append(renderValue(list.get(i), indentLevel + 1));
            sb.append(i < list.size() - 1 ? ",\n" : "\n");
        }
        sb.append(indent).append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String renderValue(Object value, int indentLevel) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return quote(string);
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            return renderMap((Map<String, Object>) map, indentLevel);
        }
        if (value instanceof List<?> list) {
            return renderList((List<Object>) list, indentLevel);
        }
        return quote(value.toString());
    }

    private String quote(String value) {
        return "'" + escape(value) + "'";
    }

    private String escape(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '$' -> sb.append("\\$");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private String indent(int level) {
        return "    ".repeat(level);
    }
}

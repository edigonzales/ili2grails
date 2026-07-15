package ch.interlis.generator.grails;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates the small, deterministic UI registry used by the Bootstrap target.
 *
 * <p>The registry deliberately contains navigation metadata only. Association
 * and relationship semantics remain owned by the existing Grails planners and
 * registries.
 */
public final class GrailsUiRegistryGenerator {

    static final String GENERATED_PACKAGE = "ch.interlis.generator.grails.generated";
    static final String REGISTRY_CLASS_NAME = "InterlisUiRegistry";

    private static final String RELATIVE_PATH =
        "src/main/groovy/ch/interlis/generator/grails/generated/" + REGISTRY_CLASS_NAME + ".groovy";

    public void generate(ModelMetadata metadata,
                         GenerationConfig config,
                         TargetNameRegistry registry,
                         GrailsRelationshipMapper relationshipMapper,
                         GrailsAssociationPlanner associationPlanner) throws IOException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(relationshipMapper, "relationshipMapper");
        Objects.requireNonNull(associationPlanner, "associationPlanner");

        Path target = config.getOutputDir().resolve(RELATIVE_PATH);
        Files.createDirectories(target.getParent());
        Files.writeString(
            target,
            renderRegistry(metadata, config, registry, relationshipMapper, associationPlanner),
            StandardCharsets.UTF_8
        );
    }

    String renderRegistry(ModelMetadata metadata,
                          GenerationConfig config,
                          TargetNameRegistry registry,
                          GrailsRelationshipMapper relationshipMapper,
                          GrailsAssociationPlanner associationPlanner) {
        List<ClassMetadata> classes = new ArrayList<>(relationshipMapper.generatedClasses());
        classes.sort(Comparator.comparing(ClassMetadata::getName, Comparator.nullsLast(String::compareTo)));

        StringBuilder source = new StringBuilder();
        source.append("package ").append(GENERATED_PACKAGE).append("\n\n");
        source.append("final class ").append(REGISTRY_CLASS_NAME).append(" {\n\n");
        source.append("    static final List<Map<String, Object>> DOMAINS = [");
        if (classes.isEmpty()) {
            source.append("]\n\n");
        } else {
            source.append("\n");
            for (int index = 0; index < classes.size(); index++) {
                ClassMetadata classMetadata = classes.get(index);
                source.append(renderDomain(classMetadata, metadata, config, registry, associationPlanner, 2));
                source.append(index < classes.size() - 1 ? ",\n" : "\n");
            }
            source.append("    ]\n\n");
        }

        source.append("    static final Map<String, Map<String, Object>> BY_ILI_NAME = ")
            .append("DOMAINS.collectEntries { [(it.iliName): it] }\n\n");
        source.append("    static List<Map<String, Object>> domains() {\n")
            .append("        return DOMAINS\n")
            .append("    }\n\n");
        source.append("    static Map<String, Object> domain(String iliName) {\n")
            .append("        return iliName == null ? null : BY_ILI_NAME[iliName]\n")
            .append("    }\n\n");
        source.append("    static Map<String, Object> domainForClassName(String domainClassName) {\n")
            .append("        return domainClassName == null ? null : DOMAINS.find { it.domainClassName == domainClassName }\n")
            .append("    }\n\n");
        source.append("    static List<Map<String, Object>> domainsForModel(String modelName) {\n")
            .append("        return modelName == null ? [] : DOMAINS.findAll { it.modelName == modelName }\n")
            .append("    }\n\n");
        source.append("    private ").append(REGISTRY_CLASS_NAME).append("() {\n")
            .append("    }\n")
            .append("}\n");
        return source.toString();
    }

    private String renderDomain(ClassMetadata classMetadata,
                                ModelMetadata metadata,
                                GenerationConfig config,
                                TargetNameRegistry registry,
                                GrailsAssociationPlanner associationPlanner,
                                int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        String iliName = classMetadata.getName();
        String modelName = metadata.getModelName();
        boolean associationDomain = associationPlanner.isAssociationDomain(iliName);
        String topicName = topicPath(classMetadata.getTopicName(), modelName);

        StringBuilder source = new StringBuilder();
        source.append(indent).append("[\n")
            .append(indent).append("    domainClassName: ")
            .append(quote(registry.domainPackage() + "." + registry.className(classMetadata))).append(",\n")
            .append(indent).append("    controller: ").append(quote(registry.viewPath(classMetadata))).append(",\n")
            .append(indent).append("    iliName: ").append(quote(iliName)).append(",\n")
            .append(indent).append("    modelName: ").append(quote(modelName)).append(",\n")
            .append(indent).append("    topicName: ").append(quote(topicName)).append(",\n")
            .append(indent).append("    className: ").append(quote(registry.className(classMetadata))).append(",\n")
            .append(indent).append("    label: ").append(quote(label(classMetadata))).append(",\n")
            .append(indent).append("    navigationVisible: ")
            .append(associationPlanner.showDomainInNavigation(iliName)).append(",\n")
            .append(indent).append("    associationDomain: ").append(associationDomain).append("\n")
            .append(indent).append("]");
        return source.toString();
    }

    private String topicPath(String topicName, String modelName) {
        if (topicName == null || topicName.isBlank()) {
            return "";
        }
        if (modelName != null && !modelName.isBlank()
            && topicName.startsWith(modelName + ".")) {
            return topicName.substring(modelName.length() + 1);
        }
        return topicName;
    }

    private String label(ClassMetadata classMetadata) {
        Map<String, String> labels = classMetadata.getLabels();
        if (labels != null && !labels.isEmpty()) {
            for (String preferredLanguage : List.of("de-CH", "de", "en")) {
                String preferred = labels.get(preferredLanguage);
                if (preferred != null && !preferred.isBlank()) {
                    return preferred;
                }
            }
            return labels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsLast(String::compareTo)))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(classMetadata.getSimpleName());
        }
        return classMetadata.getSimpleName();
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder("'");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '\'' -> escaped.append("\\'");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '$' -> escaped.append("\\$");
                default -> escaped.append(character);
            }
        }
        return escaped.append('\'').toString();
    }
}

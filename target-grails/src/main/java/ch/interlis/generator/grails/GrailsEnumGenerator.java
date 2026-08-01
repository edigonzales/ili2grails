package ch.interlis.generator.grails;

import ch.interlis.generator.grails.project.GrailsProjectFileOwner;
import ch.interlis.generator.grails.project.plan.PlannedProjectFile;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;

import java.util.ArrayList;
import java.util.List;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Generiert Enum-Klassen aus INTERLIS Enumerationen.
 */
public class GrailsEnumGenerator {

    public void generate(ModelMetadata metadata, GenerationConfig config) throws IOException {
        generate(metadata, config, TargetNameRegistry.forMetadata(metadata, config));
    }

    /**
     * Reine Planungsfunktion (Spezifikation §41.2): kein Write.
     */
    public List<PlannedProjectFile> plan(ModelMetadata metadata,
                                         GenerationConfig config,
                                         TargetNameRegistry registry) {
        List<PlannedProjectFile> planned = new ArrayList<>();
        if (metadata.getAllEnums().isEmpty()) {
            return planned;
        }
        Path baseDir = Path.of("src/main/groovy")
            .resolve(NameUtils.packageToPath(config.getEnumPackage()));
        for (EnumMetadata enumMetadata : metadata.getAllEnums()) {
            String enumName = registry.enumName(enumMetadata);
            String content = renderEnum(enumMetadata, config.getEnumPackage(), enumName, registry);
            planned.add(PlannedProjectFile.text(
                baseDir.resolve(enumName + ".groovy"),
                ch.interlis.generator.grails.project.GrailsProjectFileOwner.GENERATOR_MANAGED,
                content,
                "generated enum " + enumName));
        }
        return planned;
    }

    public void generate(ModelMetadata metadata,
                         GenerationConfig config,
                         TargetNameRegistry registry) throws IOException {
        Path baseDir = config.getOutputDir()
            .resolve("src/main/groovy")
            .resolve(NameUtils.packageToPath(config.getEnumPackage()));
        for (PlannedProjectFile planned : plan(metadata, config, registry)) {
            Path target = baseDir.resolve(planned.relativePath().getFileName());
            Files.createDirectories(target.getParent());
            Files.write(target, planned.content());
        }
    }

    private String renderEnum(EnumMetadata enumMetadata,
                              String packageName,
                              String enumName,
                              TargetNameRegistry registry) {
        String values = enumMetadata.getValues().stream()
            .map(value -> registry.enumConstantName(enumMetadata, value))
            .collect(Collectors.joining(", "));

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append("\n\n");
        sb.append("enum ").append(enumName).append(" {\n");
        if (values.isEmpty()) {
            sb.append("}\n");
            return sb.toString();
        }
        sb.append("    ").append(values).append("\n");
        sb.append("}\n");
        return sb.toString();
    }
}

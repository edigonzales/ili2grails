package ch.interlis.generator.generator;

import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;

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

    public void generate(ModelMetadata metadata,
                         GenerationConfig config,
                         TargetNameRegistry registry) throws IOException {
        if (metadata.getAllEnums().isEmpty()) {
            return;
        }
        Path baseDir = config.getOutputDir()
            .resolve("src/main/groovy")
            .resolve(NameUtils.packageToPath(config.getEnumPackage()));
        Files.createDirectories(baseDir);

        for (EnumMetadata enumMetadata : metadata.getAllEnums()) {
            String enumName = registry.enumName(enumMetadata);
            String content = renderEnum(enumMetadata, config.getEnumPackage(), enumName, registry);
            Path target = baseDir.resolve(enumName + ".groovy");
            Files.writeString(target, content, StandardCharsets.UTF_8);
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

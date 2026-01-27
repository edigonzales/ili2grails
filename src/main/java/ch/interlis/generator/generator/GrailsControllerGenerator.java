package ch.interlis.generator.generator;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generiert Grails Controller für CRUD-Operationen.
 */
public class GrailsControllerGenerator {

    public void generate(ModelMetadata metadata, GenerationConfig config) throws IOException {
        Path baseDir = config.getOutputDir()
            .resolve("grails-app/controllers")
            .resolve(NameUtils.packageToPath(config.getControllerPackage()));
        Files.createDirectories(baseDir);

        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            if (classMetadata.isAbstract()) {
                continue;
            }
            String content = renderController(classMetadata, config);
            Path target = baseDir.resolve(classMetadata.getSimpleName() + "Controller.groovy");
            Files.writeString(target, content, StandardCharsets.UTF_8);
        }
    }

    private String renderController(ClassMetadata classMetadata, GenerationConfig config) {
        String className = classMetadata.getSimpleName();
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(config.getControllerPackage()).append("\n\n");
        if (!config.getDomainPackage().equals(config.getControllerPackage())) {
            sb.append("import ").append(config.getDomainPackage()).append(".")
                .append(className).append("\n\n");
        }
        sb.append("class ").append(className).append("Controller {\n");
        sb.append("    static scaffold = ").append(className).append("\n");
        sb.append("}\n");
        return sb.toString();
    }
}

package ch.interlis.generator.generator;

import ch.interlis.generator.model.ModelMetadata;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Orchestriert die Generierung von Grails Domains, Controllern und Views.
 */
public class GrailsCrudGenerator {

    private final GrailsDomainGenerator domainGenerator = new GrailsDomainGenerator();
    private final GrailsControllerGenerator controllerGenerator = new GrailsControllerGenerator();
    private final GrailsViewGenerator viewGenerator = new GrailsViewGenerator();
    private final GrailsEnumGenerator enumGenerator = new GrailsEnumGenerator();

    public void generate(ModelMetadata metadata, GenerationConfig config) throws IOException {
        Files.createDirectories(config.getOutputDir());
        enumGenerator.generate(metadata, config);
        domainGenerator.generate(metadata, config);
        controllerGenerator.generate(metadata, config);
        viewGenerator.generate(metadata, config);
    }
}

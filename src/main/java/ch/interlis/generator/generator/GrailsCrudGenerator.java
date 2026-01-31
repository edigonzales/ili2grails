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
    private final GrailsBuildGradleUpdater buildGradleUpdater = new GrailsBuildGradleUpdater();
    private final GrailsApplicationYamlUpdater applicationYamlUpdater = new GrailsApplicationYamlUpdater();

    public void generate(ModelMetadata metadata, GenerationConfig config) throws IOException {
        Files.createDirectories(config.getOutputDir());
        enumGenerator.generate(metadata, config);
        domainGenerator.generate(metadata, config);
        controllerGenerator.generate(metadata, config);
        viewGenerator.generate(metadata, config);
        buildGradleUpdater.ensureJtsDependency(config.getOutputDir().resolve("build.gradle"));
        applicationYamlUpdater.ensureDevelopmentDataSourceUrl(
            config.getOutputDir().resolve("grails-app/conf/application.yml"),
            config.getJdbcUrl()
        );
    }
}

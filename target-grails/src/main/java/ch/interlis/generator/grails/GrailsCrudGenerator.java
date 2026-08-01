package ch.interlis.generator.grails;

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
    private final GrailsAssociationRegistryGenerator associationRegistryGenerator =
        new GrailsAssociationRegistryGenerator();
    private final GrailsUiRegistryGenerator uiRegistryGenerator =
        new GrailsUiRegistryGenerator();
    private final GrailsBuildGradleUpdater buildGradleUpdater = new GrailsBuildGradleUpdater();
    private final GrailsApplicationYamlUpdater applicationYamlUpdater = new GrailsApplicationYamlUpdater();

    public void generate(ModelMetadata metadata, GenerationConfig config) throws IOException {
        Files.createDirectories(config.getOutputDir());
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper relationshipMapper =
            GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner associationPlanner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, relationshipMapper);
        GrailsInverseRelationshipPlanner inverseRelationshipPlanner =
            GrailsInverseRelationshipPlanner.forMetadata(metadata, config, registry, relationshipMapper);

        RuntimeDescriptorPlanner descriptorPlanner = new RuntimeDescriptorPlanner(
            registry, relationshipMapper, associationPlanner, inverseRelationshipPlanner);
        RuntimeDescriptorPlan descriptorPlan = descriptorPlanner.plan(metadata, config);

        enumGenerator.generate(metadata, config, registry);
        domainGenerator.generate(
            metadata,
            config,
            registry,
            relationshipMapper,
            inverseRelationshipPlanner
        );
        associationRegistryGenerator.generate(descriptorPlan, config);
        uiRegistryGenerator.generate(descriptorPlan, config, registry);
        //controllerGenerator.generate(metadata, config, registry);
        //viewGenerator.generate(metadata, config, registry);
        buildGradleUpdater.ensureDependencies(
            config.getOutputDir().resolve("build.gradle"),
            config.isGeometryEnabled()
        );
        applicationYamlUpdater.ensureDevelopmentDataSourceUrl(
            config.getOutputDir().resolve("grails-app/conf/application.yml"),
            config.getJdbcUrl(),
            config.getSchema(),
            config.isGeometryEnabled(),
            config.getDefaultSrid(),
            config.getLanguage()
        );
        ch.interlis.generator.grails.project.GrailsProjectCustomizer.defaultCustomizer()
            .customize(
                config.getOutputDir(),
                config,
                ch.interlis.generator.grails.project.RuntimeCoordinates.ili2grailsRuntime()
            );
    }
}

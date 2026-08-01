package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.GenerationConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates the post-generation customization of a Grails project:
 * legacy scan and safe migration, plugin dependency, scaffolding templates,
 * asset manifest and minimal application configuration.
 */
public final class GrailsProjectCustomizer {

    private final GrailsScaffoldingTemplateInstaller templateInstaller;
    private final GrailsRuntimeDependencyInstaller dependencyInstaller;
    private final GrailsAssetManifestUpdater assetUpdater;
    private final GrailsApplicationConfigurationUpdater configUpdater;
    private final LegacyRuntimeScanner legacyScanner;
    private final LegacyRuntimeMigrator legacyMigrator;

    public GrailsProjectCustomizer(
        GrailsScaffoldingTemplateInstaller templateInstaller,
        GrailsRuntimeDependencyInstaller dependencyInstaller,
        GrailsAssetManifestUpdater assetUpdater,
        GrailsApplicationConfigurationUpdater configUpdater,
        LegacyRuntimeScanner legacyScanner,
        LegacyRuntimeMigrator legacyMigrator
    ) {
        this.templateInstaller = Objects.requireNonNull(templateInstaller, "templateInstaller");
        this.dependencyInstaller = Objects.requireNonNull(dependencyInstaller, "dependencyInstaller");
        this.assetUpdater = Objects.requireNonNull(assetUpdater, "assetUpdater");
        this.configUpdater = Objects.requireNonNull(configUpdater, "configUpdater");
        this.legacyScanner = Objects.requireNonNull(legacyScanner, "legacyScanner");
        this.legacyMigrator = Objects.requireNonNull(legacyMigrator, "legacyMigrator");
    }

    public static GrailsProjectCustomizer defaultCustomizer() {
        return new GrailsProjectCustomizer(
            new GrailsScaffoldingTemplateInstaller(),
            new GrailsRuntimeDependencyInstaller(),
            new GrailsAssetManifestUpdater(),
            new GrailsApplicationConfigurationUpdater(),
            new LegacyRuntimeScanner(),
            new LegacyRuntimeMigrator()
        );
    }

    public ProjectCustomizationResult customize(Path grailsProjectDir,
                                                GenerationConfig config,
                                                RuntimeCoordinates runtimeCoordinates) throws IOException {
        Objects.requireNonNull(grailsProjectDir, "grailsProjectDir");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(runtimeCoordinates, "runtimeCoordinates");

        List<ProjectCustomizationDiagnostic> diagnostics = new ArrayList<>();
        if (!GenerationConfig.UI_THEME_BOOTSTRAP.equals(config.getUiTheme())) {
            return new ProjectCustomizationResult(diagnostics, List.of());
        }
        if (!Files.isDirectory(grailsProjectDir)) {
            throw new IOException("Grails project directory does not exist: " + grailsProjectDir);
        }

        // 1. Legacy scan
        LegacyRuntimeScanResult scanResult = legacyScanner.scan(grailsProjectDir);
        for (LegacyFileMatch modified : scanResult.modifiedFiles()) {
            diagnostics.add(new ProjectCustomizationDiagnostic(
                ProjectCustomizationDiagnostic.Level.ERROR,
                "LEGACY_RUNTIME_MODIFIED",
                "Modified legacy runtime file '" + modified.relativePath()
                    + "' requires manual migration; it was not deleted.",
                modified.relativePath().toString()));
        }

        // 2. Safe legacy migration (only unmodified known copies)
        LegacyMigrationResult migration = legacyMigrator.migrate(
            grailsProjectDir, scanResult, LegacyMigrationPolicy.STRICT);

        // 3. Plugin dependency
        dependencyInstaller.install(
            grailsProjectDir.resolve("build.gradle"), runtimeCoordinates);

        // 4. Generation-time templates
        templateInstaller.install(grailsProjectDir);

        // 5. Asset manifest
        assetUpdater.update(grailsProjectDir);

        // 6. Minimal application configuration
        configUpdater.update(grailsProjectDir, config);

        diagnostics.addAll(migration.diagnostics());
        return new ProjectCustomizationResult(diagnostics, migration.deletedFiles());
    }
}

package ch.interlis.generator.grails;

import ch.interlis.generator.grails.project.GrailsApplicationConfigurationUpdater;
import ch.interlis.generator.grails.project.GrailsAssetManifestUpdater;
import ch.interlis.generator.grails.project.GrailsScaffoldingTemplateInstaller;
import ch.interlis.generator.grails.project.LegacyMigrationPolicy;
import ch.interlis.generator.grails.project.LegacyRuntimeMigrator;
import ch.interlis.generator.grails.project.LegacyRuntimeScanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Legacy facade for the bootstrap overlay installation.
 *
 * @deprecated Use {@link ch.interlis.generator.grails.project.GrailsProjectCustomizer}
 *     which additionally installs the runtime plugin dependency and performs
 *     the safe legacy migration. This facade keeps the pre-P1 call sites
 *     working during the migration.
 */
@Deprecated(forRemoval = true)
public class GrailsTemplateOverlayInstaller {

    private static final List<String> APPLICATION_JS_REQUIRES = List.of(
        "//= require webjars/proj4/2.11.0/dist/proj4.js",
        "//= require webjars/ol/9.2.4/dist/ol.js",
        "//= require webjars/bootstrap/5.3.3/js/bootstrap.bundle.min.js",
        "//= require ili-geometry-editor.js",
        "//= require ili-form-ux.js",
        "//= require ili-notifications.js",
        "//= require ili-navigation.js"
    );
    private static final List<String> APPLICATION_CSS_REQUIRES = List.of(
        "*= require webjars/bootstrap/5.3.3/css/bootstrap.min.css",
        "*= require webjars/ol/9.2.4/ol.css"
    );
    private static final List<String> LEGACY_FILES = List.of(
        "grails-app/assets/javascripts/ili-carbon-wc-bundle.js",
        "grails-app/assets/javascripts/ili-carbon-input-bridge.js",
        "grails-app/assets/fonts/fira-sans/FiraSans-Bold.woff2"
    );

    private final GrailsScaffoldingTemplateInstaller templateInstaller =
        new GrailsScaffoldingTemplateInstaller();
    private final GrailsAssetManifestUpdater assetUpdater = new GrailsAssetManifestUpdater();
    private final GrailsApplicationConfigurationUpdater configUpdater =
        new GrailsApplicationConfigurationUpdater();
    private final LegacyRuntimeScanner legacyScanner = new LegacyRuntimeScanner();
    private final LegacyRuntimeMigrator legacyMigrator = new LegacyRuntimeMigrator();

    static List<String> managedFilesForTesting() {
        List<String> files = new java.util.ArrayList<>();
        ch.interlis.generator.grails.project.GrailsScaffoldingTemplateInstaller
            .templateFilesForTesting().stream()
            .map(file -> "src/main/templates/scaffolding/" + file)
            .forEach(files::add);
        files.add("grails-app/conf/spring/resources.groovy");
        return List.copyOf(files);
    }

    static List<String> applicationJsRequiresForTesting() {
        return APPLICATION_JS_REQUIRES;
    }

    static List<String> applicationCssRequiresForTesting() {
        return APPLICATION_CSS_REQUIRES;
    }

    public void install(Path grailsProjectDir, GenerationConfig config) throws IOException {
        Objects.requireNonNull(grailsProjectDir, "grailsProjectDir");
        Objects.requireNonNull(config, "config");
        if (!GenerationConfig.UI_THEME_BOOTSTRAP.equals(config.getUiTheme())) {
            return;
        }
        cleanupLegacyCarbonArtifacts(grailsProjectDir);
        legacyMigrator.migrate(grailsProjectDir, legacyScanner.scan(grailsProjectDir),
            LegacyMigrationPolicy.REPORT_ONLY);
        templateInstaller.install(grailsProjectDir);
        assetUpdater.update(grailsProjectDir);
        configUpdater.update(grailsProjectDir, config);
    }

    private void cleanupLegacyCarbonArtifacts(Path grailsProjectDir) throws IOException {
        for (String legacyFile : LEGACY_FILES) {
            java.nio.file.Files.deleteIfExists(grailsProjectDir.resolve(legacyFile));
        }
    }
}

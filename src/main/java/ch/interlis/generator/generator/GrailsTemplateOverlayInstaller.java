package ch.interlis.generator.generator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Installiert managed Template-Overlays in ein Grails-Projekt.
 */
public class GrailsTemplateOverlayInstaller {

    private static final String OVERLAY_ROOT = "grails/overlays/carbon-openlayers/";
    private static final List<String> MANAGED_FILES = List.of(
        "src/main/templates/scaffolding/Controller.groovy",
        "src/main/templates/scaffolding/create.gsp",
        "src/main/templates/scaffolding/edit.gsp",
        "src/main/templates/scaffolding/show.gsp",
        "src/main/templates/scaffolding/index.gsp",
        "src/main/templates/scaffolding/_form.gsp",
        "src/main/templates/scaffolding/_geometry-panel.gsp",
        "src/main/templates/scaffolding/_show-details.gsp",
        "grails-app/views/layouts/main.gsp",
        "grails-app/assets/javascripts/ili-carbon-wc-bundle.js",
        "grails-app/assets/javascripts/ili-geometry-editor.js",
        "grails-app/assets/javascripts/ili-form-ux.js",
        "grails-app/assets/javascripts/ili-carbon-input-bridge.js",
        "grails-app/assets/stylesheets/ili-modern.css"
    );
    private static final List<String> APPLICATION_JS_REQUIRES = List.of(
        "//= require ili-geometry-editor.js",
        "//= require ili-form-ux.js",
        "//= require ili-carbon-input-bridge.js"
    );

    public void install(Path grailsProjectDir, GenerationConfig config) throws IOException {
        Objects.requireNonNull(grailsProjectDir, "grailsProjectDir");
        Objects.requireNonNull(config, "config");
        if (!GenerationConfig.UI_THEME_CARBON.equals(config.getUiTheme())) {
            return;
        }
        for (String relativePath : MANAGED_FILES) {
            copyManagedResource(grailsProjectDir, relativePath);
        }
        ensureAssetRequires(grailsProjectDir.resolve("grails-app/assets/javascripts/application.js"));
    }

    private void copyManagedResource(Path grailsProjectDir, String relativePath) throws IOException {
        String resourcePath = OVERLAY_ROOT + relativePath;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing overlay resource: " + resourcePath);
            }
            Path target = grailsProjectDir.resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.write(target, inputStream.readAllBytes());
        }
    }

    private void ensureAssetRequires(Path applicationJs) throws IOException {
        if (!Files.exists(applicationJs)) {
            return;
        }
        String content = Files.readString(applicationJs, StandardCharsets.UTF_8);
        String updatedContent = content;
        for (String requireLine : APPLICATION_JS_REQUIRES) {
            if (updatedContent.contains(requireLine)) {
                continue;
            }
            if (updatedContent.contains("//= require_self")) {
                updatedContent = updatedContent.replace("//= require_self", requireLine + "\n//= require_self");
            } else {
                updatedContent = updatedContent + "\n" + requireLine + "\n";
            }
        }
        if (!updatedContent.equals(content)) {
            Files.writeString(applicationJs, updatedContent, StandardCharsets.UTF_8);
        }
    }
}

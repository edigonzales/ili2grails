package ch.interlis.generator.grails;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Installiert die verbleibenden managed Template-Overlays in ein
 * Grails-Projekt.
 *
 * <p>Seit der Runtime-Plugin-Migration verwaltet dieser Installer nur noch
 * Generation-Time-Templates (Scaffolding) und die Locale-Konfiguration.
 * Runtime-Klassen, Views, Assets und I18n kommen aus dem
 * {@code ili2grails-runtime}-Plugin.</p>
 */
public class GrailsTemplateOverlayInstaller {

    private static final String OVERLAY_ROOT = "grails/overlays/bootstrap-openlayers/";
    private static final List<String> MANAGED_FILES = List.of(
        "src/main/templates/scaffolding/Controller.groovy",
        "src/main/templates/scaffolding/create.gsp",
        "src/main/templates/scaffolding/edit.gsp",
        "src/main/templates/scaffolding/show.gsp",
        "src/main/templates/scaffolding/index.gsp",
        "src/main/templates/scaffolding/_list-header.gsp",
        "src/main/templates/scaffolding/_list-filters.gsp",
        "src/main/templates/scaffolding/_list-filter-field.gsp",
        "src/main/templates/scaffolding/_list-table.gsp",
        "src/main/templates/scaffolding/_list-pagination.gsp",
        "src/main/templates/scaffolding/_list-empty.gsp",
        "src/main/templates/scaffolding/_form.gsp",
        "src/main/templates/scaffolding/_form-section.gsp",
        "src/main/templates/scaffolding/_geometry-panel.gsp",
        "src/main/templates/scaffolding/_relationship-fields.gsp",
        "src/main/templates/scaffolding/_show-details.gsp",
        "src/main/templates/scaffolding/_association-sections.gsp",
        "src/main/templates/scaffolding/_association-row-actions.gsp",
        "src/main/templates/scaffolding/_association-quick-add.gsp",
        "src/main/templates/scaffolding/_association-context-summary.gsp",
        "src/main/templates/scaffolding/_inverse-relationship-sections.gsp",
        "src/main/templates/scaffolding/_inverse-relationship-picker.gsp",
        "grails-app/conf/spring/resources.groovy"
    );
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
    private static final List<String> LEGACY_APPLICATION_JS_REQUIRES = List.of(
        "//= require ili-carbon-input-bridge.js"
    );

    static List<String> managedFilesForTesting() {
        return MANAGED_FILES;
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
        for (String relativePath : MANAGED_FILES) {
            if (relativePath.equals("grails-app/conf/spring/resources.groovy")) {
                copyLocaleConfiguration(grailsProjectDir, config);
            } else {
                copyManagedResource(grailsProjectDir, relativePath);
            }
        }
        ensureJavascriptRequires(grailsProjectDir.resolve("grails-app/assets/javascripts/application.js"));
        ensureStylesheetRequires(grailsProjectDir.resolve("grails-app/assets/stylesheets/application.css"));
    }

    private void cleanupLegacyCarbonArtifacts(Path grailsProjectDir) throws IOException {
        for (String legacyFile : LEGACY_FILES) {
            Files.deleteIfExists(grailsProjectDir.resolve(legacyFile));
        }
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

    private void copyLocaleConfiguration(Path grailsProjectDir, GenerationConfig config) throws IOException {
        String resourcePath = OVERLAY_ROOT + "grails-app/conf/spring/resources.groovy";
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing overlay resource: " + resourcePath);
            }
            String generated = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                .replace("Locale.forLanguageTag(\"de-CH\")",
                    "Locale.forLanguageTag(\"" + config.getLanguage() + "\")");
            Path target = grailsProjectDir.resolve("grails-app/conf/spring/resources.groovy");
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                Files.writeString(target, generated, StandardCharsets.UTF_8);
                return;
            }
            String existing = Files.readString(target, StandardCharsets.UTF_8);
            if (existing.contains("localeResolver(")) {
                String updated = existing.replaceAll(
                    "Locale\\.forLanguageTag\\(\\\"[^\\\"]+\\\"\\)",
                    Matcher.quoteReplacement("Locale.forLanguageTag(\"" + config.getLanguage() + "\")")
                );
                if (!updated.equals(existing)) {
                    Files.writeString(target, updated, StandardCharsets.UTF_8);
                }
                return;
            }
            int closingBrace = existing.lastIndexOf('}');
            if (closingBrace < 0) {
                throw new IOException("Cannot extend Spring resources configuration: missing closing brace");
            }
            String insertion = "    localeResolver(org.springframework.web.servlet.i18n.FixedLocaleResolver, "
                + "java.util.Locale.forLanguageTag(\"" + config.getLanguage() + "\"))\n";
            String updated = existing.substring(0, closingBrace) + insertion + existing.substring(closingBrace);
            Files.writeString(target, updated, StandardCharsets.UTF_8);
        }
    }

    private void ensureJavascriptRequires(Path applicationJs) throws IOException {
        if (!Files.exists(applicationJs)) {
            return;
        }
        String content = Files.readString(applicationJs, StandardCharsets.UTF_8);
        String updatedContent = removeLegacyRequires(content);
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

    private void ensureStylesheetRequires(Path applicationCss) throws IOException {
        if (!Files.exists(applicationCss)) {
            return;
        }
        String content = Files.readString(applicationCss, StandardCharsets.UTF_8);
        String updatedContent = content;
        for (String requireLine : APPLICATION_CSS_REQUIRES) {
            if (updatedContent.contains(requireLine)) {
                continue;
            }
            if (updatedContent.contains("*= require_self")) {
                updatedContent = updatedContent.replace("*= require_self", requireLine + "\n *= require_self");
            } else if (updatedContent.contains("*/")) {
                updatedContent = updatedContent.replace("*/", " " + requireLine + "\n */");
            } else {
                updatedContent = updatedContent + "\n" + requireLine + "\n";
            }
        }
        if (!updatedContent.equals(content)) {
            Files.writeString(applicationCss, updatedContent, StandardCharsets.UTF_8);
        }
    }

    private String removeLegacyRequires(String content) {
        String updatedContent = content;
        for (String legacyRequire : LEGACY_APPLICATION_JS_REQUIRES) {
            String regex = "(?m)^\\s*" + Pattern.quote(legacyRequire) + "\\s*\\R?";
            updatedContent = updatedContent.replaceAll(regex, "");
        }
        return updatedContent;
    }
}

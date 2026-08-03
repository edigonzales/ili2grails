package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.project.plan.TextFileEdit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Updates the application asset manifest (application.js / application.css)
 * with the require lines for plugin assets.
 *
 * <p>This is the minimal integration step the Grails Asset Pipeline needs;
 * the actual plugin assets are served from the plugin JAR.</p>
 */
public final class GrailsAssetManifestUpdater {

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
        "*= require webjars/ol/9.2.4/ol.css",
        "*= require ili-modern.css"
    );
    private static final List<String> LEGACY_APPLICATION_JS_REQUIRES = List.of(
        "//= require ili-carbon-input-bridge.js"
    );

    public void update(Path grailsProjectDir) throws IOException {
        Objects.requireNonNull(grailsProjectDir, "grailsProjectDir");
        applyPlan(grailsProjectDir, "grails-app/assets/javascripts/application.js", true);
        applyPlan(grailsProjectDir, "grails-app/assets/stylesheets/application.css", false);
    }

    /**
     * Reine Planungsfunktion (Spezifikation §41.5): kein Write.
     */
    public TextFileEdit plan(Path relativePath, String existingContent) {
        if (existingContent == null) {
            return new TextFileEdit(relativePath, null, false, "asset manifest missing");
        }
        String updatedContent = relativePath.toString().endsWith(".css")
            ? ensureStylesheetRequiresInContent(existingContent)
            : ensureJavascriptRequiresInContent(existingContent);
        return new TextFileEdit(relativePath, updatedContent,
            !updatedContent.equals(existingContent), "asset requires");
    }

    private void applyPlan(Path grailsProjectDir, String relative, boolean javascript)
        throws IOException {
        Path target = grailsProjectDir.resolve(relative);
        if (!Files.exists(target)) {
            return;
        }
        TextFileEdit edit = plan(Path.of(relative),
            Files.readString(target, StandardCharsets.UTF_8));
        if (edit.changed()) {
            Files.writeString(target, edit.updatedContent(), StandardCharsets.UTF_8);
        }
    }

    private String ensureJavascriptRequiresInContent(String content) {
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
        return updatedContent;
    }

    private String ensureStylesheetRequiresInContent(String content) {
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
        return updatedContent;
    }

    static List<String> applicationJsRequiresForTesting() {
        return APPLICATION_JS_REQUIRES;
    }

    static List<String> applicationCssRequiresForTesting() {
        return APPLICATION_CSS_REQUIRES;
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
            String regex = "(?m)^\\s*" + java.util.regex.Pattern.quote(legacyRequire) + "\\s*\\R?";
            updatedContent = updatedContent.replaceAll(regex, "");
        }
        return updatedContent;
    }
}

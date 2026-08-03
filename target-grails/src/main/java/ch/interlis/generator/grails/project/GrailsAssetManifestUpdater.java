package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.project.plan.TextFileEdit;
import java.nio.file.Path;
import java.util.List;

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

    private String removeLegacyRequires(String content) {
        String updatedContent = content;
        for (String legacyRequire : LEGACY_APPLICATION_JS_REQUIRES) {
            String regex = "(?m)^\\s*" + java.util.regex.Pattern.quote(legacyRequire) + "\\s*\\R?";
            updatedContent = updatedContent.replaceAll(regex, "");
        }
        return updatedContent;
    }
}

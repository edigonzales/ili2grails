package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.project.plan.PlannedProjectFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Installs the generation-time scaffolding templates into the Grails project.
 *
 * <p>Templates are the only overlay resources that are still copied; they are
 * {@code GENERATOR_MANAGED} and overwrite allowed.</p>
 */
public final class GrailsScaffoldingTemplateInstaller {

    private static final String OVERLAY_ROOT = "grails/overlays/bootstrap-openlayers/";
    private static final List<String> TEMPLATE_FILES = List.of(
        "Controller.groovy",
        "create.gsp",
        "edit.gsp",
        "show.gsp",
        "index.gsp",
        "_list-header.gsp",
        "_list-filters.gsp",
        "_list-filter-field.gsp",
        "_list-table.gsp",
        "_list-pagination.gsp",
        "_list-empty.gsp",
        "_form.gsp",
        "_form-section.gsp",
        "_geometry-panel.gsp",
        "_relationship-fields.gsp",
        "_show-details.gsp",
        "_association-sections.gsp",
        "_association-row-actions.gsp",
        "_association-quick-add.gsp",
        "_association-context-summary.gsp",
        "_inverse-relationship-sections.gsp",
        "_inverse-relationship-picker.gsp"
    );

    /**
     * Reine Planungsfunktion (Spezifikation §41.6): alle Ressourcen werden
     * vollständig gelesen, bevor geschrieben wird.
     */
    public List<PlannedProjectFile> plan() {
        List<PlannedProjectFile> planned = new java.util.ArrayList<>();
        for (String template : TEMPLATE_FILES) {
            String relativePath = "src/main/templates/scaffolding/" + template;
            String resourcePath = OVERLAY_ROOT + relativePath;
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Missing overlay resource: " + resourcePath);
                }
                planned.add(new PlannedProjectFile(
                    java.nio.file.Path.of(relativePath),
                    GrailsProjectFileOwner.GENERATOR_MANAGED,
                    inputStream.readAllBytes(),
                    "scaffolding template " + template));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read overlay resource " + resourcePath, e);
            }
        }
        return planned;
    }

    public static List<String> templateFilesForTesting() {
        return TEMPLATE_FILES;
    }

    /**
     * UI-Views des Runtime-Plugins, die in der App lokal liegen müssen
     * (P2-D014): Grails 7 kann Plugin-JAR-Views im Dev-Modus (bootRun)
     * nicht auflösen - die View-Auflösung ist dateibasiert. Die Dateien
     * sind GENERATOR_MANAGED; das Plugin-JAR behält seine Kopien für
     * War-Deployments.
     */
    public static final List<String> UI_VIEW_FILES = List.of(
        "interlisUi/index.gsp",
        "interlisUi/_domain-link.gsp",
        "interlisUi/_explorer-results.gsp",
        "interlisUi/_navigation-groups.gsp",
        "interlisUi/_sidebar.gsp",
        "interlisUi/_workspace-danger-zone.gsp",
        "interlisUi/_workspace-details.gsp",
        "interlisUi/_workspace-empty.gsp",
        "interlisUi/_workspace-header.gsp",
        "interlisUi/_workspace-link.gsp",
        "interlisUi/_workspace-relationships.gsp",
        "interlisUi/_workspace-table.gsp",
        "layouts/ili2grails.gsp"
    );

    private static final String UI_VIEW_RESOURCE_ROOT =
        "grails/overlays/ui-views/";

    /**
     * Runtime-JS-Assets, die app-lokal liegen müssen (gleiche Grails-7-
     * Dev-Mode-Einschränkung wie bei den Views, P2-D014).
     */
    public static final List<String> UI_ASSET_FILES = List.of(
        "ili-form-ux.js",
        "ili-geometry-editor.js",
        "ili-notifications.js",
        "ili-navigation.js"
    );

    private static final String UI_ASSET_RESOURCE_ROOT =
        "grails/overlays/ui-assets/javascripts/";

    public List<PlannedProjectFile> planUiStylesheet() {
        String relativePath = "grails-app/assets/stylesheets/ili-modern.css";
        String resourcePath = "grails/overlays/ui-assets/stylesheets/ili-modern.css";
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing plugin stylesheet resource: " + resourcePath);
            }
            return List.of(new PlannedProjectFile(
                java.nio.file.Path.of(relativePath),
                GrailsProjectFileOwner.GENERATOR_MANAGED,
                inputStream.readAllBytes(),
                "app-local runtime stylesheet (Grails 7 dev-mode limitation)"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read plugin stylesheet resource " + resourcePath, e);
        }
    }

    public List<PlannedProjectFile> planUiAssets() {
        List<PlannedProjectFile> planned = new java.util.ArrayList<>();
        for (String asset : UI_ASSET_FILES) {
            String relativePath = "grails-app/assets/javascripts/" + asset;
            String resourcePath = UI_ASSET_RESOURCE_ROOT + asset;
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Missing plugin asset resource: " + resourcePath);
                }
                planned.add(new PlannedProjectFile(
                    java.nio.file.Path.of(relativePath),
                    GrailsProjectFileOwner.GENERATOR_MANAGED,
                    inputStream.readAllBytes(),
                    "app-local runtime asset (Grails 7 dev-mode limitation) " + asset));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read plugin asset resource " + resourcePath, e);
            }
        }
        return planned;
    }

    public List<PlannedProjectFile> planUiViews() {
        List<PlannedProjectFile> planned = new java.util.ArrayList<>();
        for (String view : UI_VIEW_FILES) {
            String relativePath = "grails-app/views/" + view;
            String resourcePath = UI_VIEW_RESOURCE_ROOT + view;
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Missing plugin view resource: " + resourcePath);
                }
                planned.add(new PlannedProjectFile(
                    java.nio.file.Path.of(relativePath),
                    GrailsProjectFileOwner.GENERATOR_MANAGED,
                    inputStream.readAllBytes(),
                    "app-local UI view (Grails 7 dev-mode limitation) " + view));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read plugin view resource " + resourcePath, e);
            }
        }
        return planned;
    }
}

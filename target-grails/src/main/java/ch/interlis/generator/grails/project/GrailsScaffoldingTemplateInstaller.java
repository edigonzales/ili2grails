package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.project.plan.PlannedProjectFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

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

    public void install(Path grailsProjectDir) throws IOException {
        Objects.requireNonNull(grailsProjectDir, "grailsProjectDir");
        for (PlannedProjectFile planned : plan()) {
            Path target = grailsProjectDir.resolve(planned.relativePath());
            Files.createDirectories(target.getParent());
            Files.write(target, planned.content());
        }
    }

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
}

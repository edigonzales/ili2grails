package ch.interlis.generator.grails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsTemplateOverlayInstallerTest {

    @Test
    void installsManagedFilesAndUpdatesApplicationJsIdempotently(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("my-grails-app");
        Path applicationJs = projectDir.resolve("grails-app/assets/javascripts/application.js");
        Files.createDirectories(applicationJs.getParent());
        Files.writeString(applicationJs, String.join("\n",
            "//= require webjars/jquery/%/dist/jquery.js",
            "//= require_self",
            ""
        ));

        GenerationConfig config = GenerationConfig.builder(projectDir, "com.example")
            .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
            .mapEditor(GenerationConfig.MAP_EDITOR_OPENLAYERS)
            .build();

        GrailsTemplateOverlayInstaller installer = new GrailsTemplateOverlayInstaller();
        installer.install(projectDir, config);
        installer.install(projectDir, config);

        assertThat(projectDir.resolve("src/main/templates/scaffolding/Controller.groovy")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/create.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/edit.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/show.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/index.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_form.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_geometry-panel.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_relationship-fields.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_show-details.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/layouts/main.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-geometry-editor.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-form-ux.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/stylesheets/ili-modern.css")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-carbon-wc-bundle.js")).doesNotExist();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-carbon-input-bridge.js")).doesNotExist();

        String updatedApplicationJs = Files.readString(applicationJs);
        assertThat(updatedApplicationJs).contains("//= require ili-geometry-editor.js");
        assertThat(updatedApplicationJs).contains("//= require ili-form-ux.js");
        assertThat(updatedApplicationJs).doesNotContain("//= require ili-carbon-input-bridge.js");
        assertThat(updatedApplicationJs.indexOf("//= require ili-geometry-editor.js"))
            .isEqualTo(updatedApplicationJs.lastIndexOf("//= require ili-geometry-editor.js"));
        assertThat(updatedApplicationJs.indexOf("//= require ili-form-ux.js"))
            .isEqualTo(updatedApplicationJs.lastIndexOf("//= require ili-form-ux.js"));

        String indexTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/index.gsp"));
        assertThat(indexTemplate).contains("<table class=\"table");
        assertThat(indexTemplate).contains("data-row-delete");
        assertThat(indexTemplate).contains("name=\"q\"");
        assertThat(indexTemplate).contains("name=\"max\"");
        assertThat(indexTemplate).contains("<g:paginate");
        assertThat(indexTemplate).contains("ili-list-tools");
        assertThat(indexTemplate).doesNotContain("bx-table");

        String formTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_form.gsp"));
        assertThat(formTemplate).contains("ili-split-layout");
        assertThat(formTemplate).contains("data-unsaved-badge");
        assertThat(formTemplate).contains("template=\"relationship-fields\"");
        assertThat(formTemplate).contains("relationshipFields ?: []");
        assertThat(formTemplate).doesNotContain("js-carbon-bridge");

        String relationshipTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_relationship-fields.gsp"));
        assertThat(relationshipTemplate).contains("form-select");
        assertThat(relationshipTemplate).contains("relationshipOptions");
        assertThat(relationshipTemplate).contains("relationshipRequired");
        assertThat(relationshipTemplate).contains("js-relationship-search");
        assertThat(relationshipTemplate).contains("data-relationship-url");

        String showTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/show.gsp"));
        assertThat(showTemplate).contains("Danger Zone");
        assertThat(showTemplate).contains("data-delete-open");
        assertThat(showTemplate).contains("modal fade");
        assertThat(showTemplate).doesNotContain("bx-modal");

        String layoutTemplate = Files.readString(projectDir.resolve("grails-app/views/layouts/main.gsp"));
        assertThat(layoutTemplate).contains("bootstrap@5.3.3");
        assertThat(layoutTemplate).contains("navbar-toggler");
        assertThat(layoutTemplate).doesNotContain("ili-carbon-wc-bundle.js");
        assertThat(layoutTemplate).doesNotContain("<bx-header");

        String controllerTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/Controller.groovy"));
        assertThat(controllerTemplate).contains("def index(Integer max, Integer offset)");
        assertThat(controllerTemplate).contains("paginationParams");
        assertThat(controllerTemplate).contains("pagedRecords");
        assertThat(controllerTemplate).contains("relationshipModel");
        assertThat(controllerTemplate).contains("relationshipOptions");
        assertThat(controllerTemplate).contains("relationshipOptionPage");
        assertThat(controllerTemplate).contains("relationshipOptionLabel");
        assertThat(controllerTemplate).contains("render page as JSON");
        assertThat(controllerTemplate).contains("params.int(\"max\")");

        String formUx = Files.readString(projectDir.resolve("grails-app/assets/javascripts/ili-form-ux.js"));
        assertThat(formUx).contains("initRelationshipAutocomplete");
        assertThat(formUx).contains("js-relationship-search");

        String stylesheet = Files.readString(projectDir.resolve("grails-app/assets/stylesheets/ili-modern.css"));
        assertThat(stylesheet).contains(".ili-list-tools");
        assertThat(stylesheet).contains(".ili-pagination-bar");
    }

    @Test
    void skipsOverlayInstallationForDefaultTheme(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("my-grails-app");
        Files.createDirectories(projectDir);
        GenerationConfig config = GenerationConfig.builder(projectDir, "com.example")
            .uiTheme(GenerationConfig.UI_THEME_DEFAULT)
            .build();

        new GrailsTemplateOverlayInstaller().install(projectDir, config);

        assertThat(projectDir.resolve("src/main/templates/scaffolding/Controller.groovy")).doesNotExist();
    }

    @Test
    void removesLegacyCarbonArtifactsWhenInstallingBootstrapOverlay(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("my-grails-app");
        Path legacyBundle = projectDir.resolve("grails-app/assets/javascripts/ili-carbon-wc-bundle.js");
        Path legacyBridge = projectDir.resolve("grails-app/assets/javascripts/ili-carbon-input-bridge.js");
        Path applicationJs = projectDir.resolve("grails-app/assets/javascripts/application.js");

        Files.createDirectories(legacyBundle.getParent());
        Files.writeString(legacyBundle, "legacy bundle");
        Files.writeString(legacyBridge, "legacy bridge");
        Files.writeString(applicationJs, String.join("\n",
            "//= require ili-carbon-input-bridge.js",
            "//= require_self",
            ""
        ));

        GenerationConfig config = GenerationConfig.builder(projectDir, "com.example")
            .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
            .build();

        new GrailsTemplateOverlayInstaller().install(projectDir, config);

        assertThat(legacyBundle).doesNotExist();
        assertThat(legacyBridge).doesNotExist();

        String updatedApplicationJs = Files.readString(applicationJs);
        assertThat(updatedApplicationJs).doesNotContain("//= require ili-carbon-input-bridge.js");
        assertThat(updatedApplicationJs).contains("//= require ili-geometry-editor.js");
        assertThat(updatedApplicationJs).contains("//= require ili-form-ux.js");
    }
}

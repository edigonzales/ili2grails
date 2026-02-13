package ch.interlis.generator.generator;

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
            .uiTheme(GenerationConfig.UI_THEME_CARBON)
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
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_show-details.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/layouts/main.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-carbon-wc-bundle.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-geometry-editor.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-form-ux.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-carbon-input-bridge.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/stylesheets/ili-modern.css")).exists();

        String updatedApplicationJs = Files.readString(applicationJs);
        assertThat(updatedApplicationJs).contains("//= require ili-geometry-editor.js");
        assertThat(updatedApplicationJs).contains("//= require ili-form-ux.js");
        assertThat(updatedApplicationJs).contains("//= require ili-carbon-input-bridge.js");
        assertThat(updatedApplicationJs.indexOf("//= require ili-geometry-editor.js"))
            .isEqualTo(updatedApplicationJs.lastIndexOf("//= require ili-geometry-editor.js"));
        assertThat(updatedApplicationJs.indexOf("//= require ili-form-ux.js"))
            .isEqualTo(updatedApplicationJs.lastIndexOf("//= require ili-form-ux.js"));
        assertThat(updatedApplicationJs.indexOf("//= require ili-carbon-input-bridge.js"))
            .isEqualTo(updatedApplicationJs.lastIndexOf("//= require ili-carbon-input-bridge.js"));

        String indexTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/index.gsp"));
        assertThat(indexTemplate).contains("bx-table");
        assertThat(indexTemplate).contains("bx-table-header-cell");
        assertThat(indexTemplate).contains("data-row-delete");

        String formTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_form.gsp"));
        assertThat(formTemplate).contains("ili-split-layout");
        assertThat(formTemplate).contains("data-unsaved-badge");
        assertThat(formTemplate).contains("js-carbon-bridge");

        String showTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/show.gsp"));
        assertThat(showTemplate).contains("Danger Zone");
        assertThat(showTemplate).contains("data-delete-open");
        assertThat(showTemplate).contains("bx-modal");

        String layoutTemplate = Files.readString(projectDir.resolve("grails-app/views/layouts/main.gsp"));
        assertThat(layoutTemplate).contains("<asset:javascript src=\"ili-carbon-wc-bundle.js\"/>");
        assertThat(layoutTemplate).doesNotContain("script type=\"module\"");

        String controllerTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/Controller.groovy"));
        assertThat(controllerTemplate).contains("def index()");
        assertThat(controllerTemplate).contains("list([:])");
        assertThat(controllerTemplate).doesNotContain("params.max");
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
}

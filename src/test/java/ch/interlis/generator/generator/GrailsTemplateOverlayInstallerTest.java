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
        assertThat(projectDir.resolve("grails-app/views/layouts/main.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-geometry-editor.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/stylesheets/ili-modern.css")).exists();

        String updatedApplicationJs = Files.readString(applicationJs);
        assertThat(updatedApplicationJs).contains("//= require ili-geometry-editor.js");
        assertThat(updatedApplicationJs.indexOf("//= require ili-geometry-editor.js"))
            .isEqualTo(updatedApplicationJs.lastIndexOf("//= require ili-geometry-editor.js"));
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

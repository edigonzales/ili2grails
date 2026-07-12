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
        Path applicationCss = projectDir.resolve("grails-app/assets/stylesheets/application.css");
        Files.createDirectories(applicationJs.getParent());
        Files.createDirectories(applicationCss.getParent());
        Files.writeString(applicationJs, String.join("\n",
            "//= require webjars/jquery/%/dist/jquery.js",
            "//= require_self",
            ""
        ));
        Files.writeString(applicationCss, String.join("\n",
            "/*",
            " *= require_self",
            " */",
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
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisCrudControllerSupport.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisGeometryBinder.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisRelationshipOptions.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisTableModel.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisAssociationRegistrySupport.groovy")).exists();
        assertThat(projectDir.resolve("grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationQueryService.groovy")).exists();
        assertThat(projectDir.resolve("grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationCommandService.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisAssociationContextSupport.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisNavigationSupport.groovy")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_association-sections.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_association-row-actions.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_association-quick-add.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_association-context-summary.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/layouts/main.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-geometry-editor.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-form-ux.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/stylesheets/ili-modern.css")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-carbon-wc-bundle.js")).doesNotExist();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-carbon-input-bridge.js")).doesNotExist();

        String updatedApplicationJs = Files.readString(applicationJs);
        assertThat(updatedApplicationJs).contains("//= require webjars/proj4/2.11.0/dist/proj4.js");
        assertThat(updatedApplicationJs).contains("//= require webjars/ol/9.2.4/dist/ol.js");
        assertThat(updatedApplicationJs).contains("//= require webjars/bootstrap/5.3.3/js/bootstrap.bundle.min.js");
        assertThat(updatedApplicationJs).contains("//= require ili-geometry-editor.js");
        assertThat(updatedApplicationJs).contains("//= require ili-form-ux.js");
        assertThat(updatedApplicationJs).doesNotContain("//= require ili-carbon-input-bridge.js");
        assertThat(updatedApplicationJs.indexOf("//= require ili-geometry-editor.js"))
            .isEqualTo(updatedApplicationJs.lastIndexOf("//= require ili-geometry-editor.js"));
        assertThat(updatedApplicationJs.indexOf("//= require ili-form-ux.js"))
            .isEqualTo(updatedApplicationJs.lastIndexOf("//= require ili-form-ux.js"));

        String updatedApplicationCss = Files.readString(applicationCss);
        assertThat(updatedApplicationCss).contains("*= require webjars/bootstrap/5.3.3/css/bootstrap.min.css");
        assertThat(updatedApplicationCss).contains("*= require webjars/ol/9.2.4/ol.css");
        assertThat(updatedApplicationCss).containsOnlyOnce("*= require webjars/bootstrap/5.3.3/css/bootstrap.min.css");
        assertThat(updatedApplicationCss).containsOnlyOnce("*= require webjars/ol/9.2.4/ol.css");

        String indexTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/index.gsp"));
        assertThat(indexTemplate).contains("<table class=\"table");
        assertThat(indexTemplate).contains("data-row-delete");
        assertThat(indexTemplate).contains("name=\"q\"");
        assertThat(indexTemplate).contains("name=\"max\"");
        assertThat(indexTemplate).contains("<g:paginate");
        assertThat(indexTemplate).contains("ili-list-tools");
        assertThat(indexTemplate).contains("g:sortableColumn");
        assertThat(indexTemplate).contains("Typisierte Filter");
        assertThat(indexTemplate).doesNotContain("bx-table");

        String formTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_form.gsp"));
        assertThat(formTemplate).contains("ili-split-layout");
        assertThat(formTemplate).contains("data-unsaved-badge");
        assertThat(formTemplate).contains("template=\"relationship-fields\"");
        assertThat(formTemplate).contains("relationshipFields ?: []");
        assertThat(formTemplate).contains("fieldMeta");
        assertThat(formTemplate).contains("ili-field-help-panel");
        assertThat(formTemplate).doesNotContain("js-carbon-bridge");

        String relationshipTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_relationship-fields.gsp"));
        assertThat(relationshipTemplate).contains("form-select");
        assertThat(relationshipTemplate).contains("relationshipOptions");
        assertThat(relationshipTemplate).contains("relationshipRequired");
        assertThat(relationshipTemplate).contains("js-relationship-search");
        assertThat(relationshipTemplate).contains("data-relationship-url");
        assertThat(relationshipTemplate).contains("data-relationship-list");
        assertThat(relationshipTemplate).contains("role=\"listbox\"");

        String showTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/show.gsp"));
        assertThat(showTemplate).contains("Danger Zone");
        assertThat(showTemplate).contains("data-delete-open");
        assertThat(showTemplate).contains("modal fade");
        assertThat(showTemplate).doesNotContain("bx-modal");
        assertThat(showTemplate).contains("association-sections");
        assertThat(showTemplate).contains("associationDiagnostic");

        String associationSectionsTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_association-sections.gsp"));
        assertThat(associationSectionsTemplate).contains("ili-association-section");
        assertThat(associationSectionsTemplate).contains("ili-association-table");
        assertThat(associationSectionsTemplate).contains("ili-association-empty");
        assertThat(associationSectionsTemplate).contains("Mehr anzeigen");
        assertThat(associationSectionsTemplate).contains("association-quick-add");
        assertThat(associationSectionsTemplate).contains("data-association-delete");
        assertThat(associationSectionsTemplate).contains("action=\"associationDelete\"");

        String associationQuickAddTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_association-quick-add.gsp"));
        assertThat(associationQuickAddTemplate).contains("action=\"associationCreate\"");
        assertThat(associationQuickAddTemplate).contains("name=\"targetId\"");
        assertThat(associationQuickAddTemplate).contains("data-relationship-context");
        assertThat(associationQuickAddTemplate).contains("data-relationship-role");
        assertThat(associationQuickAddTemplate).contains("associationOptions");

        String associationRowActionsTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_association-row-actions.gsp"));
        assertThat(associationRowActionsTemplate).contains("associationController");
        assertThat(associationRowActionsTemplate).contains("associationId");

        String layoutTemplate = Files.readString(projectDir.resolve("grails-app/views/layouts/main.gsp"));
        assertThat(layoutTemplate).contains("<asset:stylesheet src=\"application.css\"/>");
        assertThat(layoutTemplate).contains("<asset:javascript src=\"application.js\"/>");
        assertThat(layoutTemplate).doesNotContain("https://cdn.jsdelivr.net");
        assertThat(layoutTemplate).contains("navbar-toggler");
        assertThat(layoutTemplate).doesNotContain("ili-carbon-wc-bundle.js");
        assertThat(layoutTemplate).doesNotContain("<bx-header");
        assertThat(layoutTemplate).contains("InterlisNavigationSupport.menuEntries");

        String controllerTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/Controller.groovy"));
        assertThat(controllerTemplate).contains("extends InterlisCrudControllerSupport");
        assertThat(controllerTemplate).contains("protected Class<${className}> domainType()");
        assertThat(controllerTemplate).contains("protected Object crudService()");
        assertThat(controllerTemplate).doesNotContain("paginationParams");
        assertThat(controllerTemplate).contains("InterlisAssociationQueryService");
        assertThat(controllerTemplate).contains("associationPage");
        assertThat(controllerTemplate).contains("associationOptions");
        assertThat(controllerTemplate).contains("protected Object associationQueryService()");
        assertThat(controllerTemplate).contains("InterlisAssociationCommandService");
        assertThat(controllerTemplate).contains("associationCreate");
        assertThat(controllerTemplate).contains("associationDelete");
        assertThat(controllerTemplate).contains("protected Object associationCommandService()");
        assertThat(controllerTemplate).contains("associationCreate: \"POST\"");
        assertThat(controllerTemplate).contains("associationDelete: \"DELETE\"");

        String controllerSupport = Files.readString(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisCrudControllerSupport.groovy"));
        assertThat(controllerSupport).contains("def index(Integer max, Integer offset)");
        assertThat(controllerSupport).contains("relationshipOptions");
        assertThat(controllerSupport).contains("InterlisGeometryBinder.bindGeometryFromParams");
        assertThat(controllerSupport).contains("fieldMeta()");
        assertThat(controllerSupport).contains("Content-Security-Policy");
        assertThat(controllerSupport).contains("DataIntegrityViolationException");
        assertThat(controllerSupport).contains("X-Content-Type-Options");
        assertThat(controllerSupport).contains("associationQueryService()");
        assertThat(controllerSupport).contains("associationModel(T instance)");
        assertThat(controllerSupport).contains("associationPage(Long id)");
        assertThat(controllerSupport).contains("associationOptions(Long id)");
        assertThat(controllerSupport).contains("associationCommandService()");
        assertThat(controllerSupport).contains("associationCreate(Long id)");
        assertThat(controllerSupport).contains("associationDelete(Long id)");
        assertThat(controllerSupport).contains("respondAssociationCommand(T instance, Map<String, Object> result)");
        assertThat(controllerSupport).contains("respondAssociationError(int status, String code, String message)");

        String relationshipOptions = Files.readString(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisRelationshipOptions.groovy"));
        assertThat(relationshipOptions).contains("interlisDisplayMeta");
        assertThat(relationshipOptions).contains("nextOffset");
        assertThat(relationshipOptions).contains("pagination.offset + options.size()");

        String geometryBinder = Files.readString(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisGeometryBinder.groovy"));
        assertThat(geometryBinder).contains("IsValidOp");
        assertThat(geometryBinder).contains("MULTIPOLYGON");
        assertThat(geometryBinder).contains("maxWktLength");
        assertThat(geometryBinder).contains("maxVertices");

        String formUx = Files.readString(projectDir.resolve("grails-app/assets/javascripts/ili-form-ux.js"));
        assertThat(formUx).contains("initRelationshipAutocomplete");
        assertThat(formUx).contains("js-relationship-search");
        assertThat(formUx).contains("pagination.nextOffset");
        assertThat(formUx).contains("data-relationship-list");
        assertThat(formUx).contains("list.addEventListener(\"scroll\"");
        assertThat(formUx).contains("data-relationship-context");
        assertThat(formUx).contains("data-relationship-role");
        assertThat(formUx).contains("data-association-delete");
        assertThat(formUx).contains("initQuickAddForms");

        String geometryEditor = Files.readString(projectDir.resolve("grails-app/assets/javascripts/ili-geometry-editor.js"));
        assertThat(geometryEditor).contains("ol.interaction.Snap");

        String stylesheet = Files.readString(projectDir.resolve("grails-app/assets/stylesheets/ili-modern.css"));
        assertThat(stylesheet).contains(".ili-list-tools");
        assertThat(stylesheet).contains(".ili-pagination-bar");
        assertThat(stylesheet).contains("--dp-color-accent");
        assertThat(stylesheet).contains(".ili-field-help-panel");
        assertThat(stylesheet).contains(".ili-relationship-results");
        assertThat(stylesheet).contains(".ili-association-quick-form");
        assertThat(stylesheet).contains("prefers-reduced-motion");
        assertThat(stylesheet).contains("@media print");
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

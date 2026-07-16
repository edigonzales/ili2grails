package ch.interlis.generator.grails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsTemplateOverlayInstallerTest {

    private static final Pattern ICON_DECLARATION = Pattern.compile(
        "(?m)^\\s*(?:\\\"([^\\\"]+)\\\"|([A-Za-z0-9_-]+)): \\\'\\\'\\\'<path"
    );
    private static final Pattern ICON_USE = Pattern.compile("<ili:icon\\s+name=\\\"([^\\\"]+)\\\"");

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
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_list-header.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_list-filters.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_list-filter-field.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_list-table.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_list-pagination.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_list-empty.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_form.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_form-section.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_geometry-panel.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_relationship-fields.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_show-details.gsp")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisCrudControllerSupport.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisFormSupport.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisGeometryBinder.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisRelationshipOptions.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisTableModel.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisListQuerySupport.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisAssociationRegistrySupport.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisUiDescriptorSupport.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisWorkspaceSupport.groovy")).exists();
        assertThat(projectDir.resolve("grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationQueryService.groovy")).exists();
        assertThat(projectDir.resolve("grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationCommandService.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisAssociationContextSupport.groovy")).exists();
        assertThat(projectDir.resolve("src/main/groovy/ch/interlis/generator/grails/runtime/InterlisNavigationSupport.groovy")).exists();
        assertThat(projectDir.resolve("grails-app/controllers/ch/interlis/generator/grails/runtime/InterlisUiController.groovy")).exists();
        assertThat(projectDir.resolve("grails-app/taglib/ch/interlis/generator/grails/runtime/InterlisUiTagLib.groovy")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/index.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/_explorer-results.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/_sidebar.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/_navigation-groups.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/_domain-link.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/_workspace-link.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/_workspace-header.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/_workspace-details.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/_workspace-relationships.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/_workspace-danger-zone.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/_workspace-table.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/interlisUi/_workspace-empty.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_association-sections.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_association-row-actions.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_association-quick-add.gsp")).exists();
        assertThat(projectDir.resolve("src/main/templates/scaffolding/_association-context-summary.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/views/layouts/main.gsp")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-geometry-editor.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-form-ux.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-navigation.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/stylesheets/ili-modern.css")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-carbon-wc-bundle.js")).doesNotExist();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-carbon-input-bridge.js")).doesNotExist();

        String updatedApplicationJs = Files.readString(applicationJs);
        assertThat(updatedApplicationJs).contains("//= require webjars/proj4/2.11.0/dist/proj4.js");
        assertThat(updatedApplicationJs).contains("//= require webjars/ol/9.2.4/dist/ol.js");
        assertThat(updatedApplicationJs).contains("//= require webjars/bootstrap/5.3.3/js/bootstrap.bundle.min.js");
        assertThat(updatedApplicationJs).contains("//= require ili-geometry-editor.js");
        assertThat(updatedApplicationJs).contains("//= require ili-form-ux.js");
        assertThat(updatedApplicationJs).contains("//= require ili-navigation.js");
        assertThat(updatedApplicationJs).doesNotContain("//= require ili-carbon-input-bridge.js");
        GrailsTemplateOverlayInstaller.applicationJsRequiresForTesting().forEach(require ->
            assertThat(updatedApplicationJs).as(require).containsOnlyOnce(require));
        assertThat(updatedApplicationJs.indexOf("//= require ili-geometry-editor.js"))
            .isEqualTo(updatedApplicationJs.lastIndexOf("//= require ili-geometry-editor.js"));
        assertThat(updatedApplicationJs.indexOf("//= require ili-form-ux.js"))
            .isEqualTo(updatedApplicationJs.lastIndexOf("//= require ili-form-ux.js"));

        String updatedApplicationCss = Files.readString(applicationCss);
        assertThat(updatedApplicationCss).contains("*= require webjars/bootstrap/5.3.3/css/bootstrap.min.css");
        assertThat(updatedApplicationCss).contains("*= require webjars/ol/9.2.4/ol.css");
        assertThat(updatedApplicationCss).containsOnlyOnce("*= require webjars/bootstrap/5.3.3/css/bootstrap.min.css");
        assertThat(updatedApplicationCss).containsOnlyOnce("*= require webjars/ol/9.2.4/ol.css");
        GrailsTemplateOverlayInstaller.applicationCssRequiresForTesting().forEach(require ->
            assertThat(updatedApplicationCss).as(require).containsOnlyOnce(require));

        String indexTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/index.gsp"));
        assertThat(indexTemplate).contains("template=\"list-header\"");
        assertThat(indexTemplate).contains("template=\"list-filters\"");
        assertThat(indexTemplate).contains("template=\"list-table\"");
        assertThat(indexTemplate).contains("template=\"list-pagination\"");
        assertThat(indexTemplate).contains("template=\"list-empty\"");
        assertThat(indexTemplate).doesNotContain("bx-table");

        String listFiltersTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_list-filters.gsp"));
        assertThat(listFiltersTemplate).contains("name=\"q\"");
        assertThat(listFiltersTemplate).contains("activeFilterChips");
        String listTableTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_list-table.gsp"));
        assertThat(listTableTemplate).contains("<table class=\"table");
        assertThat(listTableTemplate).contains("data-row-delete");
        assertThat(listTableTemplate).contains("sortUrls");
        String listPaginationTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_list-pagination.gsp"));
        assertThat(listPaginationTemplate).contains("pagination.previousParams");

        String formTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_form.gsp"));
        assertThat(formTemplate).contains("ili-split-layout");
        assertThat(formTemplate).contains("data-unsaved-badge");
        assertThat(formTemplate).contains("template=\"form-section\"");
        assertThat(formTemplate).contains("submitMode");
        assertThat(formTemplate).contains("saveAndContinue");
        assertThat(formTemplate).contains("fieldMeta");
        assertThat(formTemplate).contains("ili-validation-summary");
        assertThat(formTemplate).doesNotContain("js-carbon-bridge");

        String formSectionTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_form-section.gsp"));
        assertThat(formSectionTemplate).contains("<f:field");
        assertThat(formSectionTemplate).contains("ili-field-meta");
        assertThat(formSectionTemplate).contains("aria-invalid", "widget-aria-invalid");
        assertThat(formSectionTemplate).contains("template=\"relationship-fields\"");

        String relationshipTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_relationship-fields.gsp"));
        assertThat(relationshipTemplate).contains("form-select");
        assertThat(relationshipTemplate).contains("relationshipOptions");
        assertThat(relationshipTemplate).contains("relationshipRequired");
        assertThat(relationshipTemplate).contains("js-relationship-search");
        assertThat(relationshipTemplate).contains("data-relationship-url");
        assertThat(relationshipTemplate).contains("data-relationship-list");
        assertThat(relationshipTemplate).contains("role=\"listbox\"");
        assertThat(relationshipTemplate).contains("role=\"combobox\"", "aria-expanded=\"false\"");
        assertThat(relationshipTemplate).contains("aria-invalid");
        assertThat(relationshipTemplate).contains("relationshipFieldsToRender");

        String geometryTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_geometry-panel.gsp"));
        assertThat(geometryTemplate).contains("aria-controls", "role=\"tabpanel\"", "aria-labelledby");

        String showTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/show.gsp"));
        assertThat(showTemplate).contains("association-sections");
        assertThat(showTemplate).contains("associationDiagnostic");
        assertThat(showTemplate).contains("workspace-header");
        assertThat(showTemplate).contains("workspace-details");
        assertThat(showTemplate).contains("workspace-relationships");
        assertThat(showTemplate).contains("workspace-danger-zone");
        assertThat(showTemplate).doesNotContain("_show-details");
        assertThat(showTemplate).doesNotContain("Audit", "Verlauf", "Protokoll", "Timeline", "Restore");

        String workspaceHeader = Files.readString(projectDir.resolve("grails-app/views/interlisUi/_workspace-header.gsp"));
        assertThat(workspaceHeader).contains("data-domain-workspace-header", "data-workspace-display-label");
        String workspaceDetails = Files.readString(projectDir.resolve("grails-app/views/interlisUi/_workspace-details.gsp"));
        assertThat(workspaceDetails).contains("detailSections", "<g:message", ".label").doesNotContain("audit");
        String workspaceRelationships = Files.readString(projectDir.resolve("grails-app/views/interlisUi/_workspace-relationships.gsp"));
        assertThat(workspaceRelationships).contains("relationshipLinks", "action=\"show\"", "Keine Zuordnung");
        String workspaceDangerZone = Files.readString(projectDir.resolve("grails-app/views/interlisUi/_workspace-danger-zone.gsp"));
        assertThat(workspaceDangerZone)
            .contains("Danger Zone")
            .contains("data-delete-open", "modal fade")
            .contains("role=\"dialog\"", "aria-modal=\"true\"", "aria-describedby")
            .contains("serverseitig geprüft", "Referenzielle Beziehungen", "Datenbank-Integritätsbedingungen")
            .doesNotContain("abhängige Daten werden", "garantiert", "bx-modal");
        String workspaceLink = Files.readString(projectDir.resolve("grails-app/views/interlisUi/_workspace-link.gsp"));
        assertThat(workspaceLink).contains("data-ili-workspace-link", "controller", "action")
            .doesNotContain("iliName", "domainClassName");
        String workspaceTable = Files.readString(projectDir.resolve("grails-app/views/interlisUi/_workspace-table.gsp"));
        assertThat(workspaceTable).contains("workspaceSection.columns", "row.values", "row.links",
            "template=\"/interlisUi/workspace-empty\"", "action=\"${cellLink.action ?: 'show'}\"");
        String workspaceEmpty = Files.readString(projectDir.resolve("grails-app/views/interlisUi/_workspace-empty.gsp"));
        assertThat(workspaceEmpty).contains("data-workspace-empty", "emptyMessage", "Keine Einträge");

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
        assertThat(layoutTemplate).contains("ili-sidebar-toggle");
        assertThat(layoutTemplate).doesNotContain("ili-carbon-wc-bundle.js");
        assertThat(layoutTemplate).doesNotContain("<bx-header");
        assertThat(layoutTemplate).contains("InterlisNavigationSupport.navigationModel");
        assertThat(layoutTemplate).contains("data-ili-domain-finder-form");
        assertThat(layoutTemplate).contains("role=\"combobox\"", "aria-autocomplete=\"list\"");
        assertThat(layoutTemplate).contains("data-ili-extension-point=\"user-slot\"");
        assertThat(layoutTemplate).doesNotContain("principal");
        assertThat(layoutTemplate).doesNotContain("navbar-toggler-icon");

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
        assertThat(controllerSupport).contains("InterlisFormSupport.submitMode");
        assertThat(controllerSupport).contains("saveAndContinue");
        assertThat(controllerSupport).contains("prepareEditContext");
        assertThat(controllerSupport).contains("Set<String> allowedFields");
        assertThat(controllerSupport).doesNotContain("new java.util.LinkedHashMap(params)");
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
        assertThat(controllerSupport).contains("InterlisWorkspaceSupport.showModel");
        assertThat(controllerSupport).contains("Datenbank-Integritätsbedingung");
        assertThat(controllerSupport).contains("respondAssociationCommand(T instance, Map<String, Object> result)");
        assertThat(controllerSupport).contains("respondAssociationError(int status, String code, String message)");

        String uiDescriptorSupport = Files.readString(projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisUiDescriptorSupport.groovy"));
        assertThat(uiDescriptorSupport).contains("static Map<String, Object> descriptor");
        assertThat(uiDescriptorSupport).contains("Unknown field");
        assertThat(uiDescriptorSupport).contains("detailSections", "scalarDetailProperty");

        String uiController = Files.readString(projectDir.resolve(
            "grails-app/controllers/ch/interlis/generator/grails/runtime/InterlisUiController.groovy"));
        assertThat(uiController).contains("static allowedMethods = [index: \"GET\", domains: \"GET\"]");
        assertThat(uiController).contains("Content-Security-Policy");
        assertThat(uiController).doesNotContain("unsafe-inline");
        assertThat(uiController).doesNotContain("unsafe-eval");
        assertThat(uiController).doesNotContain("UrlMappings");

        String uiTagLib = Files.readString(projectDir.resolve(
            "grails-app/taglib/ch/interlis/generator/grails/runtime/InterlisUiTagLib.groovy"));
        assertThat(uiTagLib).contains("static namespace = \"ili\"");
        assertThat(uiTagLib).contains("ICON_PATHS");
        assertThat(uiTagLib).doesNotContain("icon-font");
        assertThat(uiTagLib).doesNotContain("cdn");

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
        assertThat(formUx).contains("actionElement.form === form");
        assertThat(formUx).contains("a[href]");
        assertThat(formUx).contains("beforeunload");
        assertThat(formUx).contains("hidden.bs.modal", "_iliReturnFocus", "aria-activedescendant");

        String geometryEditor = Files.readString(projectDir.resolve("grails-app/assets/javascripts/ili-geometry-editor.js"));
        assertThat(geometryEditor).contains("ol.interaction.Snap");

        String stylesheet = Files.readString(projectDir.resolve("grails-app/assets/stylesheets/ili-modern.css"));
        assertThat(stylesheet).contains(".ili-list-tools");
        assertThat(stylesheet).contains(".ili-pagination-bar");
        assertThat(stylesheet).doesNotContain("--dp-");
        assertThat(stylesheet).doesNotContain("--ili-");
        assertThat(stylesheet).contains(".ili-domain-finder");
        assertThat(stylesheet).contains(".ili-sidebar");
        assertThat(stylesheet).contains(".ili-form-section");
        assertThat(stylesheet).contains("position: sticky");
        assertThat(stylesheet).contains("env(safe-area-inset-bottom)");
        assertThat(stylesheet).doesNotContain("#D3121B");
        assertThat(stylesheet).doesNotContain("#B80F17");
        assertThat(stylesheet).doesNotContain("#dc3545", "#fff1f1", "#ffffff", "#212529", "#")
            .contains("var(--bs-danger");
        assertThat(stylesheet).contains(".ili-field-help-panel");
        assertThat(stylesheet).contains(".ili-relationship-results");
        assertThat(stylesheet).contains(".ili-association-quick-form");
        assertThat(stylesheet).contains("prefers-reduced-motion");
        assertThat(stylesheet).contains("@media print");

        String navigationJs = Files.readString(projectDir.resolve("grails-app/assets/javascripts/ili-navigation.js"));
        assertThat(navigationJs).contains("ili2grails.ui.favorites");
        assertThat(navigationJs).contains("ili2grails.ui.recents");
        assertThat(navigationJs).contains("ArrowDown");
        assertThat(navigationJs).contains("localStorage");

        String actionIndexTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_list-table.gsp"));
        assertThat(actionIndexTemplate).contains("<ili:icon name=\"eye\"/>");
        assertThat(actionIndexTemplate).contains("<ili:icon name=\"pencil\"/>");
        assertThat(actionIndexTemplate).contains("<ili:icon name=\"trash\"/>");
        assertThat(actionIndexTemplate).doesNotContain("<svg");

        String actionShowTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/show.gsp"));
        assertThat(actionShowTemplate).doesNotContain("<svg");

        try (var paths = Files.walk(projectDir.resolve("grails-app"))) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".gsp") || path.toString().endsWith(".css"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        assertThat(content).as(path.toString()).doesNotContain("--dp-");
                        assertThat(content).as(path.toString()).doesNotContain("--ili-");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        }
    }

    @Test
    void managedManifestMatchesOverlayResourcesAndAssetRequiresAreUnique() throws Exception {
        URL overlayUrl = getClass().getClassLoader().getResource("grails/overlays/bootstrap-openlayers");
        assertThat(overlayUrl).isNotNull();
        Path overlayRoot = Path.of(overlayUrl.toURI());

        Set<String> resources;
        try (var paths = Files.walk(overlayRoot)) {
            resources = paths.filter(Files::isRegularFile)
                .map(overlayRoot::relativize)
                .map(Path::toString)
                .map(path -> path.replace(java.io.File.separatorChar, '/'))
                .collect(Collectors.toSet());
        }

        assertThat(resources).containsExactlyInAnyOrderElementsOf(
            new HashSet<>(GrailsTemplateOverlayInstaller.managedFilesForTesting()));
    }

    @Test
    void genericIconsAreCentralEmbeddedBootstrapIconsAndOverlayHasNoUnsafeLegacyMarkers() throws Exception {
        URL overlayUrl = getClass().getClassLoader().getResource("grails/overlays/bootstrap-openlayers");
        assertThat(overlayUrl).isNotNull();
        Path overlayRoot = Path.of(overlayUrl.toURI());
        Path tagLib = overlayRoot.resolve(
            "grails-app/taglib/ch/interlis/generator/grails/runtime/InterlisUiTagLib.groovy");
        String tagLibContent = Files.readString(tagLib);

        Set<String> declaredIcons = new HashSet<>();
        Matcher declarationMatcher = ICON_DECLARATION.matcher(tagLibContent);
        while (declarationMatcher.find()) {
            declaredIcons.add(declarationMatcher.group(1) != null
                ? declarationMatcher.group(1)
                : declarationMatcher.group(2));
        }
        assertThat(declaredIcons).contains("grid-3x3-gap");

        try (var paths = Files.walk(overlayRoot)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".gsp"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        Matcher useMatcher = ICON_USE.matcher(content);
                        while (useMatcher.find()) {
                            assertThat(declaredIcons)
                                .as("icon %s in %s", useMatcher.group(1), path)
                                .contains(useMatcher.group(1));
                        }
                        assertThat(content).as(path.toString()).doesNotContain("<svg", "raw(");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        }
        assertThat(tagLibContent).contains("<svg", "aria-hidden=\"true\"")
            .doesNotContain("icon-font", "cdn");

        try (var paths = Files.walk(overlayRoot)) {
            paths.filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        assertThat(content).as(path.toString()).doesNotContain("--dp-");
                        assertThat(content).as(path.toString())
                            .doesNotContain("bootstrap-icons.woff", "cdn.jsdelivr.net", "@font-face",
                                "springSecurity", "envers", "currentUser", "principal", "login", "logout",
                                "audit", "timeline", "restore", "Verlauf", "Protokoll");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        }
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

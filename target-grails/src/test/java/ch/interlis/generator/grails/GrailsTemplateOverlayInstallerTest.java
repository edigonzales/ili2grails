package ch.interlis.generator.grails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsTemplateOverlayInstallerTest {

    private static final List<String> NOTO_SANS_FONT_FILES = List.of(
        "NotoSans-Regular.woff2",
        "NotoSans-Medium.woff2",
        "NotoSans-SemiBold.woff2",
        "NotoSans-Bold.woff2",
        "NotoSans-Italic.woff2"
    );
    private static final List<String> FIRA_SANS_FONT_FILES = List.of(
        "FiraSans-Regular.woff2",
        "FiraSans-SemiBold.woff2"
    );
    private static final Pattern ICON_DECLARATION = Pattern.compile(
        "(?m)^\\s*(?:\\\"([^\\\"]+)\\\"|([A-Za-z0-9_-]+)): \\\'\\\'\\\'<path"
    );
    private static final Pattern ICON_USE = Pattern.compile("<ili:icon\\s+name=\\\"([A-Za-z0-9_-]+)\\\"");

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
        Path legacyBoldFont = projectDir.resolve(
            "grails-app/assets/fonts/fira-sans/FiraSans-Bold.woff2");
        Files.createDirectories(legacyBoldFont.getParent());
        Files.write(legacyBoldFont, new byte[] {0, 1, 2, 3});

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
        assertThat(Files.readString(projectDir.resolve("grails-app/conf/spring/resources.groovy")))
            .contains("FixedLocaleResolver", "Locale.forLanguageTag(\"de-CH\")");
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-geometry-editor.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-form-ux.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-navigation.js")).exists();
        assertThat(projectDir.resolve("grails-app/assets/stylesheets/ili-modern.css")).exists();
        NOTO_SANS_FONT_FILES.forEach(fileName -> assertThat(projectDir.resolve(
            "grails-app/assets/fonts/noto-sans/" + fileName)).exists());
        assertThat(projectDir.resolve("src/main/resources/fonts/noto-sans/OFL.txt")).exists();
        assertThat(projectDir.resolve("src/main/resources/fonts/fira-sans/OFL.txt")).exists();
        FIRA_SANS_FONT_FILES.forEach(fileName -> assertThat(projectDir.resolve(
            "grails-app/assets/fonts/fira-sans/" + fileName)).exists());
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-carbon-wc-bundle.js")).doesNotExist();
        assertThat(projectDir.resolve("grails-app/assets/javascripts/ili-carbon-input-bridge.js")).doesNotExist();
        assertThat(legacyBoldFont).doesNotExist();

        NOTO_SANS_FONT_FILES.forEach(fileName -> {
            try {
                byte[] content = Files.readAllBytes(projectDir.resolve(
                    "grails-app/assets/fonts/noto-sans/" + fileName));
                assertThat(content.length).as(fileName).isGreaterThan(4);
                assertThat(new String(content, 0, 4, StandardCharsets.US_ASCII))
                    .as(fileName).isEqualTo("wOF2");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        FIRA_SANS_FONT_FILES.forEach(fileName -> {
            try {
                byte[] content = Files.readAllBytes(projectDir.resolve(
                    "grails-app/assets/fonts/fira-sans/" + fileName));
                assertThat(content.length).as(fileName).isGreaterThan(4);
                assertThat(new String(content, 0, 4, StandardCharsets.US_ASCII))
                    .as(fileName).isEqualTo("wOF2");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

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
        assertThat(listFiltersTemplate)
            .contains("ili-search-input-group", "ili-search-icon", "ili-search-input",
                "ili2grails.list.filters", "aria-label=\"\\${message(code: 'ili2grails.list.search",
                "ili2grails.list.searchSubmit", "ili-filter-panel\" \\${activeFilterChips ? 'open' : ''}",
                "<span class=\"badge rounded-pill ili-active-filter-badge\">",
                "class=\"ili-active-filter-remove\"", "ili2grails.list.removeFilter",
                "aria-label=\"\\${message(code: 'ili2grails.list.removeFilter'",
                "title=\"\\${message(code: 'ili2grails.list.removeFilter'", "&times;")
            .doesNotContain("aria-labelledby=\"list-search-heading\"", "<label class=\"form-label\" for=\"list-search\">");
        assertThat(listFiltersTemplate)
            .doesNotContain("ili-active-filters-label", "code=\"ili2grails.list.active\"",
                "class=\"badge rounded-pill ili-active-filter-badge text-decoration-none\"");
        assertThat(listFiltersTemplate)
            .doesNotContain("input-group-lg", "form-select-lg", "btn-lg", "name=\"max\"");
        String listTableTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_list-table.gsp"));
        assertThat(listTableTemplate).contains("<table class=\"table");
        assertThat(listTableTemplate).contains("data-row-delete");
        assertThat(listTableTemplate).contains("sortUrls");
        String listPaginationTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_list-pagination.gsp"));
        assertThat(listPaginationTemplate)
            .contains("pagination.previousParams", "pagination.pageSizeParams", "data-ili-page-size-select",
                "page.ellipsis", "ili-pagination-page-number", "ili-pagination-ellipsis")
            .doesNotContain("form-select-lg", "btn-lg", "ili-list-result-summary ili-pagination-summary text-secondary");
        assertThat(indexTemplate)
            .contains("data-list-result-summary")
            .doesNotContain("default.list.label", "ili-list-result-summary text-secondary");

        String listHeaderTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_list-header.gsp"));
        assertThat(listHeaderTemplate)
            .contains("<ili:icon name=\"plus-lg\" cssClass=\"me-1\"/>", "ili2grails.action.new")
            .doesNotContain("default.new.label");
        String listEmptyTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_list-empty.gsp"));
        assertThat(listEmptyTemplate)
            .contains("!hasActiveListQuery", "ili2grails.list.noData",
                "<ili:icon name=\"plus-lg\" cssClass=\"me-1\"/>", "ili2grails.action.new")
            .doesNotContain("domainHasRecords", "ili2grails.list.noResults",
                "ili2grails.list.noResultsDescription", "ili2grails.list.reset", "default.new.label");
        String overlayCss = Files.readString(projectDir.resolve("grails-app/assets/stylesheets/ili-modern.css"));
        assertThat(overlayCss)
            .contains(".ili-list-result-summary", "font-size: 1rem;", "color: var(--bs-body-color);",
                ".ili-main-content", "background: transparent;", ".form-label", "margin-bottom: 0;",
                ".ili-search-input-group .ili-search-icon", "background-color: var(--ili-neutral-surface);",
                "border-right: 0", ".ili-search-input-group .ili-search-input", "border-left: 0",
                ".ili-domain-search-row", "gap: 1rem;",
                "--ili-card-shadow: 0 1px 3px rgba(var(--ili-neutral-emphasis-rgb), 0.08);",
                ".ili-list-tools + .ili-table-tile", "margin-top: 1.25rem;",
                ".ili-table-wrap .table > tbody > tr:last-child > *", "border-bottom: 0;",
                "box-shadow: var(--ili-card-shadow);",
                "grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr)",
                ".ili-icon-action:not(.ili-icon-action-danger):hover",
                "rgba(var(--bs-danger-rgb), 0.65)",
                ".ili-sidebar .offcanvas-header.ili-sidebar-header", "justify-content: flex-end",
                "padding: 0", ".ili-sidebar .ili-sidebar-close", "width: 2.25rem", "height: 2.25rem",
                "border: 0", "background: transparent", ".ili-sidebar .ili-sidebar-close:hover",
                ".ili-active-filter-badge", "font-size: 0.875rem;", "font-weight: 400;",
                "display: inline-flex", ".ili-active-filter-remove", ".ili-active-filter-remove:hover",
                ".ili-active-filter-remove:focus-visible")
            .doesNotContain(".ili-active-filters-label", ".ili-active-filter-badge:hover",
                ".ili-active-filter-badge:focus-visible");

        String sidebarTemplate = Files.readString(projectDir.resolve("grails-app/views/interlisUi/_sidebar.gsp"));
        assertThat(sidebarTemplate).doesNotContain("ili2grails.shell.navigation\" default=\"Navigation")
            .contains("class=\"ili-sidebar-close\"", "name=\"x-circle\"")
            .doesNotContain("btn btn-outline-secondary btn-sm ili-sidebar-close");
        String sidebarTagLib = Files.readString(projectDir.resolve(
            "grails-app/taglib/ch/interlis/generator/grails/runtime/InterlisUiTagLib.groovy"));
        assertThat(sidebarTagLib).contains("\"x-circle\"");

        String formTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/_form.gsp"));
        assertThat(formTemplate).contains("ili-split-layout");
        assertThat(formTemplate).contains("data-unsaved-badge");
        assertThat(formTemplate).contains("template=\"form-section\"");
        assertThat(formTemplate).contains("submitMode");
        assertThat(formTemplate).contains("saveAndContinue");
        assertThat(formTemplate).contains("fieldMeta");
        assertThat(formTemplate).contains("ili-validation-summary");
        assertThat(formTemplate)
            .contains("<ili:icon name=\"list\" cssClass=\"me-1\"/>", "ili2grails.action.list",
                "<ili:icon name=\"plus-lg\" cssClass=\"me-1\"/>", "ili2grails.action.new")
            .doesNotContain("default.list.label", "default.new.label");
        assertThat(formTemplate).doesNotContain("js-carbon-bridge");

        String createTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/create.gsp"));
        assertThat(createTemplate)
            .contains("ili2grails.form.createTitle", "pageTitleCode: 'ili2grails.form.createTitle'",
                "submitCode: 'ili2grails.action.save'", "submitDefault: 'Speichern'", "pageSubtitle: message(")
            .doesNotContain("default.create.label", "default.button.create.label", "pageSubtitle: \\${message");
        String deMessages = Files.readString(projectDir.resolve("grails-app/i18n/messages_de_CH.properties"));
        assertThat(deMessages)
            .contains("ili2grails.action.save=Speichern", "ili2grails.form.createTitle={0} erfassen",
                "ili2grails.list.removeFilter=Filter entfernen", "ili2grails.action.create=Erfassen")
            .doesNotContain("ili2grails.list.noResults=", "ili2grails.list.noResultsDescription=", "ili2grails.list.reset=",
                "ili2grails.list.active=");
        String editTemplate = Files.readString(projectDir.resolve("src/main/templates/scaffolding/edit.gsp"));
        assertThat(editTemplate)
            .contains("pageSubtitle: message(")
            .doesNotContain("pageSubtitle: \\${message");

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
        assertThat(layoutTemplate).contains("data-ili-neutral-palette=\"balanced\"");
        assertThat(layoutTemplate).contains("<asset:javascript src=\"application.js\"/>");
        assertThat(layoutTemplate).doesNotContain("https://cdn.jsdelivr.net");
        assertThat(layoutTemplate).contains("ili-sidebar-toggle");
        assertThat(layoutTemplate).doesNotContain("ili-carbon-wc-bundle.js");
        assertThat(layoutTemplate).doesNotContain("<bx-header");
        assertThat(layoutTemplate).contains("InterlisNavigationSupport.navigationModel");
        assertThat(layoutTemplate)
            .contains("breadcrumbAction", "breadcrumbRecordLabel", "ili2grails.action.create",
                "aria-current=\"page\"", "action=\"show\" id=\"${params.id}\"", "g:elseif")
            .doesNotContain("g:choose", "g:when", "g:otherwise");
        assertThat(layoutTemplate).contains("data-ili-domain-finder-form");
        assertThat(layoutTemplate)
            .contains("role=\"combobox\"", "aria-autocomplete=\"list\"", "ili-domain-search-row",
                "ili-search-input-group",
                "ili-search-icon", "ili-search-input", "btn btn-primary", "ili2grails.list.searchSubmit")
            .doesNotContain("<button class=\"btn btn-outline-secondary\" type=\"submit\"");
        assertThat(layoutTemplate).contains("data-ili-extension-point=\"topbar-toolbar\"");
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
        assertThat(uiDescriptorSupport)
            .contains("static Map<String, Object> descriptor", "displayFieldsFor", "list.displayFields");
        assertThat(uiDescriptorSupport).contains("Unknown field");
        assertThat(uiDescriptorSupport).contains("detailSections", "scalarDetailProperty");
        String relationshipDisplayOptions = Files.readString(projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisRelationshipOptions.groovy"));
        assertThat(relationshipDisplayOptions).contains("optionLabel(def grailsApplication, Object value)",
            "InterlisUiDescriptorSupport.displayFieldsFor");
        String workspaceSupport = Files.readString(projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisWorkspaceSupport.groovy"));
        assertThat(workspaceSupport).contains("workspaceDisplayLabel", "displayFields", "#${id}");
        String crudControllerSupport = Files.readString(projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisCrudControllerSupport.groovy"));
        assertThat(crudControllerSupport).contains(
            "InterlisWorkspaceSupport.renderValue(grailsApplication, value)",
            "InterlisRelationshipOptions.optionLabel(grailsApplication, selected)");
        String listQuerySupport = Files.readString(projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisListQuerySupport.groovy"));
        assertThat(listQuerySupport).contains("optionLabel(grailsApplication, selected)");
        String associationQueryService = Files.readString(projectDir.resolve(
            "grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationQueryService.groovy"));
        assertThat(associationQueryService).contains("optionLabel(grailsApplication, target)");
        String associationContextSupport = Files.readString(projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisAssociationContextSupport.groovy"));
        assertThat(associationContextSupport).contains("optionLabel(grailsApplication, owner)");

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
        int formActionsStart = stylesheet.indexOf(".ili-form-actions {");
        int formActionsEnd = stylesheet.indexOf('}', formActionsStart);
        assertThat(formActionsStart).isGreaterThanOrEqualTo(0);
        assertThat(formActionsEnd).isGreaterThan(formActionsStart);
        assertThat(stylesheet.substring(formActionsStart, formActionsEnd))
            .contains("position: sticky", "background: transparent;", "border-top: 0;",
                "box-shadow: none;", "backdrop-filter: none;")
            .doesNotContain("var(--ili-neutral-surface)", "var(--bs-border-color)",
                "var(--bs-box-shadow-sm)", "backdrop-filter: blur");
        assertThat(stylesheet).contains(
            "@font-face",
            "font-family: \"Fira Sans\"",
            "font-display: swap",
            "font-weight: 400",
            "font-weight: 600",
            "FiraSans-Regular.woff2",
            "FiraSans-SemiBold.woff2"
        );
        assertThat(stylesheet).doesNotContain(
            "Frutiger",
            "fonts.gstatic.com",
            "fonts.googleapis.com",
            "https://"
        );
        assertThat(stylesheet).contains(".ili-pagination-bar");
        assertThat(stylesheet).doesNotContain("--dp-");
        assertThat(stylesheet).contains("--ili-neutral-");
        assertThat(stylesheet).contains(".ili-domain-finder");
        assertThat(stylesheet).contains(".ili-sidebar");
        assertThat(stylesheet).contains(".ili-form-section");
        assertThat(stylesheet).contains("position: sticky");
        assertThat(stylesheet).contains("env(safe-area-inset-bottom)");
        assertThat(stylesheet).doesNotContain("#D3121B");
        assertThat(stylesheet).doesNotContain("#B80F17");
        assertThat(stylesheet).doesNotContain("#dc3545", "#fff1f1", "#212529")
            .contains("var(--bs-danger");
        assertThat(stylesheet).contains(".ili-field-help-panel");
        assertThat(stylesheet).contains(".ili-native-form-host .ili-form-field > .ili-native-grid");
        int nativeFormFieldStart = stylesheet.indexOf(".ili-native-form-host .ili-form-field > .ili-native-grid");
        int nativeFormFieldEnd = stylesheet.indexOf('}', nativeFormFieldStart);
        assertThat(stylesheet.substring(nativeFormFieldStart, nativeFormFieldEnd))
            .contains("gap: 0.25rem");
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
    void neutralPaletteIsCentralizedAndProvidesThreeContrastLevels() throws Exception {
        URL overlayUrl = getClass().getClassLoader().getResource("grails/overlays/bootstrap-openlayers");
        assertThat(overlayUrl).isNotNull();
        Path overlayRoot = Path.of(overlayUrl.toURI());
        String stylesheet = Files.readString(overlayRoot.resolve(
            "grails-app/assets/stylesheets/ili-modern.css"));
        String layout = Files.readString(overlayRoot.resolve(
            "grails-app/views/layouts/main.gsp"));
        String listHeader = Files.readString(overlayRoot.resolve(
            "src/main/templates/scaffolding/_list-header.gsp"));
        String listFilters = Files.readString(overlayRoot.resolve(
            "src/main/templates/scaffolding/_list-filters.gsp"));

        assertThat(stylesheet).contains(
            "--ili-neutral-ink",
            "--ili-neutral-muted",
            "--ili-neutral-border",
            "--ili-neutral-surface",
            "--ili-neutral-canvas",
            "--ili-neutral-header",
            "--ili-neutral-hover",
            ":root[data-ili-neutral-palette=\"quiet\"]",
            ":root[data-ili-neutral-palette=\"defined\"]",
            "--bs-table-hover-bg: var(--ili-neutral-hover)",
            "--bs-pagination-disabled-bg: var(--ili-neutral-header)",
            ".ili-active-filter-badge",
            "border: 1px solid var(--ili-neutral-border)",
            "background: var(--ili-neutral-header)",
            "background: var(--ili-neutral-hover)"
        );
        assertThat(layout).contains("data-ili-neutral-palette=\"balanced\"");
        assertThat(listHeader)
            .doesNotContain("ili-record-count-badge", "text-bg-secondary");
        assertThat(listFilters)
            .contains("<span class=\"badge rounded-pill ili-active-filter-badge\">",
                "class=\"ili-active-filter-remove\"", "ili2grails.list.removeFilter",
                "aria-label=\"\\${message(code: 'ili2grails.list.removeFilter'",
                "title=\"\\${message(code: 'ili2grails.list.removeFilter'", "&times;")
            .doesNotContain("text-bg-light", "ili-active-filters-label", "code=\"ili2grails.list.active\"",
                "class=\"badge rounded-pill ili-active-filter-badge text-decoration-none\"");
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
                .filter(path -> !path.toString().endsWith(".woff2"))
                .filter(path -> !path.toString().endsWith(".properties"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        assertThat(content).as(path.toString()).doesNotContain("--dp-");
                        assertThat(content).as(path.toString())
                            .doesNotContain("bootstrap-icons.woff", "cdn.jsdelivr.net",
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
        assertThat(projectDir.resolve("grails-app/assets/fonts/noto-sans")).doesNotExist();
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

    @Test
    void installsSelectedLanguageBundlesAndPreservesProjectMessages(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("my-grails-app");
        Path baseMessages = projectDir.resolve("grails-app/i18n/messages.properties");
        Files.createDirectories(baseMessages.getParent());
        Files.writeString(baseMessages, "custom.project.message=Keep me\n");

        GenerationConfig config = GenerationConfig.builder(projectDir, "com.example")
            .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
            .language(GenerationConfig.LANGUAGE_EN)
            .build();

        new GrailsTemplateOverlayInstaller().install(projectDir, config);

        assertThat(projectDir.resolve("grails-app/i18n/messages_en.properties")).exists();
        assertThat(projectDir.resolve("grails-app/i18n/messages_de_CH.properties")).doesNotExist();
        String merged = Files.readString(baseMessages);
        assertThat(merged).contains("custom.project.message=Keep me");
        assertThat(merged).contains("ili2grails.pagination.pageSize=Rows per page");
        assertThat(merged).contains("ili2grails.list.searchPlaceholder=Search for {0} ...");
        assertThat(merged).contains("ili2grails.action.save=Save", "ili2grails.form.createTitle=Create {0}",
            "ili2grails.list.removeFilter=Remove filter", "ili2grails.action.create=Create");
        assertThat(merged).doesNotContain("ili2grails.list.noResults=", "ili2grails.list.noResultsDescription=",
            "ili2grails.list.reset=", "ili2grails.list.active=");
        assertThat(merged).doesNotContain("ili2grails.pagination.pageSize=Zeilen pro Seite");
        assertThat(Files.readString(projectDir.resolve("grails-app/conf/spring/resources.groovy")))
            .contains("Locale.forLanguageTag(\"en\")");
    }
}

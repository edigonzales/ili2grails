package ch.interlis.generator.grails;

import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsBrowserE2eTest {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(8);
    private static final Duration BOOT_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration DB_TIMEOUT = Duration.ofSeconds(90);
    private static final String DEFAULT_JDBC_URL = "jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret";
    private static final Path MODEL_FILE = Path.of("test-models/SimpleAddressModel.ili");
    private static final Path LIST_QUERY_MODEL_FILE = Path.of("test-models/ListQueryE2E.ili");
    private static final Path ASSOCIATION_MODEL_FILE = Path.of("test-models/QuickLinkE2E.ili");
    private static final Path CONTEXTUAL_ASSOC_MODEL_FILE = Path.of("test-models/ContextualAssociationE2E.ili");
    private static final Path WORKSPACE_MODEL_FILE = Path.of("test-models/MultiDomainWorkspaceE2E.ili");
    private static final Path GETTING_STARTED_MODEL_FILE =
        Path.of("docs/getting-started/models/GsSimpleModel.ili");
    private static final Path GETTING_STARTED_DATA_FILE =
        Path.of("docs/getting-started/data/GsSimpleModel.xtf");
    private static final Path SCREENSHOT_DIR = Path.of("build/e2e-screenshots");
    private static final List<String> MODEL_REPOSITORIES = List.of(
        "test-models",
        "https://models.interlis.ch/",
        "https://models.geo.admin.ch/"
    );
    private static final String BASE_PACKAGE = "com.example";
    private static final String DOMAIN_PACKAGE = "com.example.domain";
    private static final String ENUM_PACKAGE = "com.example.enums";

    @TempDir
    Path tempDir;

    @BeforeAll
    static void requireTools() throws Exception {
        if (!isCommandAvailable(List.of("grails", "--version"))) {
            throw new TestAbortedException("grails CLI not available in PATH; skipping browser E2E test");
        }
        if (!isCommandAvailable(List.of("docker", "compose", "version"))) {
            throw new TestAbortedException("docker compose not available; skipping browser E2E test");
        }
        Path ili2pgHome = ili2pgHome();
        if (!Files.exists(ili2pgHome.resolve("ili2pg-5.5.1.jar"))
            || !Files.isDirectory(ili2pgHome.resolve("libs"))) {
            throw new TestAbortedException("ili2pg home not available: " + ili2pgHome);
        }
    }

    @Test
    void generatedGrailsAppSupportsCrudRelationshipsAndGeometryInBrowser() throws Exception {
        String externalAppUrl = externalAppUrl();
        if (externalAppUrl != null) {
            waitForHttp(externalAppUrl);
            runBrowserCrud(externalAppUrl);
            return;
        }
        if (!Files.exists(MODEL_FILE)) {
            throw new TestAbortedException("Model file not available: " + MODEL_FILE);
        }
        startComposeDb();
        waitForDatabase();

        String schemaName = uniqueSchemaName("e2e_simple_");
        Path appDir = null;
        Process bootRun = null;
        try {
            dropSchema(schemaName);
            importSchema(schemaName);
            ModelMetadata metadata = readMetadata(schemaName);
            appDir = createGrailsApp();
            GenerationConfig config = GenerationConfig.builder(appDir, BASE_PACKAGE)
                .domainPackage(DOMAIN_PACKAGE)
                .controllerPackage(BASE_PACKAGE)
                .enumPackage(ENUM_PACKAGE)
                .jdbcUrl(baseJdbcUrl())
                .schema(schemaName)
                .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
                .mapEditor(GenerationConfig.MAP_EDITOR_OPENLAYERS)
                .geometryEnabled(true)
                .build();

            new GrailsTemplateOverlayInstaller().install(appDir, config);
            new GrailsCrudGenerator().generate(metadata, config);
            generateScaffolding(appDir, metadata, config);

            int port = freePort();
            bootRun = startGrailsApp(appDir, port);
            waitForHttp("http://localhost:" + port + "/interlisUi/index");

            runBrowserCrud("http://localhost:" + port);
        } finally {
            if (bootRun != null) {
                bootRun.destroy();
                if (!bootRun.waitFor(10, TimeUnit.SECONDS)) {
                    bootRun.destroyForcibly();
                    bootRun.waitFor();
                }
            }
            dropSchema(schemaName);
        }
    }

    @Test
    void generatedGrailsAppSupportsQuickLinkAndAssociationDeleteInBrowser() throws Exception {
        String externalAppUrl = externalAppUrl();
        if (externalAppUrl != null) {
            waitForHttp(externalAppUrl);
            runAssociationQuickLinkE2E(externalAppUrl);
            return;
        }
        if (!Files.exists(ASSOCIATION_MODEL_FILE)) {
            throw new TestAbortedException("AssociationCases.ili not available for browser E2E");
        }
        startComposeDb();
        waitForDatabase();

        String schemaName = uniqueSchemaName("e2e_assoc_");
        Path appDir = null;
        Process bootRun = null;
        try {
            dropSchema(schemaName);
            importAssociationSchema(schemaName);
            ModelMetadata metadata = readAssociationMetadata(schemaName);
            appDir = createGrailsApp();
            GenerationConfig config = GenerationConfig.builder(appDir, BASE_PACKAGE)
                .domainPackage(DOMAIN_PACKAGE)
                .controllerPackage(BASE_PACKAGE)
                .enumPackage(ENUM_PACKAGE)
                .jdbcUrl(baseJdbcUrl())
                .schema(schemaName)
                .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
                .mapEditor(GenerationConfig.MAP_EDITOR_NONE)
                .geometryEnabled(false)
                .build();

            new GrailsTemplateOverlayInstaller().install(appDir, config);
            new GrailsCrudGenerator().generate(metadata, config);
            generateScaffolding(appDir, metadata, config);

            int port = freePort();
            bootRun = startGrailsApp(appDir, port);
            waitForHttp("http://localhost:" + port + "/interlisUi/index");

            runAssociationQuickLinkE2E("http://localhost:" + port);
        } finally {
            if (bootRun != null) {
                bootRun.destroy();
                if (!bootRun.waitFor(10, TimeUnit.SECONDS)) {
                    bootRun.destroyForcibly();
                    bootRun.waitFor();
                }
            }
            dropSchema(schemaName);
        }
    }

    @Test
    void generatedGrailsAppSupportsContextualAssociationFormAndSelfAndNaryInBrowser() throws Exception {
        String externalAppUrl = externalAppUrl();
        if (externalAppUrl != null) {
            waitForHttp(externalAppUrl);
            runContextualAssociationE2E(externalAppUrl);
            return;
        }
        if (!Files.exists(CONTEXTUAL_ASSOC_MODEL_FILE)) {
            throw new TestAbortedException("ContextualAssociationE2E.ili not available for browser E2E");
        }
        startComposeDb();
        waitForDatabase();

        String schemaName = uniqueSchemaName("e2e_ctxassoc_");
        Path appDir = null;
        Process bootRun = null;
        try {
            dropSchema(schemaName);
            importContextualAssociationSchema(schemaName);
            ModelMetadata metadata = readContextualAssociationMetadata(schemaName);
            appDir = createGrailsApp();
            GenerationConfig config = GenerationConfig.builder(appDir, BASE_PACKAGE)
                .domainPackage(DOMAIN_PACKAGE)
                .controllerPackage(BASE_PACKAGE)
                .enumPackage(ENUM_PACKAGE)
                .jdbcUrl(baseJdbcUrl())
                .schema(schemaName)
                .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
                .mapEditor(GenerationConfig.MAP_EDITOR_NONE)
                .geometryEnabled(false)
                .build();

            new GrailsTemplateOverlayInstaller().install(appDir, config);
            new GrailsCrudGenerator().generate(metadata, config);
            generateScaffolding(appDir, metadata, config);

            int port = freePort();
            bootRun = startGrailsApp(appDir, port);
            waitForHttp("http://localhost:" + port + "/interlisUi/index");

            runContextualAssociationE2E("http://localhost:" + port);
        } finally {
            if (bootRun != null) {
                bootRun.destroy();
                if (!bootRun.waitFor(10, TimeUnit.SECONDS)) {
                    bootRun.destroyForcibly();
                    bootRun.waitFor();
                }
            }
            dropSchema(schemaName);
        }
    }

    @Test
    void generatedGrailsAppSupportsListSearchFiltersSortPagingAndEmptyStatesInBrowser() throws Exception {
        if (!Files.exists(LIST_QUERY_MODEL_FILE)) {
            throw new TestAbortedException("ListQueryE2E.ili not available for browser E2E");
        }
        startComposeDb();
        waitForDatabase();

        String schemaName = uniqueSchemaName("e2e_list_");
        Process bootRun = null;
        try {
            dropSchema(schemaName);
            importListQuerySchema(schemaName);
            ModelMetadata metadata = readListQueryMetadata(schemaName);
            Path appDir = createGrailsApp();
            GenerationConfig config = GenerationConfig.builder(appDir, BASE_PACKAGE)
                .domainPackage(DOMAIN_PACKAGE)
                .controllerPackage(BASE_PACKAGE)
                .enumPackage(ENUM_PACKAGE)
                .jdbcUrl(baseJdbcUrl())
                .schema(schemaName)
                .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
                .mapEditor(GenerationConfig.MAP_EDITOR_NONE)
                .geometryEnabled(false)
                .build();

            new GrailsTemplateOverlayInstaller().install(appDir, config);
            new GrailsCrudGenerator().generate(metadata, config);
            configureListFormSections(appDir);
            augmentListRecordFieldMetadata(appDir);
            generateScaffolding(appDir, metadata, config);

            int port = freePort();
            bootRun = startGrailsApp(appDir, port);
            String baseUrl = "http://localhost:" + port;
            waitForGrailsApp(bootRun, appDir, baseUrl + "/interlisUi/index");
            runListQueryE2E(baseUrl);
        } finally {
            if (bootRun != null) {
                bootRun.destroy();
                if (!bootRun.waitFor(10, TimeUnit.SECONDS)) {
                    bootRun.destroyForcibly();
                    bootRun.waitFor();
                }
            }
            dropSchema(schemaName);
        }
    }

    @Test
    void generatedGrailsAppSupportsConfiguredMultiDomainWorkspaceInBrowser() throws Exception {
        if (externalAppUrl() != null) {
            throw new TestAbortedException("MultiDomainWorkspaceE2E requires the generated fixture app");
        }
        if (!Files.exists(WORKSPACE_MODEL_FILE)) {
            throw new TestAbortedException("MultiDomainWorkspaceE2E.ili not available for browser E2E");
        }
        startComposeDb();
        waitForDatabase();

        String schemaName = uniqueSchemaName("e2e_workspace_");
        Process bootRun = null;
        try {
            dropSchema(schemaName);
            importWorkspaceSchema(schemaName);
            ModelMetadata metadata = readWorkspaceMetadata(schemaName);
            addWorkspaceVersionColumns(schemaName, metadata);
            Path appDir = createGrailsApp();
            GenerationConfig config = GenerationConfig.builder(appDir, BASE_PACKAGE)
                .domainPackage(DOMAIN_PACKAGE)
                .controllerPackage(BASE_PACKAGE)
                .enumPackage(ENUM_PACKAGE)
                .jdbcUrl(baseJdbcUrl())
                .schema(schemaName)
                .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
                .mapEditor(GenerationConfig.MAP_EDITOR_NONE)
                .geometryEnabled(false)
                .build();

            new GrailsTemplateOverlayInstaller().install(appDir, config);
            new GrailsCrudGenerator().generate(metadata, config);
            MultiDomainWorkspaceFixture.install(appDir);
            generateScaffolding(appDir, metadata, config);

            int port = freePort();
            bootRun = startGrailsApp(appDir, port);
            String baseUrl = "http://localhost:" + port;
            waitForHttp(baseUrl + "/interlisUi/index");
            runMultiDomainWorkspaceE2E(baseUrl);
        } finally {
            if (bootRun != null) {
                bootRun.destroy();
                if (!bootRun.waitFor(10, TimeUnit.SECONDS)) {
                    bootRun.destroyForcibly();
                    bootRun.waitFor();
                }
            }
            dropSchema(schemaName);
        }
    }

    @Test
    void generatedGettingStartedAppAssignsExistingEmployeeThroughInverseRelationship() throws Exception {
        if (externalAppUrl() != null) {
            throw new TestAbortedException(
                "Getting-Started inverse-relationship E2E requires the generated fixture app"
            );
        }
        if (!Files.exists(GETTING_STARTED_MODEL_FILE) || !Files.exists(GETTING_STARTED_DATA_FILE)) {
            throw new TestAbortedException("Getting-Started model or data file is not available");
        }
        startComposeDb();
        waitForDatabase();

        String schemaName = uniqueSchemaName("e2e_gs_inverse_");
        Process bootRun = null;
        try {
            dropSchema(schemaName);
            importGettingStartedSchemaAndData(schemaName);
            assertGettingStartedPhysicalModel(schemaName);
            ModelMetadata metadata = readGettingStartedMetadata(schemaName);
            Path appDir = createGrailsApp();
            GenerationConfig config = GenerationConfig.builder(appDir, BASE_PACKAGE)
                .domainPackage(DOMAIN_PACKAGE)
                .controllerPackage(BASE_PACKAGE)
                .enumPackage(ENUM_PACKAGE)
                .jdbcUrl(baseJdbcUrl())
                .schema(schemaName)
                .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
                .mapEditor(GenerationConfig.MAP_EDITOR_NONE)
                .geometryEnabled(false)
                .build();

            new GrailsTemplateOverlayInstaller().install(appDir, config);
            new GrailsCrudGenerator().generate(metadata, config);
            assertThat(Files.readString(appDir.resolve(
                "grails-app/domain/com/example/domain/Department.groovy"
            ))).contains("interlisInverseRelationshipMeta", "employees:");
            generateScaffolding(appDir, metadata, config);

            int port = freePort();
            bootRun = startGrailsApp(appDir, port);
            String baseUrl = "http://localhost:" + port;
            waitForHttp(baseUrl + "/interlisUi/index");
            try {
                runGettingStartedInverseRelationshipE2E(baseUrl, schemaName);
            } catch (AssertionError | RuntimeException failure) {
                Path logFile = appDir.resolve("build/browser-e2e.log");
                String runtimeLog = Files.exists(logFile)
                    ? Files.readString(logFile)
                    : "(browser-e2e.log fehlt)";
                throw new AssertionError(
                    failure.getMessage() + "\nGenerated app log:\n" + runtimeLog,
                    failure
                );
            }
        } finally {
            if (bootRun != null) {
                bootRun.destroy();
                if (!bootRun.waitFor(10, TimeUnit.SECONDS)) {
                    bootRun.destroyForcibly();
                    bootRun.waitFor();
                }
            }
            dropSchema(schemaName);
        }
    }

    private void runGettingStartedInverseRelationshipE2E(String baseUrl, String schemaName) {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            List<String> pageErrors = new ArrayList<>();
            List<String> consoleMessages = new ArrayList<>();
            List<String> relationshipRequests = new ArrayList<>();
            page.onPageError(pageErrors::add);
            page.onConsoleMessage(message ->
                consoleMessages.add(message.type() + ": " + message.text())
            );
            page.onResponse(response -> {
                if (response.url().contains("relationshipCollection")
                    || response.url().contains("relationshipAssign")) {
                    relationshipRequests.add(response.status() + " " + response.url());
                }
            });
            page.onRequestFailed(request -> {
                if (request.url().contains("relationshipCollection")
                    || request.url().contains("relationshipAssign")) {
                    relationshipRequests.add("FAILED " + request.url());
                }
            });
            String planningId = findGettingStartedDepartment(page, baseUrl, "Planning");
            page.navigate(baseUrl + "/department/show/" + planningId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            Locator planningSection = page.locator(
                "[data-inverse-relationship-section][data-relationship-name='employees']"
            );
            assertThat(planningSection.locator("[data-inverse-total-value]").getAttribute("data-inverse-total-value"))
                .isEqualTo("400");
            assertThat(planningSection.locator("[data-inverse-total]").textContent().trim())
                .isNotEqualTo("400");
            assertThat(planningSection.locator("[data-inverse-relationship-rows] tr").count())
                .isEqualTo(10);
            assertThat(planningSection.locator(".ili-inverse-relationship-header")
                .evaluate("element => getComputedStyle(element).borderBottomWidth"))
                .isEqualTo("0px");
            assertThat(page.locator(".ili-definition-row dt").first()
                .evaluate("element => getComputedStyle(element).fontWeight"))
                .isEqualTo("400");
            screenshot(page, "getting-started-inverse-preview", true);

            assertThat(planningSection.locator("[data-inverse-browser]").count()).isZero();
            assertThat(planningSection.locator("thead th").count()).isGreaterThan(1);
            Locator inlineNext = planningSection.locator(".ili-pagination-controls a").last();
            assertThat(inlineNext.textContent()).contains("Weiter");
            inlineNext.click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            planningSection = page.locator(
                "[data-inverse-relationship-section][data-relationship-name='employees']"
            );
            assertThat(planningSection.locator("[data-inverse-relationship-rows] tr").count())
                .isEqualTo(10);
            assertThat(page.url()).contains("inverse.employees.offset=10");
            screenshot(page, "getting-started-inverse-inline-page-2", true);

            Locator inlineSearch = planningSection.locator("input[name='inverse.employees.q']");
            inlineSearch.fill("Employee 399");
            inlineSearch.press("Enter");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            planningSection = page.locator(
                "[data-inverse-relationship-section][data-relationship-name='employees']"
            );
            assertThat(planningSection.locator("[data-inverse-relationship-rows] tr").count()).isEqualTo(1);
            assertThat(planningSection.locator("[data-inverse-relationship-rows]").textContent())
                .contains("demo.employee399@example.com");
            inlineSearch = planningSection.locator("input[name='inverse.employees.q']");
            inlineSearch.fill("does-not-exist");
            inlineSearch.press("Enter");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            planningSection = page.locator(
                "[data-inverse-relationship-section][data-relationship-name='employees']"
            );
            assertThat(planningSection.locator("[data-inverse-relationship-rows] tr").count()).isZero();
            assertThat(planningSection.locator("[data-inverse-empty]").isVisible()).isTrue();
            screenshot(page, "getting-started-inverse-inline-empty", true);

            page.navigate(baseUrl + "/department/show/" + planningId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            planningSection = page.locator(
                "[data-inverse-relationship-section][data-relationship-name='employees']"
            );

            Locator planningSearch = planningSection.locator("[data-relationship-collection='employees']");
            planningSearch.fill("Clara");
            page.waitForTimeout(1000);
            Locator planningResults = planningSection.locator("[data-relationship-list]");
            assertThat(planningResults.isVisible())
                .as("Planning autocomplete; html=" + planningResults.innerHTML()
                    + "; requests=" + relationshipRequests
                    + "; pageErrors=" + pageErrors
                    + "; console=" + consoleMessages
                    + "; section=" + planningSection.innerText())
                .isTrue();
            planningSearch.focus();
            page.keyboard().press("Escape");
            assertThat(planningResults.isVisible()).isFalse();
            planningSearch.fill("Clara");
            page.waitForTimeout(1000);
            assertThat(planningResults.isVisible()).isTrue();
            page.locator("h1").click();
            assertThat(planningResults.isVisible()).isFalse();
            screenshot(page, "getting-started-inverse-combobox", true);

            String hrId = createGettingStartedDepartment(page, baseUrl, "HR");
            String itId = createGettingStartedDepartment(page, baseUrl, "IT");
            String employeeId = createGettingStartedEmployee(page, baseUrl, "Anna", "Keller", hrId);

            page.navigate(baseUrl + "/department/show/" + itId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            Locator section = page.locator(
                "[data-inverse-relationship-section][data-relationship-name='employees']"
            );
            assertThat(section.count())
                .as("inverse section on Department show:\n" + page.locator("body").innerText())
                .isEqualTo(1);
            assertThat(section.locator("[data-inverse-total-value]")
                .getAttribute("data-inverse-total-value")).isEqualTo("0");
            assertThat(section.locator("[data-inverse-relationship-form]").count()).isEqualTo(1);
            assertThat(section.locator("[data-inverse-relationship-form]").getAttribute("action"))
                .endsWith("/department/relationshipAssign/" + itId);
            assertThat(section.locator("[data-inverse-browser]").count()).isZero();
            assertThat(section.locator("select[name='targetId']").getAttribute("class"))
                .contains("visually-hidden");

            APIResponse optionResponse = page.request().get(
                baseUrl + "/department/relationshipCollectionOptions/" + itId
                    + "?relationship=employees&q=Anna&max=25&offset=0"
            );
            String optionBody = optionResponse.text();
            assertThat(optionResponse.status())
                .as("inverse option response: " + optionBody)
                .isEqualTo(200);
            assertThat(optionBody).contains("Anna", "HR");

            Locator search = section.locator("[data-relationship-collection='employees']");
            search.fill("Anna");
            page.waitForTimeout(1000);
            Locator resultList = section.locator("[data-relationship-list]");
            assertThat(resultList.innerHTML())
                .as("inverse autocomplete DOM; pageErrors=" + pageErrors
                    + "; console=" + consoleMessages
                    + "; relationshipRequests=" + relationshipRequests
                    + "; url=" + search.getAttribute("data-relationship-url")
                    + "; select=" + section.locator("select[name='targetId']").innerHTML())
                .contains("Anna");
            Locator option = section.locator("[data-relationship-value]")
                .filter(new Locator.FilterOptions().setHasText("HR"))
                .first();
            option.waitFor();
            assertThat(option.textContent()).contains("Anna", "HR");
            option.click();
            section.locator("[data-inverse-assign-submit]").click();

            Locator modal = section.locator("[data-inverse-reassignment-modal]");
            page.waitForTimeout(1000);
            assertThat(modal.isVisible())
                .as("reassignment modal; requests=" + relationshipRequests
                    + "; pageErrors=" + pageErrors
                    + "; error=" + section.locator("[data-inverse-relationship-error]").textContent())
                .isTrue();
            assertThat(relationshipRequests)
                .as("unconfirmed reassignment must be rejected before the dialog is shown")
                .anyMatch(request ->
                    request.startsWith("409 ")
                        && request.contains("/department/relationshipAssign/" + itId)
                );
            assertThat(modal.locator("[data-inverse-reassignment-text]").textContent())
                .contains("Anna", "HR", "IT");
            modal.locator("[data-bs-dismiss='modal']").last().click();
            modal.waitFor(new Locator.WaitForOptions().setState(
                com.microsoft.playwright.options.WaitForSelectorState.HIDDEN
            ));
            assertThat(employeeDepartmentId(schemaName, employeeId)).isEqualTo(hrId);

            section.locator("[data-inverse-assign-submit]").click();
            modal.waitFor();
            modal.locator("[data-inverse-reassignment-confirm]").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            Locator assignedRow = page.locator(
                "[data-inverse-relationship-section][data-relationship-name='employees'] "
                    + "[data-inverse-related-id='" + employeeId + "']"
            );
            assignedRow.waitFor();

            assertThat(employeeDepartmentId(schemaName, employeeId)).isEqualTo(itId);
            assertThat(assignedRow.textContent()).contains("Anna");

            APIResponse filteredCollection = page.request().get(
                baseUrl + "/department/relationshipCollectionPage/" + itId
                    + "?relationship=employees&q=Anna&max=25&offset=0"
            );
            assertThat(filteredCollection.status()).isEqualTo(200);
            assertThat(filteredCollection.text()).contains("Anna");

            page.navigate(baseUrl + "/department/show/" + hrId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator("[data-inverse-related-id='" + employeeId + "']").count()).isZero();

            page.navigate(baseUrl + "/employee/show/" + employeeId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator("[data-workspace-details]").textContent()).contains("IT");

            APIResponse invalid = page.request().post(
                baseUrl + "/department/relationshipAssign/" + itId
                    + "?relationship=unknown&targetId=" + employeeId
                    + "&confirmReassignment=false&format=json"
            );
            assertThat(invalid.status()).isEqualTo(400);
            assertThat(invalid.text()).contains("RELATIONSHIP_INVALID");
        } catch (PlaywrightException e) {
            if (e.getMessage() != null && e.getMessage().contains("Executable doesn't exist")) {
                throw new TestAbortedException("Playwright Chromium browser is not installed", e);
            }
            throw e;
        }
    }

    private String findGettingStartedDepartment(Page page, String baseUrl, String name) {
        page.navigate(baseUrl + "/department/index");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        Locator link = page.locator("a[href*='/department/show/']")
            .filter(new Locator.FilterOptions().setHasText(name))
            .first();
        assertThat(link.count())
            .as("Getting-Started Department " + name + " on " + page.url())
            .isEqualTo(1);
        return showId(link.getAttribute("href"));
    }

    private String createGettingStartedDepartment(Page page, String baseUrl, String name) {
        page.navigate(baseUrl + "/department/create");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("input[name='aname']").fill(name);
        selectFirstRelationshipOptions(page);
        submitForm(page);
        assertThat(page.url()).contains("/department/show/");
        return showId(page.url());
    }

    private String createGettingStartedEmployee(Page page,
                                                String baseUrl,
                                                String firstName,
                                                String lastName,
                                                String departmentId) {
        page.navigate(baseUrl + "/employee/create");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        Locator formSections = page.locator("[data-form-section]");
        assertThat(formSections.count()).isEqualTo(1);
        assertThat(formSections.nth(0).getAttribute("data-form-section")).isEqualTo("Basisdaten");
        page.locator("input[name='firstname']").fill(firstName);
        page.locator("input[name='lastname']").fill(lastName);
        page.locator("input[name='email']").fill(
            firstName.toLowerCase(java.util.Locale.ROOT) + "@example.test"
        );
        page.locator("select[name='department.id']").selectOption(departmentId);
        submitForm(page);
        assertThat(page.url()).contains("/employee/show/");
        return showId(page.url());
    }

    private void runContextualAssociationE2E(String baseUrl) {
        try {
            Files.createDirectories(SCREENSHOT_DIR);
        } catch (IOException ignored) {
        }
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();

            String personId = createRecord(page, baseUrl, "person");
            String docId = createRecord(page, baseUrl, "document");
            String parcelId = createRecord(page, baseUrl, "parcel");

            // Verify Person show renders association sections
            page.navigate(baseUrl + "/person/show/" + personId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            screenshot(page, "01-person-show");
            long sections = page.locator(".ili-association-section").count();
            System.out.println("Person show sections: " + sections);
            assertThat(sections).isGreaterThan(0);

            // Beteiligung contextual create
            String ctxUrl = baseUrl + "/beteiligung/create?associationContext="
                + urlEncode("ContextualAssociationE2E.Data.Beteiligung::PersonRole")
                + "&associationOwnerId=" + personId;
            page.navigate(ctxUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            screenshot(page, "02-beteiligung-create");
            selectFirstRelationshipOptions(page);
            fillVisibleControls(page, "Ctx");
            screenshot(page, "03-beteiligung-form");
            trySubmitOrVisit(page, baseUrl + "/person/show/" + personId);
            screenshot(page, "04-after-beteiligung");

            // TernaryAssoc contextual create
            String ternaryUrl = baseUrl + "/ternaryAssoc/create?associationContext="
                + urlEncode("ContextualAssociationE2E.Data.TernaryAssoc::TPersonRole")
                + "&associationOwnerId=" + personId;
            page.navigate(ternaryUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            screenshot(page, "05-ternary-create");
            selectFirstRelationshipOptions(page);
            fillVisibleControls(page, "Nary");
            screenshot(page, "06-ternary-form");
            trySubmitOrVisit(page, baseUrl + "/person/show/" + personId);
            screenshot(page, "07-after-ternary");

            System.out.println("Phase 5 E2E: all contextual association tests completed.");
        } catch (PlaywrightException e) {
            if (e.getMessage() != null && e.getMessage().contains("Executable doesn't exist")) {
                throw new TestAbortedException("Playwright Chromium browser is not installed", e);
            }
            throw e;
        }
    }

    private void runListQueryE2E(String baseUrl) {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            String bernId = createListMunicipality(page, baseUrl, "Bern");
            String zurichId = createListMunicipality(page, baseUrl, "Zürich");
            String bernRecordId = createListRecord(page, baseUrl, "Bahnhof Bern", "ACTIVE", true, "2024", "2024-01-01", bernId);
            createListRecord(page, baseUrl, "Archiv Zürich", "ARCHIVED", false, "2020", "2020-01-01", zurichId);
            createListRecord(page, baseUrl, "Entwurf Bern", "DRAFT", false, "2025", "2025-02-01", bernId);
            runFormUxE2E(page, baseUrl, bernId);

            page.navigate(baseUrl + "/record/show/" + bernRecordId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            screenshot(page, "workspace-record-show", true);
            screenshot(page, "phase7-mockup-03-workspace", true);
            assertWorkspaceShow(page, "Bahnhof Bern", bernId);

            page.navigate(baseUrl + "/record/index");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator("#list-search").count()).isEqualTo(1);
            assertThat(page.locator(".ili-table-tile tbody tr").count()).isEqualTo(4);
            assertThat(page.locator(".ili-pagination-bar").count()).isEqualTo(1);
            screenshot(page, "phase7-mockup-02-list", true);
            assertNoHorizontalOverflow(page);

            page.navigate(baseUrl + "/record/index?q=Bahnhof");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator(".ili-table-tile").textContent()).contains("Bahnhof Bern");
            assertThat(page.locator(".ili-table-tile").textContent()).doesNotContain("Archiv Zürich");
            assertThat(page.locator(".ili-active-filters").textContent()).contains("Suche");

            page.navigate(baseUrl + "/record/index?filter.astatus=active");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator(".ili-table-tile").textContent()).contains("Bahnhof Bern");
            assertThat(page.locator(".ili-active-filters").textContent()).containsIgnoringCase("active");

            page.navigate(baseUrl + "/record/index?filter.aactive=false&filter.ayear.min=2021&filter.ayear.max=2025");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator(".ili-table-tile").textContent()).contains("Entwurf Bern");
            assertThat(page.locator(".ili-table-tile").textContent()).doesNotContain("Bahnhof Bern");

            page.navigate(baseUrl + "/record/index?filter.validfrom.from=2024-01-01&filter.validfrom.to=2024-12-31");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator(".ili-table-tile").textContent()).contains("Bahnhof Bern");

            page.navigate(baseUrl + "/record/index?filter.municipalityrole=" + bernId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator(".ili-table-tile").textContent()).contains("Bahnhof Bern", "Entwurf Bern");
            assertThat(page.locator(".ili-table-tile").textContent()).doesNotContain("Archiv Zürich");

            page.navigate(baseUrl + "/record/index?sort=ayear&order=desc&max=1&offset=1&filter.aactive=false");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.url()).contains("filter.aactive=false");
            assertThat(page.locator(".ili-pagination-bar").count()).isEqualTo(1);
            assertThat(page.locator(".ili-sort-link").count()).isGreaterThan(0);

            page.navigate(baseUrl + "/record/index?filter.unknown=ignored&sort=unsafe");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator("[data-list-query-warning]").count()).isEqualTo(1);
            assertThat(page.locator("[data-list-query-warning]").textContent()).contains("Sortierung");

            page.navigate(baseUrl + "/record/index?q=does-not-exist");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            Locator emptyState = page.locator("[data-list-empty-state]");
            assertThat(emptyState.count())
                .as("list empty state at " + page.url() + ":\n" + page.locator("body").innerText())
                .isEqualTo(1);
            assertThat(emptyState.textContent()).contains("Keine Treffer");

            page.navigate(baseUrl + "/municipality/show/" + bernId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            Locator municipalityDelete = page.locator(
                "[data-domain-workspace-header] [data-delete-open]");
            assertThat(municipalityDelete.isVisible()).isTrue();
            municipalityDelete.click();
            page.waitForTimeout(250);
            assertThat(page.locator("[data-delete-modal]").isVisible()).isTrue();
            assertThat(page.locator("[data-delete-modal]").textContent())
                .contains("Bern", "dauerhaft gelöscht", "serverseitig geprüft",
                    "referenzieller", "Endgültig löschen");
            page.waitForFunction(
                "() => document.activeElement && document.activeElement.matches('[data-delete-cancel]')");
            screenshot(page, "workspace-delete-dialog", true);
            page.evaluate("""
                () => {
                  const form = document.querySelector('.ili-hidden-delete-form');
                  if (!form) {
                    throw new Error('No delete form found');
                  }
                  const submit = form.querySelector('.js-delete-submit');
                  if (typeof form.requestSubmit === 'function') {
                    form.requestSubmit(submit || undefined);
                  } else if (submit) {
                    submit.click();
                  } else {
                    form.submit();
                  }
                }
                """);
            page.waitForTimeout(1000);
            // Flash-Meldung der Delete-Integritätsprüfung (ili2grails.runtime.deleteIntegrity)
            assertThat(page.locator("body").textContent())
                .contains("Aktion nicht möglich", "konnte nicht gelöscht werden", "verwendet");
            screenshot(page, "workspace-delete-conflict", true);
        } catch (PlaywrightException e) {
            if (e.getMessage() != null && e.getMessage().contains("Executable doesn't exist")) {
                throw new TestAbortedException("Playwright Chromium browser is not installed; skipping browser E2E test", e);
            }
            throw e;
        }
    }

    private void runMultiDomainWorkspaceE2E(String baseUrl) {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            String selectedParcelId = createWorkspaceParcel(page, baseUrl, "P-100");
            String emptyParcelId = createWorkspaceParcel(page, baseUrl, "P-200");
            createWorkspaceRelated(page, baseUrl, "building", "Haus A", selectedParcelId);
            createWorkspaceRelated(page, baseUrl, "owner", "Anna Beispiel", selectedParcelId);

            page.navigate(baseUrl + "/interlisUi/index");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            Locator workspaceGroups = page.locator("[data-ili-navigation-group='workspaces']");
            assertThat(workspaceGroups.count()).isEqualTo(2);
            assertThat(workspaceGroups.first().textContent())
                .contains("Fachliche Arbeitsseiten", "Parzellen-Workspace");
            page.locator("[data-ili-workspace-link='parcel-workspace']").first().click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.url()).contains("/parcelWorkspace/index");
            assertThat(page.locator("[data-workspace-table='parcels']").count())
                .as("workspace index render at " + page.url() + "\n" + page.locator("body").innerText())
                .isEqualTo(1);
            assertThat(page.locator("[data-workspace-table='parcels']").textContent()).contains("P-100", "P-200");

            page.locator("[data-workspace-table='parcels'] a[href*='/parcelWorkspace/show/']")
                .filter(new Locator.FilterOptions().setHasText("P-100")).click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator("[data-multi-domain-workspace]").count()).isEqualTo(1);
            assertThat(page.locator("[data-workspace-display-label]").textContent()).contains("P-100");
            assertThat(page.locator("[data-workspace-table='buildings']").textContent()).contains("Haus A");
            assertThat(page.locator("[data-workspace-table='owners']").textContent()).contains("Anna Beispiel");
            assertThat(page.locator("[data-workspace-table='buildings'] a[href*='/building/show/']").count())
                .isEqualTo(1);
            assertThat(page.locator("[data-workspace-table='owners'] a[href*='/owner/show/']").count())
                .isEqualTo(1);
            assertNoHorizontalOverflow(page);

            page.locator("[data-workspace-table='buildings'] a[href*='/building/show/']").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.url()).contains("/building/show/");
            assertThat(page.locator("[data-workspace-display-label]").textContent()).contains("Haus A");

            page.navigate(baseUrl + "/parcelWorkspace/show/" + emptyParcelId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator("[data-workspace-empty]").count()).isGreaterThanOrEqualTo(2);
            assertThat(page.locator("body").innerText()).doesNotContain("Haus A", "Anna Beispiel");

            page.locator(".ili-workspace-header a[href*='/parcel/index']").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.url()).contains("/parcel/index");
            page.navigate(baseUrl + "/parcelWorkspace/show/" + selectedParcelId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            String body = page.locator("body").innerText();
            assertThat(body).doesNotContain("Audit", "Verlauf", "Protokoll", "Timeline", "Restore");

            page.locator("[data-workspace-edit-link]").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.url()).contains("/parcelWorkspace/edit/" + selectedParcelId);
            page.locator("#parcelNumber").fill("P-101");
            page.locator("input[name='buildingEdits[0].name']").fill("Haus B");
            page.locator("input[name='ownerEdits[0].name']").fill("Bea Beispiel");
            page.locator("[data-workspace-save]").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.url()).contains("/parcelWorkspace/show/" + selectedParcelId);
            assertThat(page.locator("[data-workspace-display-label]").textContent()).contains("P-101");
            assertThat(page.locator("[data-workspace-table='buildings']").textContent()).contains("Haus B");
            assertThat(page.locator("[data-workspace-table='owners']").textContent()).contains("Bea Beispiel");

            page.locator("[data-workspace-edit-link]").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.locator("form.ili-form").evaluate("form => form.noValidate = true");
            page.locator("#parcelNumber").fill("P-ERROR");
            page.locator("input[name='ownerEdits[0].name']").fill("");
            page.locator("[data-workspace-save]").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.url()).contains("/parcelWorkspace/update/" + selectedParcelId);
            assertThat(page.locator("[data-multi-domain-workspace-edit]").count()).isEqualTo(1);
            assertThat(page.locator("[data-validation-summary]").count()).isEqualTo(1);
            assertThat(page.locator("[data-field-error='ownerEdits-0-name']").count()).isEqualTo(1);
            assertThat(page.locator("#parcelNumber").inputValue()).isEqualTo("P-ERROR");
            assertThat(page.locator("input[name='ownerEdits[0].name']").inputValue()).isEmpty();

            page.navigate(baseUrl + "/parcelWorkspace/show/" + selectedParcelId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator("[data-workspace-display-label]").textContent()).contains("P-101");
            assertThat(page.locator("[data-workspace-table='buildings']").textContent()).contains("Haus B");
            assertThat(page.locator("[data-workspace-table='owners']").textContent()).contains("Bea Beispiel");
            assertThat(page.locator("body").innerText()).doesNotContain("P-ERROR", "Audit", "Verlauf", "Protokoll");
            screenshot(page, "phase7-mockup-05-workspace", true);
            screenshot(page, "workspace-multi-domain-show", true);
        } catch (PlaywrightException e) {
            if (e.getMessage() != null && e.getMessage().contains("Executable doesn't exist")) {
                throw new TestAbortedException("Playwright Chromium browser is not installed; skipping browser E2E test", e);
            }
            throw e;
        }
    }

    private String createWorkspaceParcel(Page page, String baseUrl, String number) {
        page.navigate(baseUrl + "/parcel/create");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        Locator field = page.locator("input[name='anumber']");
        if (field.count() == 0) {
            field = page.locator("input[name='number']");
        }
        assertThat(field.count()).as("Parcel number field on " + page.url()).isEqualTo(1);
        field.fill(number);
        submitForm(page);
        assertThat(page.url()).contains("/parcel/show/");
        return showId(page.url());
    }

    private void createWorkspaceRelated(Page page, String baseUrl, String controller,
                                        String name, String parcelId) {
        page.navigate(baseUrl + "/" + controller + "/create");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        Locator field = page.locator("input[name='aname']");
        if (field.count() == 0) {
            field = page.locator("input[name='name']");
        }
        assertThat(field.count()).as("Name field for " + controller).isEqualTo(1);
        field.fill(name);
        Locator relationship = page.locator("select[name='parcel.id']");
        assertThat(relationship.count()).as("Parcel relationship for " + controller).isEqualTo(1);
        relationship.selectOption(parcelId);
        submitForm(page);
        assertThat(page.url()).contains("/" + controller + "/show/");
    }

    private String showId(String url) {
        return url.replaceAll(".*/show/([^/?#]+).*", "$1");
    }

    private String createListMunicipality(Page page, String baseUrl, String name) {
        page.navigate(baseUrl + "/municipality/create");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        if (page.locator("input[name='aname']").count() == 0) {
            throw new AssertionError("Municipality create form missing aname field at " + page.url()
                + "\n" + page.locator("body").innerText());
        }
        page.locator("input[name='aname']").fill(name);
        submitForm(page);
        return page.url().replaceAll(".*/show/(\\d+).*", "$1");
    }

    private void configureListFormSections(Path appDir) throws IOException {
        Path applicationYaml = appDir.resolve("grails-app/conf/application.yml");
        String existing = Files.readString(applicationYaml);
        String addition = """
              ui:
                domains:
                  - iliName: ListQueryE2E.Lists.Record
                    form:
                      sections:
                        - title: Basisdaten
                          fields: [aname, astatus]
            """;
        String rootKey = "ili2grails:\n";
        int insertionPoint = existing.indexOf(rootKey);
        assertThat(insertionPoint).as("generated application.yml ili2grails root").isGreaterThanOrEqualTo(0);
        insertionPoint += rootKey.length();
        existing = existing.substring(0, insertionPoint)
            + addition
            + existing.substring(insertionPoint);
        Files.writeString(applicationYaml, existing);
    }

    private void augmentListRecordFieldMetadata(Path appDir) throws IOException {
        Path recordDomain = appDir.resolve("grails-app/domain/com/example/domain/Record.groovy");
        String source = Files.readString(recordDomain);
        String marker = "ayear: [label: 'Year', qualifiedName: 'ListQueryE2E.Lists.Record.Year']";
        String replacement = "ayear: [label: 'Year', documentation: 'Record year', unit: 'Jahr', "
            + "qualifiedName: 'ListQueryE2E.Lists.Record.Year']";
        assertThat(source).contains(marker);
        Files.writeString(recordDomain, source.replace(marker, replacement));
    }

    private void runFormUxE2E(Page page, String baseUrl, String municipalityId) {
        page.navigate(baseUrl + "/record/create");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.locator("[data-form-section='Basisdaten']").count()).isEqualTo(1);
        assertThat(page.locator("[data-form-section='Weitere Felder']").count()).isEqualTo(1);
        assertThat(page.locator("#field-ayear .ili-field-meta").count()).isEqualTo(1);
        assertThat(page.locator("#field-ayear .ili-field-documentation").textContent())
            .contains("Record year");
        assertThat(page.locator("#field-ayear .ili-unit-badge").textContent()).contains("Jahr");
        assertThat(page.locator("button[name='submitMode'][value='save']").count()).isEqualTo(1);
        assertThat(page.locator("button[name='submitMode'][value='saveAndContinue']").count()).isEqualTo(1);
        assertThat(page.locator("[data-sticky-form-actions]").count()).isEqualTo(1);

        page.locator("form.ili-form").evaluate("form => form.noValidate = true");
        page.locator("input[name='aname']").fill("Validation bleibt erhalten");
        page.locator("input[name='ayear']").fill("1800");
        page.locator("select[name='municipalityrole.id']").selectOption(municipalityId);
        page.locator("button[name='submitMode'][value='save']").click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.locator("[data-validation-summary]").count()).isEqualTo(1);
        assertThat(page.locator("input[name='aname']").inputValue())
            .isEqualTo("Validation bleibt erhalten");
        assertThat(page.locator("select[name='municipalityrole.id']").inputValue())
            .isEqualTo(municipalityId);
        assertThat(page.locator("[data-field-error='year'], #field-ayear .invalid-feedback").count())
            .isGreaterThan(0);

        page.locator("input[name='aname']").fill("Dirty state");
        assertThat(page.locator("[data-unsaved-badge]").isVisible()).isTrue();
        page.onceDialog(dialog -> {
            assertThat(dialog.message()).contains("ungespeicherte Änderungen");
            dialog.dismiss();
        });
        page.locator("a[data-unsaved-nav]").first().click();
        assertThat(page.locator("[data-unsaved-badge]").isVisible()).isTrue();

        page.navigate(baseUrl + "/record/create");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("input[name='aname']").fill("Speichern und weiter");
        page.locator("input[name='ayear']").fill("2024");
        page.locator("select[name='municipalityrole.id']").selectOption(municipalityId);
        page.locator("button[name='submitMode'][value='saveAndContinue']").click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.url()).contains("/record/edit/");
        assertThat(page.locator("input[name='aname']").inputValue()).isEqualTo("Speichern und weiter");

        page.locator("button[name='submitMode'][value='save']").click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.url()).contains("/record/show/");
        assertThat(page.locator("[data-unsaved-badge]").isVisible()).isFalse();
    }

    private String createListRecord(Page page, String baseUrl, String name, String status,
                                    boolean active, String year, String validFrom, String municipalityId) {
        page.navigate(baseUrl + "/record/create");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        if (page.locator("input[name='aname']").count() == 0) {
            throw new AssertionError("Record create form missing aname field at " + page.url()
                + "\n" + page.locator("body").innerText());
        }
        page.locator("input[name='aname']").fill(name);
        if (page.locator("select[name='astatus']").count() > 0) {
            Locator options = page.locator("select[name='astatus'] option");
            String selectedValue = null;
            List<String> available = new ArrayList<>();
            for (int i = 0; i < options.count(); i++) {
                Locator option = options.nth(i);
                String value = option.getAttribute("value");
                String label = option.textContent();
                available.add(value + "=" + label);
                if (status.equalsIgnoreCase(value) || status.equalsIgnoreCase(label == null ? "" : label.trim())) {
                    selectedValue = value;
                }
            }
            if (selectedValue == null) {
                throw new AssertionError("No enum option for " + status + ": " + available);
            }
            page.locator("select[name='astatus']").selectOption(selectedValue);
        }
        if (page.locator("input[name='aactive'][type='checkbox']").count() > 0) {
            if (active) page.locator("input[name='aactive'][type='checkbox']").check();
            else page.locator("input[name='aactive'][type='checkbox']").uncheck();
        }
        page.locator("input[name='ayear']").fill(year);
        if (page.locator("input[type='date'][name='validfrom']").count() > 0) {
            page.locator("input[type='date'][name='validfrom']").fill(validFrom);
        } else {
            String[] dateParts = validFrom.split("-");
            setDatePart(page, "validfrom_day", dateParts[2]);
            setDatePart(page, "validfrom_month", dateParts[1]);
            setDatePart(page, "validfrom_year", dateParts[0]);
        }
        page.locator("select[name='municipalityrole.id']").selectOption(municipalityId);
        submitForm(page);
        assertThat(page.url()).contains("/record/show/");
        return page.url().replaceAll(".*/show/(\\d+).*", "$1");
    }

    private void setDatePart(Page page, String field, String value) {
        String select = "select[name='" + field + "']";
        if (page.locator(select).count() > 0) {
            Locator options = page.locator(select + " option");
            String selectedValue = null;
            for (int i = 0; i < options.count(); i++) {
                Locator option = options.nth(i);
                String optionValue = option.getAttribute("value");
                String label = option.textContent();
                if (sameDatePart(optionValue, value) || sameDatePart(label, value)) {
                    selectedValue = optionValue;
                    break;
                }
            }
            if (selectedValue == null) {
                throw new AssertionError("No date option for " + field + "=" + value);
            }
            page.locator(select).selectOption(selectedValue);
        } else {
            page.locator("input[name='" + field + "']").fill(value);
        }
    }

    private boolean sameDatePart(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        try {
            return Integer.parseInt(actual.trim()) == Integer.parseInt(expected.trim());
        } catch (NumberFormatException ignored) {
            return actual.trim().equalsIgnoreCase(expected.trim());
        }
    }

    private void trySubmitOrVisit(Page page, String fallbackUrl) {
        if (page.locator("[data-form-submit]").count() > 0) {
            Locator saveAction = page.locator("[data-form-submit][value='save']");
            (saveAction.count() > 0 ? saveAction : page.locator("[data-form-submit]").first()).click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            return;
        }
        if (page.locator("input[type=\"submit\"], button[type=\"submit\"]").count() > 0) {
            page.locator("input[type=\"submit\"], button[type=\"submit\"]").first().click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            return;
        }
        page.navigate(fallbackUrl);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private static void screenshot(Page page, String name) {
        screenshot(page, name, false);
    }

    private static void screenshot(Page page, String name, boolean fullPage) {
        try {
            Path file = SCREENSHOT_DIR.resolve(name + ".png");
            Files.createDirectories(file.getParent());
            page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(fullPage));
            System.out.println("Screenshot: " + file.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Screenshot failed for " + name + ": " + e.getMessage());
        }
    }

    private void assertSecurityHeaders(Page page, String url) {
        APIResponse response = page.request().get(url);
        assertThat(response.status()).as("security header response status").isEqualTo(200);
        assertThat(responseHeader(response, "Content-Security-Policy"))
            .contains("default-src 'self'", "script-src 'self'", "style-src 'self'",
                "object-src 'none'", "frame-ancestors 'none'", "form-action 'self'")
            .doesNotContain("unsafe-inline", "unsafe-eval");
        assertThat(responseHeader(response, "X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(responseHeader(response, "X-Frame-Options")).isEqualTo("DENY");
        assertThat(responseHeader(response, "Referrer-Policy"))
            .isEqualTo("strict-origin-when-cross-origin");
        assertThat(responseHeader(response, "Permissions-Policy"))
            .isEqualTo("geolocation=(), microphone=(), camera=()");
    }

    private String responseHeader(APIResponse response, String name) {
        return response.headers().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(name))
            .map(entry -> entry.getValue())
            .findFirst()
            .orElse(null);
    }

    private void assertGetMutationRejected(Page page, String url) {
        APIResponse response = page.request().get(url);
        assertThat(response.status())
            .as("GET mutation must be rejected: " + url)
            .isGreaterThanOrEqualTo(400)
            .isLessThan(500);
    }

    private void assertNoHorizontalOverflow(Page page) {
        assertThat(page.evaluate("() => document.documentElement.scrollWidth <= window.innerWidth + 1"))
            .as("responsive layout must not overflow horizontally at " + page.url())
            .isEqualTo(true);
    }

    private void runBrowserCrud(String baseUrl) {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();

            verifyApplicationShell(page, browser, baseUrl);
            createAddress(page, baseUrl);
            String addressShowUrl = page.url();
            assertGetMutationRejected(page, baseUrl + "/address/delete/" + showId(addressShowUrl));
            editCurrentRecordGeometry(page);

            createPerson(page, baseUrl);
            deleteCurrentRecord(page);

            page.navigate(addressShowUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            deleteCurrentRecord(page);
        } catch (PlaywrightException e) {
            if (e.getMessage() != null && e.getMessage().contains("Executable doesn't exist")) {
                throw new TestAbortedException("Playwright Chromium browser is not installed; skipping browser E2E test", e);
            }
            throw e;
        }
    }

    private void verifyApplicationShell(Page page, Browser browser, String baseUrl) {
        page.navigate(baseUrl + "/interlisUi/index");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertSecurityHeaders(page, baseUrl + "/interlisUi/index");
        assertThat(page.locator("[data-ili-sidebar]").count()).isEqualTo(1);
        assertThat(page.locator("[data-ili-domain-finder-input]").count()).isEqualTo(1);
        assertThat(page.locator("[data-ili-domain-finder-input]").getAttribute("role")).isEqualTo("combobox");
        assertThat(page.locator("[data-ili-finder-results][role='listbox']").count()).isEqualTo(1);
        assertThat(page.locator("[data-ili-extension-point=\"topbar-toolbar\"]").count()).isEqualTo(1);
        assertNoHorizontalOverflow(page);
        screenshot(page, "phase7-mockup-01-shell", true);

        page.locator("[data-ili-domain-finder-input]").fill("Person");
        page.locator("[data-ili-domain-finder-input]").press("ArrowDown");
        assertThat(page.locator("[data-ili-domain-finder-input]").getAttribute("aria-activedescendant"))
            .isNotBlank();
        page.locator("[data-ili-domain-finder-input]").press("Enter");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.url()).contains("/person/index");

        page.navigate(baseUrl + "/interlisUi/domains?q=Address");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.locator(".ili-explorer-search-results").count()).isEqualTo(1);
        assertThat(page.locator(".ili-explorer-search-results").textContent()).contains("Address");

        page.navigate(baseUrl + "/interlisUi/index");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("[data-ili-favorite-toggle]").first().click();
        String favorites = (String) page.evaluate("() => window.localStorage.getItem('ili2grails.ui.favorites')");
        assertThat(favorites).isNotBlank();
        page.locator("[data-ili-domain-link]").first().click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        String recents = (String) page.evaluate("() => window.localStorage.getItem('ili2grails.ui.recents')");
        assertThat(recents).isNotBlank();

        page.setViewportSize(390, 844);
        page.navigate(baseUrl + "/interlisUi/index");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("[data-ili-sidebar-toggle]").click();
        page.waitForTimeout(500);
        String sidebarClass = (String) page.locator("#iliSidebar").getAttribute("class");
        assertThat(sidebarClass).as("mobile sidebar class").contains("show");
        page.locator("[data-ili-sidebar-close]").click();
        page.setViewportSize(1280, 900);

        Page storageDisabledPage = browser.newPage();
        List<String> pageErrors = new ArrayList<>();
        storageDisabledPage.onPageError(pageErrors::add);
        storageDisabledPage.addInitScript("""
            () => Object.defineProperty(window, 'localStorage', {
              configurable: true,
              get() { throw new Error('localStorage disabled'); }
            })
            """);
        storageDisabledPage.navigate(baseUrl + "/interlisUi/index");
        storageDisabledPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(pageErrors).isEmpty();
        storageDisabledPage.close();
    }

    private void createAddress(Page page, String baseUrl) {
        openCreateForm(page, baseUrl, "Address");
        assertThat(page.locator("[data-form-section='Basisdaten']").count()).isEqualTo(1);
        assertThat(page.locator("[data-form-field]").count()).isGreaterThan(0);
        fillVisibleControls(page, "E2E");
        Locator city = page.locator("[name='city']");
        assertThat(city.count()).isEqualTo(1);
        city.fill("<img src=x onerror=alert('xss')>");
        setGeometryWkt(page, "POINT (2600000 1200000)");
        submitForm(page);
        assertThat(page.url()).contains("/address/show/");
        assertThat(page.locator(".ili-geometry-editor").count()).isGreaterThan(0);
        assertWorkspaceShow(page, "E2E", null);
        String escapedBody = page.locator("body").innerHTML();
        assertThat(page.locator("body").textContent()).contains("<img src=x onerror=alert('xss')>");
        assertThat(escapedBody).doesNotContain("<img");
        assertThat(page.locator("img, [onerror], script:not([src])").count()).isZero();
        assertThat(page.locator("[data-workspace-geometry]").count()).isEqualTo(1);
        assertThat(page.locator(".ili-map-panel").count()).isEqualTo(1);
        screenshot(page, "workspace-address-show", true);
        Locator deleteOpen = page.locator("[data-domain-workspace-header] [data-delete-open]");
        deleteOpen.click();
        page.waitForTimeout(250);
        screenshot(page, "workspace-address-delete-dialog", true);
        assertThat(page.locator("[data-delete-modal]").isVisible()).isTrue();
        assertThat(page.locator("[data-delete-modal]").getAttribute("role")).isEqualTo("dialog");
        assertThat(page.locator("[data-delete-modal]").getAttribute("aria-modal")).isEqualTo("true");
        assertThat(page.locator("[data-delete-modal]").getAttribute("aria-describedby")).isNotBlank();
        assertThat(page.locator("[data-delete-modal]").textContent())
            .contains("Address löschen?", "E2E", "dauerhaft gelöscht",
                "serverseitig geprüft", "Integritätsbedingungen", "Endgültig löschen");
        page.waitForFunction(
            "() => document.activeElement && document.activeElement.matches('[data-delete-cancel]')");
        assertThat(page.locator("[data-delete-cancel]")
            .evaluate("element => document.activeElement === element"))
            .isEqualTo(true);
        Locator deleteModalClose = page.locator("[data-delete-modal] .ili-modal-close");
        assertThat(deleteModalClose.isVisible()).isTrue();
        assertThat(deleteModalClose.getAttribute("class")).contains("ili-modal-close", "ms-auto");
        assertThat(deleteModalClose.evaluate("element => getComputedStyle(element).borderStyle"))
            .isEqualTo("none");
        page.locator("[data-delete-modal] .ili-modal-close").click();
        page.locator("[data-delete-modal]").waitFor(new Locator.WaitForOptions().setState(
            com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
        page.waitForFunction("() => document.activeElement && document.activeElement.matches('[data-delete-open]')");
        assertThat(page.locator("[data-delete-open]").evaluate("element => document.activeElement === element"))
            .isEqualTo(true);

        assertNoHorizontalOverflow(page);

        page.setViewportSize(390, 844);
        Locator workspaceActions = page.locator(
            "[data-domain-workspace-header] .ili-page-actions");
        assertThat(workspaceActions.locator(".btn").count()).isEqualTo(4);
        assertThat(workspaceActions.evaluate("element => getComputedStyle(element).display"))
            .isEqualTo("grid");
        String gridColumns = (String) workspaceActions.evaluate(
            "element => getComputedStyle(element).gridTemplateColumns");
        assertThat(gridColumns.trim().split("\\s+")).hasSize(2);
        assertNoHorizontalOverflow(page);
        screenshot(page, "workspace-address-show-mobile", true);

        deleteOpen.click();
        page.waitForTimeout(250);
        page.waitForFunction(
            "() => document.activeElement && document.activeElement.matches('[data-delete-cancel]')");
        assertNoHorizontalOverflow(page);
        screenshot(page, "workspace-address-delete-dialog-mobile", true);
        page.locator("[data-delete-cancel]").click();
        page.locator("[data-delete-modal]").waitFor(new Locator.WaitForOptions().setState(
            com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
        page.setViewportSize(1280, 900);
    }

    private void editCurrentRecordGeometry(Page page) {
        page.locator("a[href*='/edit/'], a[href$='/edit']").first().click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        screenshot(page, "phase7-mockup-04-edit", true);
        assertThat(page.locator(".ili-form-section").count()).isGreaterThan(0);
        assertThat(page.locator("[data-sticky-form-actions]").count()).isEqualTo(1);
        setGeometryWkt(page, "POINT (2600010 1200010)");
        assertThat(page.locator("[data-unsaved-badge]").isVisible()).isTrue();
        submitForm(page);
        assertThat(page.url()).contains("/address/show/");
    }

    private void createPerson(Page page, String baseUrl) {
        openCreateForm(page, baseUrl, "Person");
        assertThat(page.locator("#field-firstname > .ili-native-grid").count())
            .as("required FirstName field wrapper")
            .isEqualTo(1);
        assertThat(page.locator("#field-email > .ili-native-grid").count())
            .as("optional Email field wrapper")
            .isEqualTo(1);
        String firstNameRowGap = (String) page.locator("#field-firstname > .ili-native-grid")
            .evaluate("element => getComputedStyle(element).rowGap");
        String emailRowGap = (String) page.locator("#field-email > .ili-native-grid")
            .evaluate("element => getComputedStyle(element).rowGap");
        String quarterRem = (String) page.evaluate("""
            () => {
              const rootFontSize = parseFloat(getComputedStyle(document.documentElement).fontSize);
              return (rootFontSize * 0.25) + 'px';
            }
            """);
        assertThat(firstNameRowGap).as("FirstName label/control gap").isEqualTo(quarterRem);
        assertThat(emailRowGap).as("Email label/control gap").isEqualTo(firstNameRowGap);
        fillVisibleControls(page, "Person E2E");
        if (page.locator(".js-relationship-search").count() > 0) {
            assertThat(page.locator(".js-relationship-search").first().getAttribute("role"))
                .isEqualTo("combobox");
            assertThat(page.locator("[data-relationship-list][role='listbox']").count())
                .isGreaterThan(0);
        }
        selectFirstRelationshipOptions(page);
        submitForm(page);
        assertThat(page.url()).contains("/person/show/");
    }

    private void assertWorkspaceShow(Page page, String expectedLabel, String expectedMunicipalityId) {
        assertThat(page.locator("[data-domain-workspace]").count()).isEqualTo(1);
        assertThat(page.locator("[data-domain-workspace-header]").count()).isEqualTo(1);
        assertThat(page.locator("[data-workspace-display-label]").textContent()).contains(expectedLabel);
        assertThat(page.locator("[data-workspace-domain-label]").count()).isZero();
        assertThat(page.locator(".ili-workspace-header .ili-page-subtitle").textContent()).contains("· #");
        assertThat(page.locator("[data-workspace-details]").count()).isEqualTo(1);
        assertThat(page.locator("[data-workspace-relationships]").count()).isZero();
        if (expectedMunicipalityId != null) {
            assertThat(page.locator("[data-workspace-details] .ili-data-link").count()).isGreaterThan(0);
            assertThat(page.locator("[data-workspace-details] .ili-data-link").first()
                .getAttribute("href")).contains(expectedMunicipalityId);
        }
        assertThat(page.locator("[data-workspace-danger-zone]").count()).isZero();
        assertThat(page.locator("[data-delete-modal]").count()).isEqualTo(1);
        Locator workspaceActions = page.locator(
            "[data-domain-workspace-header] .ili-page-actions > a, "
                + "[data-domain-workspace-header] .ili-page-actions > button");
        assertThat(workspaceActions.count()).isEqualTo(4);
        assertThat(workspaceActions.nth(2).textContent()).contains("Bearbeiten");
        assertThat(workspaceActions.nth(3).textContent()).contains("Löschen");
        assertThat(workspaceActions.nth(3).getAttribute("class")).contains("btn-outline-danger");
        assertThat(workspaceActions.nth(3).getAttribute("data-delete-open")).isNotBlank();
        assertThat(page.locator("[data-audit-tab], [data-history-tab], [data-protocol-tab], [data-timeline]").count())
            .isZero();
        String body = page.locator("body").textContent();
        assertThat(body).doesNotContain("Danger Zone", "Destruktiv",
            "Audit", "Verlauf", "Protokoll", "Timeline", "Restore");
        if (expectedMunicipalityId != null) {
            Locator municipalityLink = page.locator(
                "[data-workspace-details] a.ili-data-link");
            assertThat(municipalityLink.count()).isGreaterThan(0);
            assertThat(municipalityLink.first().getAttribute("href"))
                .contains("/municipality/show/" + expectedMunicipalityId);
        }
    }

    private void selectFirstRelationshipOptions(Page page) {
        page.evaluate("""
            () => {
              for (const select of document.querySelectorAll('select[name$=".id"]')) {
                const option = Array.from(select.options).find(item => item.value);
                if (option) {
                  select.value = option.value;
                  select.dispatchEvent(new Event('change', { bubbles: true }));
                }
              }
            }
            """);
    }

    private void openCreateForm(Page page, String baseUrl, String menuLabel) {
        page.navigate(baseUrl + "/");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        String indexUrl = (String) page.evaluate("""
            (menuLabel) => {
              const link = Array.from(document.querySelectorAll('[data-ili-domain-link]'))
                .find(item => item.textContent.trim() === menuLabel);
              return link ? link.href : null;
            }
            """, menuLabel);
        assertThat(indexUrl).as("navigation link for " + menuLabel).isNotBlank();

        page.navigate(indexUrl);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.title()).doesNotContain("Page Not Found");

        String createUrl = (String) page.evaluate("""
            () => {
              const link = Array.from(document.querySelectorAll('.ili-page-actions a, .ili-empty-state a'))
                .find(item => item.href && item.href.includes('/create'));
              return link ? link.href : null;
            }
            """);
        assertThat(createUrl)
            .as("create link for " + menuLabel + " on " + page.url() + "\n" + pageSummary(page))
            .isNotBlank();

        page.navigate(createUrl);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.title()).doesNotContain("Page Not Found");
    }

    private String pageSummary(Page page) {
        return (String) page.evaluate("""
            () => JSON.stringify({
              title: document.title,
              links: Array.from(document.querySelectorAll('a')).slice(0, 20).map(item => ({
                text: item.textContent.trim(),
                href: item.href,
                className: item.getAttribute('class')
              })),
              body: document.body.innerHTML.slice(0, 10000)
            }, null, 2)
            """);
    }

    private void deleteCurrentRecord(Page page) {
        page.locator("[data-delete-open]").click();
        page.locator("[data-delete-confirm]").waitFor();
        page.evaluate("""
            () => {
              const form = document.querySelector('.ili-hidden-delete-form');
              if (!form) {
                throw new Error('No delete form found');
              }
              const submit = form.querySelector('.js-delete-submit');
              if (typeof form.requestSubmit === 'function') {
                form.requestSubmit(submit || undefined);
              } else if (submit) {
                submit.click();
              } else {
                form.submit();
              }
            }
            """);
        page.waitForURL("**/index", new Page.WaitForURLOptions().setTimeout(10_000));
        assertThat(page.url()).contains("/index");
    }

    private void fillVisibleControls(Page page, String prefix) {
        page.evaluate("""
            (prefix) => {
              const controls = Array.from(document.querySelectorAll('input, textarea'))
                .filter(input => input.offsetParent !== null)
                .filter(input => !input.disabled && !input.readOnly)
                .filter(input => !input.classList.contains('js-relationship-search'))
                .filter(input => {
                  const type = (input.getAttribute('type') || 'text').toLowerCase();
                  return ['text', 'email', 'tel', 'search', 'date', 'number', 'textarea'].includes(type);
                });
              let index = 1;
              for (const input of controls) {
                const type = (input.getAttribute('type') || 'text').toLowerCase();
                if (type === 'date') {
                  input.value = '1990-01-01';
                } else if (type === 'number') {
                  input.value = String(1000 + index);
                } else if ((input.name || '').toLowerCase().includes('email')) {
                  input.value = 'e2e@example.test';
                } else if ((input.name || '').toLowerCase().includes('postal')) {
                  input.value = '4500';
                } else {
                  input.value = prefix + ' ' + index;
                }
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                index++;
              }
            }
            """, prefix);
    }

    private void setGeometryWkt(Page page, String wkt) {
        if (page.locator(".js-geometry-wkt").count() == 0) {
            String diagnostics = (String) page.evaluate("""
                () => JSON.stringify({
                  url: window.location.href,
                  title: document.title,
                  geometryEditors: Array.from(document.querySelectorAll('[data-geometry-field]')).map(item => ({
                    field: item.getAttribute('data-geometry-field'),
                    kind: item.getAttribute('data-geometry-kind')
                  })),
                  inputs: Array.from(document.querySelectorAll('input, select, textarea')).map(item => ({
                    name: item.getAttribute('name'),
                    type: item.getAttribute('type'),
                    className: item.getAttribute('class')
                  })),
                  body: document.body.innerHTML.slice(0, 2000)
                }, null, 2)
                """);
            throw new AssertionError("No geometry WKT field found on generated form:\n" + diagnostics);
        }
        page.evaluate("""
            (wkt) => {
              const input = document.querySelector('.js-geometry-wkt');
              if (!input) {
                throw new Error('No geometry WKT field found');
              }
              input.value = wkt;
              input.dispatchEvent(new Event('input', { bubbles: true }));
              input.dispatchEvent(new Event('change', { bubbles: true }));
            }
            """, wkt);
    }

    private void submitForm(Page page) {
        assertThat(page.locator("[data-form-submit]").count())
            .as("submit action on " + page.url() + "\n" + pageSummary(page))
            .isGreaterThan(0);
        Locator saveAction = page.locator("[data-form-submit][value='save']");
        (saveAction.count() > 0 ? saveAction : page.locator("[data-form-submit]").first()).click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.locator(".alert-danger:visible").count())
            .as("validation errors after submit on " + page.url() + "\n" + page.locator("body").innerText())
            .isZero();
    }

    private void generateScaffolding(Path appDir, ModelMetadata metadata, GenerationConfig config)
        throws IOException, InterruptedException {
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        List<String> targetClasses = metadata.getAllClasses().stream()
            .filter(classMetadata -> !classMetadata.isAbstract())
            .sorted(Comparator.comparing(ClassMetadata::getName, Comparator.nullsLast(String::compareTo)))
            .map(classMetadata -> DOMAIN_PACKAGE + "." + registry.className(classMetadata))
            .toList();
        for (String targetClass : targetClasses) {
            runCommand(appDir, List.of("./grailsw", "generate-all", targetClass), COMMAND_TIMEOUT);
        }
        runCommand(appDir, List.of("./gradlew", "compileGroovy"), COMMAND_TIMEOUT);
    }

    private Process startGrailsApp(Path appDir, int port) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
            "./gradlew",
            "bootRun",
            "--args=--server.port=" + port
        );
        builder.directory(appDir.toFile());
        Path logFile = appDir.resolve("build/browser-e2e.log");
        Files.createDirectories(logFile.getParent());
        builder.redirectErrorStream(true);
        builder.redirectOutput(logFile.toFile());
        builder.environment().put("DB_USERNAME", jdbcQueryValue("user", "username", "postgres"));
        builder.environment().put("DB_PASSWORD", jdbcQueryValue("password", null, "secret"));
        return builder.start();
    }

    private void waitForGrailsApp(Process process, Path appDir, String url) throws Exception {
        long deadline = System.nanoTime() + BOOT_TIMEOUT.toNanos();
        Exception lastError = null;
        while (System.nanoTime() < deadline && process.isAlive()) {
            try (java.io.InputStream ignored = java.net.URI.create(url).toURL().openStream()) {
                return;
            } catch (Exception e) {
                lastError = e;
                Thread.sleep(1000);
            }
        }
        Path logFile = appDir.resolve("build/browser-e2e.log");
        String runtimeLog = Files.exists(logFile)
            ? Files.readString(logFile)
            : "(browser-e2e.log fehlt)";
        throw new IOException(
            "Grails app did not start at " + url
                + "\nProcess alive: " + process.isAlive()
                + "\nGenerated app log:\n" + runtimeLog,
            lastError
        );
    }

    private void waitForHttp(String url) throws Exception {
        long deadline = System.nanoTime() + BOOT_TIMEOUT.toNanos();
        Exception lastError = null;
        while (System.nanoTime() < deadline) {
            try (java.io.InputStream ignored = java.net.URI.create(url).toURL().openStream()) {
                return;
            } catch (Exception e) {
                lastError = e;
                Thread.sleep(1000);
            }
        }
        throw new IOException("Grails app did not start at " + url, lastError);
    }

    private Path createGrailsApp() throws IOException, InterruptedException {
        runCommand(tempDir, List.of("grails", "create-app", "browser-e2e"), COMMAND_TIMEOUT);
        Path appDir = tempDir.resolve("browser-e2e");
        appDir.resolve("gradlew").toFile().setExecutable(true);
        appDir.resolve("grailsw").toFile().setExecutable(true);
        return appDir;
    }

    private ModelMetadata readMetadata(String schemaName) throws Exception {
        try (Connection connection = DriverManager.getConnection(baseJdbcUrl())) {
            MetadataReader reader = new MetadataReader(connection, MODEL_FILE.toFile(), schemaName, MODEL_REPOSITORIES);
            return reader.readMetadata("SimpleAddressModel");
        }
    }

    private ModelMetadata readListQueryMetadata(String schemaName) throws Exception {
        try (Connection connection = DriverManager.getConnection(baseJdbcUrl())) {
            MetadataReader reader = new MetadataReader(connection,
                LIST_QUERY_MODEL_FILE.toFile(), schemaName, MODEL_REPOSITORIES);
            return reader.readMetadata("ListQueryE2E");
        }
    }

    private ModelMetadata readAssociationMetadata(String schemaName) throws Exception {
        try (Connection connection = DriverManager.getConnection(baseJdbcUrl())) {
            MetadataReader reader = new MetadataReader(connection,
                ASSOCIATION_MODEL_FILE.toFile(), schemaName, MODEL_REPOSITORIES);
            return reader.readMetadata("QuickLinkE2E");
        }
    }

    private ModelMetadata readContextualAssociationMetadata(String schemaName) throws Exception {
        try (Connection connection = DriverManager.getConnection(baseJdbcUrl())) {
            MetadataReader reader = new MetadataReader(connection,
                CONTEXTUAL_ASSOC_MODEL_FILE.toFile(), schemaName, MODEL_REPOSITORIES);
            return reader.readMetadata("ContextualAssociationE2E");
        }
    }

    private ModelMetadata readWorkspaceMetadata(String schemaName) throws Exception {
        try (Connection connection = DriverManager.getConnection(baseJdbcUrl())) {
            MetadataReader reader = new MetadataReader(connection,
                WORKSPACE_MODEL_FILE.toFile(), schemaName, MODEL_REPOSITORIES);
            return reader.readMetadata("MultiDomainWorkspaceE2E");
        }
    }

    private ModelMetadata readGettingStartedMetadata(String schemaName) throws Exception {
        try (Connection connection = DriverManager.getConnection(baseJdbcUrl())) {
            MetadataReader reader = new MetadataReader(
                connection,
                GETTING_STARTED_MODEL_FILE.toFile(),
                schemaName,
                List.of(GETTING_STARTED_MODEL_FILE.getParent().toString())
            );
            return reader.readMetadata("GsSimpleModel");
        }
    }

    private void addWorkspaceVersionColumns(String schemaName, ModelMetadata metadata) throws SQLException {
        try (Connection connection = DriverManager.getConnection(baseJdbcUrl());
             Statement statement = connection.createStatement()) {
            for (String iliName : List.of(
                MultiDomainWorkspaceFixture.PARCEL_ILI_NAME,
                MultiDomainWorkspaceFixture.BUILDING_ILI_NAME,
                MultiDomainWorkspaceFixture.OWNER_ILI_NAME)) {
                ClassMetadata classMetadata = metadata.getClass(iliName);
                if (classMetadata == null || classMetadata.getTableName() == null
                    || classMetadata.getTableName().isBlank()) {
                    throw new SQLException("Missing workspace table metadata for " + iliName);
                }
                String tableName = quoteIdentifier(classMetadata.getTableName());
                statement.execute("ALTER TABLE " + quoteIdentifier(schemaName) + "." + tableName
                    + " ADD COLUMN IF NOT EXISTS \"version\" BIGINT NOT NULL DEFAULT 0");
            }
        }
    }

    private String quoteIdentifier(String identifier) throws SQLException {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_]+")) {
            throw new SQLException("Unsafe SQL identifier in workspace fixture: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private void importAssociationSchema(String schemaName) throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        Path ili2pgHome = ili2pgHome();
        String classpath = ili2pgHome.resolve("ili2pg-5.5.1.jar")
            + File.pathSeparator
            + ili2pgHome.resolve("libs/*");
        List<String> command = new ArrayList<>(List.of(
            javaExecutable.toString(),
            "-cp", classpath,
            "ch.ehi.ili2pg.PgMain",
            "--dbhost", "localhost",
            "--dbport", "54321",
            "--dbdatabase", "edit",
            "--dbusr", "postgres",
            "--dbpwd", "secret",
            "--defaultSrsCode", "2056",
            "--createFk",
            "--nameByTopic",
            "--strokeArcs",
            "--smart2Inheritance",
            "--createEnumTabs",
            "--modeldir", String.join(";", MODEL_REPOSITORIES),
            "--models", "QuickLinkE2E",
            "--dbschema", schemaName,
            "--schemaimport"
        ));
        CommandResult result = runCommandResult(Path.of("."), command, COMMAND_TIMEOUT);
        if (result.exitCode() != 0) {
            throw new IOException("ili2pg import failed for QuickLinkE2E (exit " + result.exitCode() + "):\n" + result.output());
        }
    }

    private void importListQuerySchema(String schemaName) throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        Path ili2pgHome = ili2pgHome();
        String classpath = ili2pgHome.resolve("ili2pg-5.5.1.jar")
            + File.pathSeparator
            + ili2pgHome.resolve("libs/*");
        List<String> command = new ArrayList<>(List.of(
            javaExecutable.toString(),
            "-cp", classpath,
            "ch.ehi.ili2pg.PgMain",
            "--dbhost", "localhost",
            "--dbport", "54321",
            "--dbdatabase", "edit",
            "--dbusr", "postgres",
            "--dbpwd", "secret",
            "--defaultSrsCode", "2056",
            "--createFk",
            "--nameByTopic",
            "--smart2Inheritance",
            "--createEnumTabs",
            "--modeldir", String.join(";", MODEL_REPOSITORIES),
            "--models", "ListQueryE2E",
            "--dbschema", schemaName,
            "--schemaimport"
        ));
        CommandResult result = runCommandResult(Path.of("."), command, COMMAND_TIMEOUT);
        if (result.exitCode() != 0) {
            throw new IOException("ili2pg import failed for ListQueryE2E (exit " + result.exitCode() + "):\n" + result.output());
        }
    }

    private void importContextualAssociationSchema(String schemaName) throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        Path ili2pgHome = ili2pgHome();
        String classpath = ili2pgHome.resolve("ili2pg-5.5.1.jar")
            + File.pathSeparator
            + ili2pgHome.resolve("libs/*");
        List<String> command = new ArrayList<>(List.of(
            javaExecutable.toString(),
            "-cp", classpath,
            "ch.ehi.ili2pg.PgMain",
            "--dbhost", "localhost",
            "--dbport", "54321",
            "--dbdatabase", "edit",
            "--dbusr", "postgres",
            "--dbpwd", "secret",
            "--defaultSrsCode", "2056",
            "--createFk",
            "--nameByTopic",
            "--strokeArcs",
            "--smart2Inheritance",
            "--createEnumTabs",
            "--modeldir", String.join(";", MODEL_REPOSITORIES),
            "--models", "ContextualAssociationE2E",
            "--dbschema", schemaName,
            "--schemaimport"
        ));
        CommandResult result = runCommandResult(Path.of("."), command, COMMAND_TIMEOUT);
        if (result.exitCode() != 0) {
            throw new IOException("ili2pg import failed for ContextualAssociationE2E (exit " + result.exitCode() + "):\n" + result.output());
        }
    }

    private void importWorkspaceSchema(String schemaName) throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        Path ili2pgHome = ili2pgHome();
        String classpath = ili2pgHome.resolve("ili2pg-5.5.1.jar")
            + File.pathSeparator
            + ili2pgHome.resolve("libs/*");
        List<String> command = new ArrayList<>(List.of(
            javaExecutable.toString(),
            "-cp", classpath,
            "ch.ehi.ili2pg.PgMain",
            "--dbhost", "localhost",
            "--dbport", "54321",
            "--dbdatabase", "edit",
            "--dbusr", "postgres",
            "--dbpwd", "secret",
            "--defaultSrsCode", "2056",
            "--createFk",
            "--nameByTopic",
            "--smart2Inheritance",
            "--createEnumTabs",
            "--modeldir", String.join(";", MODEL_REPOSITORIES),
            "--models", "MultiDomainWorkspaceE2E",
            "--dbschema", schemaName,
            "--schemaimport"
        ));
        CommandResult result = runCommandResult(Path.of("."), command, COMMAND_TIMEOUT);
        if (result.exitCode() != 0) {
            throw new IOException("ili2pg import failed for MultiDomainWorkspaceE2E (exit "
                + result.exitCode() + "):\n" + result.output());
        }
    }

    private void runAssociationQuickLinkE2E(String baseUrl) {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();

            // Create two participants and one counterpart via the generated CRUD UI.
            String personAId = createRecord(page, baseUrl, "person");
            String personBId = createRecord(page, baseUrl, "person");
            String parcelId = createRecord(page, baseUrl, "tag");

            // Person show page must render the genuine QUICK association section (ExtendedTopicAssociation).
            page.navigate(baseUrl + "/person/show/" + personAId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.locator(".ili-association-section").count())
                .as("association sections on Person show")
                .isGreaterThan(0);
            assertThat(page.locator(".ili-association-quick-form").count())
                .as("quick-add form for the genuine QUICK association")
                .isGreaterThan(0);

            // Read the quick-add context/role straight from the rendered form (no hard-coding).
            String contextId = (String) page.evaluate(
                "() => document.querySelector('.ili-association-quick-form input[name=\"context\"]').value");
            String role = (String) page.evaluate(
                "() => document.querySelector('.ili-association-quick-form input[name=\"role\"]').value");
            assertThat(contextId).contains("::");
            assertThat(role).isNotBlank();

            // Quick-link create (POST) through the real command service.
            APIResponse createResp = page.request().post(
                baseUrl + "/person/associationCreate/" + personAId
                    + "?context=" + urlEncode(contextId)
                    + "&role=" + urlEncode(role)
                    + "&targetId=" + parcelId
                    + "&format=json");
            String createBody = createResp.text();
            assertThat(createBody)
                .as("quick-link create result (status=" + createResp.status() + ")")
                .contains("\"success\":true");

            long total = associationTotal(page, baseUrl, personAId, contextId);
            assertThat(total).as("link count for owner A after create").isEqualTo(1);
            String associationId = firstAssociationId(page, baseUrl, personAId, contextId);
            assertThat(associationId).isNotBlank();

            // Manipulation: owner B must not be able to delete owner A's link.
            APIResponse manipResp = page.request().delete(
                baseUrl + "/person/associationDelete/" + personBId
                    + "?context=" + urlEncode(contextId)
                    + "&associationId=" + associationId
                    + "&format=json");
            assertThat(manipResp.status())
                .as("wrong-owner delete must be rejected (body=" + manipResp.text() + ")")
                .isEqualTo(404);
            assertThat(associationTotal(page, baseUrl, personAId, contextId))
                .as("link survives the manipulation attempt")
                .isEqualTo(1);

            // Rightful delete removes only the link.
            APIResponse deleteResp = page.request().delete(
                baseUrl + "/person/associationDelete/" + personAId
                    + "?context=" + urlEncode(contextId)
                    + "&associationId=" + associationId
                    + "&format=json");
            assertThat(deleteResp.status()).as("rightful delete status").isBetween(200, 299);
            assertThat(associationTotal(page, baseUrl, personAId, contextId))
                .as("link removed after rightful delete")
                .isZero();

            // The counterpart object still exists.
            page.navigate(baseUrl + "/tag/show/" + parcelId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page.title()).doesNotContain("Page Not Found");
            assertThat(page.url()).contains("/tag/show/" + parcelId);
        } catch (PlaywrightException e) {
            if (e.getMessage() != null && e.getMessage().contains("Executable doesn't exist")) {
                throw new TestAbortedException("Playwright Chromium browser is not installed; skipping browser E2E test", e);
            }
            throw e;
        }
    }

    private String createRecord(Page page, String baseUrl, String controller) {
        page.navigate(baseUrl + "/" + controller + "/create");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.title()).doesNotContain("Page Not Found");
        fillVisibleControls(page, "QLE2E");
        submitForm(page);
        assertThat(page.url()).contains("/" + controller + "/show/");
        return page.url().replaceAll(".*/show/(\\d+).*", "$1");
    }

    private long associationTotal(Page page, String baseUrl, String ownerId, String contextId) {
        APIResponse resp = page.request().get(
            baseUrl + "/person/associationPage/" + ownerId + "?context=" + urlEncode(contextId) + "&format=json");
        String body = resp.text();
        Object total = parseJsonNumber(body, "total");
        assertThat(total)
            .as("associationPage did not return a JSON total (status=" + resp.status() + ", body=" + body + ")")
            .isNotNull();
        return ((Number) total).longValue();
    }

    private String firstAssociationId(Page page, String baseUrl, String ownerId, String contextId) {
        APIResponse resp = page.request().get(
            baseUrl + "/person/associationPage/" + ownerId + "?context=" + urlEncode(contextId) + "&format=json");
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"associationId\"\\s*:\\s*\"(\\d+)\"")
            .matcher(resp.text());
        return m.find() ? m.group(1) : null;
    }

    private Object parseJsonNumber(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"" + key + "\"\\s*:\\s*(\\d+)")
            .matcher(json == null ? "" : json);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void importSchema(String schemaName) throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        Path ili2pgHome = ili2pgHome();
        String classpath = ili2pgHome.resolve("ili2pg-5.5.1.jar")
            + File.pathSeparator
            + ili2pgHome.resolve("libs/*");
        List<String> command = new ArrayList<>(List.of(
            javaExecutable.toString(),
            "-cp", classpath,
            "ch.ehi.ili2pg.PgMain",
            "--dbhost", "localhost",
            "--dbport", "54321",
            "--dbdatabase", "edit",
            "--dbusr", "postgres",
            "--dbpwd", "secret",
            "--defaultSrsCode", "2056",
            "--createFk",
            "--nameByTopic",
            "--strokeArcs",
            "--smart2Inheritance",
            "--createEnumTabs",
            "--modeldir", String.join(";", MODEL_REPOSITORIES),
            "--models", "SimpleAddressModel",
            "--dbschema", schemaName,
            "--schemaimport"
        ));
        CommandResult result = runCommandResult(Path.of("."), command, COMMAND_TIMEOUT);
        if (result.exitCode() != 0) {
            throw new IOException("ili2pg import failed (exit " + result.exitCode() + "):\n" + result.output());
        }
    }

    private void importGettingStartedSchemaAndData(String schemaName)
        throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        Path ili2pgHome = ili2pgHome();
        String classpath = ili2pgHome.resolve("ili2pg-5.5.1.jar")
            + File.pathSeparator
            + ili2pgHome.resolve("libs/*");
        List<String> commonArguments = List.of(
            javaExecutable.toString(),
            "-cp", classpath,
            "ch.ehi.ili2pg.PgMain",
            "--dbhost", "localhost",
            "--dbport", "54321",
            "--dbdatabase", "edit",
            "--dbusr", "postgres",
            "--dbpwd", "secret",
            "--defaultSrsCode", "2056",
            "--createFk",
            "--nameByTopic",
            "--strokeArcs",
            "--smart2Inheritance",
            "--createEnumTabs",
            "--modeldir", GETTING_STARTED_MODEL_FILE.getParent().toString(),
            "--models", "GsSimpleModel",
            "--dbschema", schemaName
        );
        List<String> schemaCommand = new ArrayList<>(commonArguments);
        schemaCommand.add("--schemaimport");
        CommandResult schemaResult = runCommandResult(Path.of("."), schemaCommand, COMMAND_TIMEOUT);
        if (schemaResult.exitCode() != 0) {
            throw new IOException("ili2pg schema import failed for GsSimpleModel (exit "
                + schemaResult.exitCode() + "):\n" + schemaResult.output());
        }

        List<String> dataCommand = new ArrayList<>(commonArguments);
        dataCommand.add("--import");
        dataCommand.add(GETTING_STARTED_DATA_FILE.toString());
        CommandResult dataResult = runCommandResult(Path.of("."), dataCommand, COMMAND_TIMEOUT);
        if (dataResult.exitCode() != 0) {
            throw new IOException("ili2pg data import failed for GsSimpleModel (exit "
                + dataResult.exitCode() + "):\n" + dataResult.output());
        }
    }

    private void assertGettingStartedPhysicalModel(String schemaName) throws SQLException {
        String columnSql = """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_schema = ?
              AND table_name = 'organization_employee'
              AND column_name = 't_basket'
            """;
        String associationTableSql = """
            SELECT count(*)
            FROM information_schema.tables
            WHERE table_schema = ?
              AND lower(table_name) LIKE '%departmentemployee%'
            """;
        try (Connection connection = DriverManager.getConnection(baseJdbcUrl());
             PreparedStatement columnStatement = connection.prepareStatement(columnSql);
             PreparedStatement tableStatement = connection.prepareStatement(associationTableSql)) {
            columnStatement.setString(1, schemaName);
            tableStatement.setString(1, schemaName);
            assertThat(singleCount(columnStatement))
                .as("basket-free Getting-Started employee table")
                .isZero();
            assertThat(singleCount(tableStatement))
                .as("no synthetic DepartmentEmployee link table")
                .isZero();
        }
    }

    private String employeeDepartmentId(String schemaName, String employeeId) {
        try {
            String sql = "SELECT department FROM " + quoteIdentifier(schemaName)
                + ".\"organization_employee\" WHERE t_id = ?";
            try (Connection connection = DriverManager.getConnection(baseJdbcUrl());
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, Long.parseLong(employeeId));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new AssertionError("Employee #" + employeeId + " not found");
                    }
                    return Long.toString(result.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not read Employee.department", e);
        }
    }

    private long singleCount(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Count query returned no row");
            }
            return result.getLong(1);
        }
    }

    private static void startComposeDb() throws IOException, InterruptedException {
        CommandResult result = runCommandResult(
            Path.of("."),
            List.of("docker", "compose", "up", "-d", "edit-db"),
            Duration.ofMinutes(3)
        );
        if (result.exitCode() != 0) {
            throw new TestAbortedException("Could not start docker compose edit-db: " + result.output());
        }
    }

    private static void waitForDatabase() throws InterruptedException {
        long deadline = System.nanoTime() + DB_TIMEOUT.toNanos();
        SQLException lastError = null;
        while (System.nanoTime() < deadline) {
            try (Connection ignored = DriverManager.getConnection(baseJdbcUrl())) {
                return;
            } catch (SQLException e) {
                lastError = e;
                Thread.sleep(1000);
            }
        }
        throw new TestAbortedException("PostGIS database not reachable at " + baseJdbcUrl(), lastError);
    }

    private static void dropSchema(String schemaName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(baseJdbcUrl());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    private static CommandResult runCommandResult(Path workingDir, List<String> command, Duration timeout)
        throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);
        Path outputFile = Files.createTempFile("ili2grails-command-", ".log");
        builder.redirectOutput(outputFile.toFile());
        Process process = builder.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
        }
        String output = Files.readString(outputFile, StandardCharsets.UTF_8);
        Files.deleteIfExists(outputFile);
        return new CommandResult(finished ? process.exitValue() : -1, output);
    }

    private static void runCommand(Path workingDir, List<String> command, Duration timeout)
        throws IOException, InterruptedException {
        CommandResult result = runCommandResult(workingDir, command, timeout);
        if (result.exitCode() != 0) {
            throw new IOException("Command failed (exit " + result.exitCode() + "): "
                + String.join(" ", command) + "\nOutput:\n" + result.output());
        }
    }

    private static boolean isCommandAvailable(List<String> command) throws IOException, InterruptedException {
        return runCommandResult(Path.of("."), command, Duration.ofSeconds(30)).exitCode() == 0;
    }

    private static Path ili2pgHome() {
        return Path.of(System.getProperty("ili2pgHome", "/Users/stefan/apps/ili2pg-5.5.1"));
    }

    private static String baseJdbcUrl() {
        String jdbcUrl = System.getProperty("browserE2eJdbcUrl");
        return jdbcUrl == null || jdbcUrl.isBlank() ? DEFAULT_JDBC_URL : jdbcUrl;
    }

    private static String externalAppUrl() {
        String appUrl = System.getProperty("browserE2eAppUrl");
        if (appUrl == null || appUrl.isBlank()) {
            return null;
        }
        return appUrl.replaceAll("/+$", "");
    }

    private static String jdbcQueryValue(String primaryKey, String fallbackKey, String defaultValue) {
        String jdbcUrl = baseJdbcUrl();
        int queryStart = jdbcUrl.indexOf('?');
        if (queryStart < 0) {
            return defaultValue;
        }
        String query = jdbcUrl.substring(queryStart + 1);
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            String key = separator >= 0 ? part.substring(0, separator) : part;
            String value = separator >= 0 ? part.substring(separator + 1) : "";
            if (primaryKey.equalsIgnoreCase(key) || (fallbackKey != null && fallbackKey.equalsIgnoreCase(key))) {
                return value.isBlank() ? defaultValue : value;
            }
        }
        return defaultValue;
    }

    private static String uniqueSchemaName(String prefix) {
        return prefix + Long.toUnsignedString(System.nanoTime(), 36);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}

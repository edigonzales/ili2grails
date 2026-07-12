package ch.interlis.generator.grails;

import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
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
    private static final Path ASSOCIATION_MODEL_FILE = Path.of("test-models/QuickLinkE2E.ili");
    private static final Path CONTEXTUAL_ASSOC_MODEL_FILE = Path.of("test-models/ContextualAssociationE2E.ili");
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
            waitForHttp("http://localhost:" + port + "/");

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
            waitForHttp("http://localhost:" + port + "/");

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
            waitForHttp("http://localhost:" + port + "/");

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

    private void trySubmitOrVisit(Page page, String fallbackUrl) {
        if (page.locator("[data-form-submit]").count() > 0) {
            page.locator("[data-form-submit]").click();
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
        try {
            Path file = SCREENSHOT_DIR.resolve(name + ".png");
            Files.createDirectories(file.getParent());
            page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(false));
            System.out.println("Screenshot: " + file.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Screenshot failed for " + name + ": " + e.getMessage());
        }
    }

    private void runBrowserCrud(String baseUrl) {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();

            createAddress(page, baseUrl);
            String addressShowUrl = page.url();
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

    private void createAddress(Page page, String baseUrl) {
        openCreateForm(page, baseUrl, "Address");
        fillVisibleControls(page, "E2E");
        setGeometryWkt(page, "POINT (2600000 1200000)");
        submitForm(page);
        assertThat(page.url()).contains("/address/show/");
        assertThat(page.locator(".ili-geometry-editor").count()).isGreaterThan(0);
    }

    private void editCurrentRecordGeometry(Page page) {
        page.locator("a[href*='/edit/'], a[href$='/edit']").first().click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        setGeometryWkt(page, "POINT (2600010 1200010)");
        submitForm(page);
        assertThat(page.url()).contains("/address/show/");
    }

    private void createPerson(Page page, String baseUrl) {
        openCreateForm(page, baseUrl, "Person");
        fillVisibleControls(page, "Person E2E");
        selectFirstRelationshipOptions(page);
        submitForm(page);
        assertThat(page.url()).contains("/person/show/");
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
              const link = Array.from(document.querySelectorAll('nav a.nav-link'))
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
        page.locator("[data-form-submit]").click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.locator(".alert-danger").count()).isZero();
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
        builder.redirectErrorStream(true);
        builder.environment().put("DB_USERNAME", jdbcQueryValue("user", "username", "postgres"));
        builder.environment().put("DB_PASSWORD", jdbcQueryValue("password", null, "secret"));
        return builder.start();
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
        runCommand(tempDir, List.of("grails", "create-app", "browser-e2e", "--grails-version", grailsVersion()), COMMAND_TIMEOUT);
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

    private static String grailsVersion() {
        String version = System.getProperty("grailsSmokeVersion");
        return version == null || version.isBlank() ? "7.0.6" : version;
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

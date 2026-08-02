package ch.interlis.generator.grails;

import ch.interlis.generator.grails.verification.contract.CommandRunner;
import ch.interlis.generator.grails.verification.environment.ExternalToolStatus;
import ch.interlis.generator.grails.verification.environment.InfrastructureSupport;
import ch.interlis.generator.grails.verification.environment.VerificationEnvironmentDetector;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrailsRuntimeSmokeTest {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(8);
    private static final String APP_NAME = "runtime-smoke";
    private static final String BASE_PACKAGE = "com.example";
    private static final String DOMAIN_PACKAGE = "com.example.domain";
    private static final String ENUM_PACKAGE = "com.example.enums";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/smoke?user=smoke&password=smoke";

    @TempDir
    Path tempDir;

    @BeforeAll
    static void requireGrailsCli() throws Exception {
        boolean required = InfrastructureSupport.required("grailsRuntimeSmokeRequired");
        ExternalToolStatus status = new VerificationEnvironmentDetector().detectGrails(new CommandRunner());
        InfrastructureSupport.requireTool(status, required, "grails runtime smoke test");
        RuntimeApiTestSupport.publishRuntimeToMavenLocal(Path.of("."));
    }

    @Test
    void regenerationIsIdempotentAndUserModificationsBlock() throws Exception {
        Path appDir = createGrailsApp();
        ModelMetadata metadata = collisionMetadata();
        GenerationConfig config = grailsConfig(appDir, true);

        new GrailsTemplateOverlayInstaller().install(appDir, config);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();

        // 1. Erste Generation
        generator.generate(metadata, config);
        String projectHash = projectHash(appDir);
        Path manifest = appDir.resolve(".ili2grails/generation-manifest.json");
        assertThat(manifest).exists();

        // 2. Zweite identische Generation: keine mutierenden Aktionen
        ch.interlis.generator.grails.project.plan.GenerationPlan secondPlan =
            generator.plan(metadata, config);
        assertThat(secondPlan.hasBlockingDiagnostics()).isFalse();
        assertThat(secondPlan.mutatingChanges())
            .as("second identical generation must not mutate")
            .isEmpty();
        assertThat(projectHash(appDir)).isEqualTo(projectHash(appDir));

        // 3. Domain-Datei manuell verändern -> neue Generation blockiert
        //    vollständig, keine andere Datei wird geändert.
        Path domainFile = appDir.resolve(
            "grails-app/domain/com/example/domain/TopicAGebaeude.groovy");
        Files.writeString(domainFile, "class TopicAGebaeude { String hacked = 'x' }");
        String manifestBefore = Files.readString(manifest);
        String registryBefore = Files.readString(appDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisUiRegistry.groovy"));

        assertThatThrownBy(() -> generator.generate(metadata, config))
            .isInstanceOf(ch.interlis.generator.grails.GrailsGenerationBlockedException.class)
            .hasMessageContaining("no project files were changed");
        assertThat(Files.readString(manifest)).isEqualTo(manifestBefore);
        assertThat(Files.readString(appDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisUiRegistry.groovy")))
            .isEqualTo(registryBefore);
    }

    @Test
    void generatedDomainsAndEnumsCompileInRealGrailsApp() throws Exception {
        Path appDir = createGrailsApp();
        ModelMetadata metadata = collisionMetadata();
        GenerationConfig config = grailsConfig(appDir, true);

        new GrailsTemplateOverlayInstaller().install(appDir, config);
        new GrailsCrudGenerator().generate(metadata, config);

        String buildGradle = Files.readString(appDir.resolve("build.gradle"));
        assertThat(buildGradle).contains("org.locationtech.jts:jts-core:1.19.0");
        assertThat(buildGradle).contains("org.postgresql:postgresql:42.7.7");
        assertThat(buildGradle).contains("org.hibernate:hibernate-spatial:5.6.15.Final");

        runCommand(appDir, List.of("./gradlew", "compileGroovy"));
    }

    @Test
    void generateAllUsesGeneratedDomainClassNamesAndCompiles() throws Exception {
        Path appDir = createGrailsApp();
        ModelMetadata metadata = simpleMetadata();
        GenerationConfig config = grailsConfig(appDir, false, true);

        new GrailsTemplateOverlayInstaller().install(appDir, config);
        new GrailsCrudGenerator().generate(metadata, config);

        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        ClassMetadata personAddress = metadata.getClass("SmokeModel.People.PersonAddress");
        String domainClass = DOMAIN_PACKAGE + "." + registry.className(personAddress);

        runCommand(appDir, List.of("./grailsw", "generate-all", domainClass));
        Path generatedForm = appDir.resolve("grails-app/views")
            .resolve(registry.viewPath(personAddress))
            .resolve("_form.gsp");
        assertThat(Files.readString(generatedForm)).contains("form-section", "submitMode", "saveAndContinue");
        assertThat(Files.readString(generatedForm.getParent().resolve("_form-section.gsp")))
            .contains("relationship-fields", "<f:field");
        String generatedCreate = Files.readString(generatedForm.getParent().resolve("create.gsp"));
        assertThat(generatedCreate)
            .contains("pageSubtitle: message(")
            .doesNotContain("pageSubtitle: ${message");
        String generatedEdit = Files.readString(generatedForm.getParent().resolve("edit.gsp"));
        assertThat(generatedEdit)
            .contains("pageSubtitle: message(")
            .doesNotContain("pageSubtitle: ${message");
        runCommand(appDir, List.of("./gradlew", "compileGroovy"));
    }

    @Test
    void associationRegistryAndRuntimeCompilesInRealGrailsApp() throws Exception {
        Path appDir = createGrailsApp();
        ModelMetadata metadata = simpleMetadata();
        GenerationConfig config = grailsConfig(appDir, false, true);

        new GrailsTemplateOverlayInstaller().install(appDir, config);
        new GrailsCrudGenerator().generate(metadata, config);

        Path registryFile = appDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisAssociationRegistry.groovy");
        assertThat(registryFile).exists();

        Path uiRegistryFile = appDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisUiRegistry.groovy");
        assertThat(uiRegistryFile).exists();

        // Runtime artefacts must NOT be copied into the app anymore; they come
        // from the ili2grails-runtime plugin.
        Path runtimeSourceDir = appDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime");
        assertThat(runtimeSourceDir).doesNotExist();
        assertThat(appDir.resolve(
            "grails-app/services/ch/interlis/generator/grails/runtime"
        )).doesNotExist();
        assertThat(appDir.resolve(
            "grails-app/controllers/ch/interlis/generator/grails/runtime/InterlisUiController.groovy"
        )).doesNotExist();
        assertThat(appDir.resolve(
            "grails-app/taglib/ch/interlis/generator/grails/runtime/InterlisUiTagLib.groovy"
        )).doesNotExist();
        // UI-Views sind seit P2-D014 generator-managed app-lokal (Grails 7
        // kann Plugin-JAR-Views im Dev-Modus nicht auflösen); Runtime-KLASSEN
        // kommen weiterhin ausschliesslich aus dem Plugin-JAR.
        assertThat(appDir.resolve("grails-app/views/interlisUi/index.gsp")).exists();
        // JS/CSS der Runtime sind seit P2-D014 ebenfalls generator-managed
        // app-lokal (Grails-7-Dev-Mode-Einschränkung), damit die App im
        // bootRun-Modus die Shell rendern kann.
        assertThat(appDir.resolve("grails-app/assets/javascripts/ili-navigation.js")).exists();
        assertThat(appDir.resolve("grails-app/assets/stylesheets/ili-modern.css")).exists();

        Path associationSectionsGsp = appDir.resolve(
            "src/main/templates/scaffolding/_association-sections.gsp");
        assertThat(associationSectionsGsp).exists();

        Path associationRowActionsGsp = appDir.resolve(
            "src/main/templates/scaffolding/_association-row-actions.gsp");
        assertThat(associationRowActionsGsp).exists();

        Path associationQuickAddGsp = appDir.resolve(
            "src/main/templates/scaffolding/_association-quick-add.gsp");
        assertThat(associationQuickAddGsp).exists();
        assertThat(appDir.resolve(
            "src/main/templates/scaffolding/_inverse-relationship-sections.gsp"
        )).exists();
        assertThat(appDir.resolve(
            "src/main/templates/scaffolding/_inverse-relationship-picker.gsp"
        )).exists();
        assertThat(appDir.resolve("src/main/templates/scaffolding/_list-filters.gsp")).exists();
        assertThat(appDir.resolve("src/main/templates/scaffolding/_list-table.gsp")).exists();

        runCommand(appDir, List.of("./gradlew", "compileGroovy"));

        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        ClassMetadata personAddress = metadata.getClass("SmokeModel.People.PersonAddress");
        String domainClass = DOMAIN_PACKAGE + "." + registry.className(personAddress);
        runCommand(appDir, List.of("./grailsw", "generate-all", domainClass));

        String showGsp = Files.readString(appDir.resolve("grails-app/views")
            .resolve(registry.viewPath(personAddress))
            .resolve("show.gsp"));
        assertThat(showGsp)
            .contains("/interlisUi/workspace-header")
            .contains("/interlisUi/workspace-details")
            .contains("/interlisUi/workspace-relationships")
            .contains("/interlisUi/workspace-danger-zone")
            .contains("association-sections")
            .contains("inverse-relationship-sections")
            .doesNotContain("Audit", "Verlauf", "Protokoll", "Timeline", "Restore");

        // generate-all must render the association partials into the view folder
        // (proves the templates survive scaffolding-time evaluation without errors).
        Path viewDir = appDir.resolve("grails-app/views").resolve(registry.viewPath(personAddress));
        String listGsp = Files.readString(viewDir.resolve("index.gsp"));
        assertThat(listGsp).contains("list-filters").contains("list-table").contains("list-pagination");
        Path generatedSections = viewDir.resolve("_association-sections.gsp");
        Path generatedQuickAdd = viewDir.resolve("_association-quick-add.gsp");
        Path generatedInverseSections = viewDir.resolve("_inverse-relationship-sections.gsp");
        Path generatedInversePicker = viewDir.resolve("_inverse-relationship-picker.gsp");
        assertThat(generatedSections).exists();
        assertThat(generatedQuickAdd).exists();
        assertThat(generatedInverseSections).exists();
        assertThat(generatedInversePicker).exists();
        String generatedQuickAddContent = Files.readString(generatedQuickAdd);
        assertThat(generatedQuickAddContent).contains("associationCreate");
        assertThat(generatedQuickAddContent).contains("data-relationship-context");
        assertThat(generatedQuickAddContent).doesNotContain("raw(");
        assertThat(Files.readString(generatedInversePicker))
            .contains("relationshipAssign")
            .contains("data-inverse-reassignment-modal")
            .doesNotContain("raw(");
    }

    @Test
    void listQuerySupportExecutesSearchFiltersSortAndPagingAgainstH2() throws Exception {
        Path appDir = createGrailsApp();
        ModelMetadata metadata = listQueryMetadata();
        GenerationConfig config = GenerationConfig.builder(appDir, BASE_PACKAGE)
            .domainPackage(DOMAIN_PACKAGE)
            .controllerPackage(BASE_PACKAGE)
            .enumPackage(ENUM_PACKAGE)
            .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
            .mapEditor(GenerationConfig.MAP_EDITOR_NONE)
            .geometryEnabled(false)
            .build();

        new GrailsTemplateOverlayInstaller().install(appDir, config);
        new GrailsCrudGenerator().generate(metadata, config);
        generateScaffolding(appDir, metadata, config);
        Path applicationYaml = appDir.resolve("grails-app/conf/application.yml");
        String h2Configuration = Files.readString(applicationYaml)
            .replace("dbCreate: \"update\"", "dbCreate: \"create-drop\"")
            .replace("org.hibernate.dialect.PostgreSQLDialect", "org.hibernate.dialect.H2Dialect");
        Files.writeString(applicationYaml, h2Configuration);
        Files.writeString(appDir.resolve("grails-app/conf/application-test.yml"), """
            environments:
                test:
                    dataSource:
                        dbCreate: create-drop
            """);
        Files.createDirectories(appDir.resolve("src/integration-test/groovy/com/example"));
        Files.writeString(appDir.resolve(
            "src/integration-test/groovy/com/example/ListQueryIntegrationSpec.groovy"), listQueryIntegrationSpec());

        runCommand(appDir, List.of("./gradlew", "integrationTest", "--tests",
            "com.example.ListQueryIntegrationSpec", "--no-daemon"), COMMAND_TIMEOUT);
    }

    @Test
    void inverseRelationshipAssignmentExecutesAgainstH2() throws Exception {
        Path appDir = createGrailsApp();
        ModelMetadata metadata = inverseRelationshipMetadata();
        GenerationConfig config = GenerationConfig.builder(appDir, BASE_PACKAGE)
            .domainPackage(DOMAIN_PACKAGE)
            .controllerPackage(BASE_PACKAGE)
            .enumPackage(ENUM_PACKAGE)
            .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
            .mapEditor(GenerationConfig.MAP_EDITOR_NONE)
            .geometryEnabled(false)
            .build();

        new GrailsTemplateOverlayInstaller().install(appDir, config);
        new GrailsCrudGenerator().generate(metadata, config);
        generateScaffolding(appDir, metadata, config);
        String h2Configuration = Files.readString(appDir.resolve("grails-app/conf/application.yml"))
            .replace("dbCreate: \"update\"", "dbCreate: \"create-drop\"")
            .replace("org.hibernate.dialect.PostgreSQLDialect", "org.hibernate.dialect.H2Dialect");
        Files.writeString(appDir.resolve("grails-app/conf/application.yml"), h2Configuration);
        Files.writeString(appDir.resolve("grails-app/conf/application-test.yml"), """
            environments:
                test:
                    dataSource:
                        dbCreate: create-drop
            """);
        Files.createDirectories(appDir.resolve("src/integration-test/groovy/com/example"));
        Files.writeString(appDir.resolve(
            "src/integration-test/groovy/com/example/InverseRelationshipIntegrationSpec.groovy"
        ), inverseRelationshipIntegrationSpec());

        runCommand(appDir, List.of("./gradlew", "integrationTest", "--tests",
            "com.example.InverseRelationshipIntegrationSpec", "--no-daemon"), COMMAND_TIMEOUT);
    }

    @Test
    void multiDomainWorkspaceRendersExplicitRelatedSectionsAndNavigationOnH2() throws Exception {
        Path appDir = createGrailsApp();
        ModelMetadata metadata = MultiDomainWorkspaceFixture.referenceMetadata();
        GenerationConfig config = GenerationConfig.builder(appDir, BASE_PACKAGE)
            .domainPackage(DOMAIN_PACKAGE)
            .controllerPackage(BASE_PACKAGE)
            .enumPackage(ENUM_PACKAGE)
            .uiTheme(GenerationConfig.UI_THEME_BOOTSTRAP)
            .mapEditor(GenerationConfig.MAP_EDITOR_NONE)
            .geometryEnabled(false)
            .build();

        new GrailsTemplateOverlayInstaller().install(appDir, config);
        new GrailsCrudGenerator().generate(metadata, config);
        MultiDomainWorkspaceFixture.install(appDir);
        generateScaffolding(appDir, metadata, config);

        String h2Configuration = Files.readString(appDir.resolve("grails-app/conf/application.yml"))
            .replace("dbCreate: \"update\"", "dbCreate: \"create-drop\"")
            .replace("org.hibernate.dialect.PostgreSQLDialect", "org.hibernate.dialect.H2Dialect");
        Files.writeString(appDir.resolve("grails-app/conf/application.yml"), h2Configuration);
        Files.writeString(appDir.resolve("grails-app/conf/application-test.yml"), """
            environments:
                test:
                    dataSource:
                        dbCreate: create-drop
            """);
        Files.createDirectories(appDir.resolve("src/integration-test/groovy/com/example"));
        Files.writeString(appDir.resolve(
            "src/integration-test/groovy/com/example/ParcelWorkspaceIntegrationSpec.groovy"),
            multiDomainWorkspaceIntegrationSpec());

        runCommand(appDir, List.of("./gradlew", "integrationTest", "--tests",
            "com.example.ParcelWorkspaceIntegrationSpec", "--no-daemon"), COMMAND_TIMEOUT);
    }

    private String projectHash(Path root) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var files = Files.walk(root)) {
            List<Path> regularFiles = files.filter(Files::isRegularFile)
                .filter(path -> !path.startsWith(root.resolve("build")))
                .filter(path -> !path.startsWith(root.resolve(".gradle")))
                .sorted()
                .toList();
            for (Path file : regularFiles) {
                digest.update(root.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(file));
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private Path createGrailsApp() throws Exception {
        runCommand(tempDir, List.of("grails", "create-app", APP_NAME));
        Path appDir = tempDir.resolve(APP_NAME);
        appDir.resolve("gradlew").toFile().setExecutable(true);
        appDir.resolve("grailsw").toFile().setExecutable(true);
        assertThat(appDir.resolve("build.gradle")).exists();
        assertThat(appDir.resolve("grailsw")).exists();
        RuntimeApiTestSupport.installRuntimePluginDependency(appDir);
        return appDir;
    }

    private ModelMetadata listQueryMetadata() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("ListQueryModel");

        modelBuilder.enumBuilder("ListQueryModel.RecordStatus")
            .value("ACTIVE", 0)
            .value("ARCHIVED", 1)
            .value("DRAFT", 2);

        modelBuilder.classBuilder("ListQueryModel.Municipality")
            .tableName("list_municipality")
            .attribute(new AttributeMetadataBuilder("name")
                .javaType("String")
                .columnName("list_name")
                .mandatory(true));

        modelBuilder.classBuilder("ListQueryModel.Record")
            .tableName("list_record")
            .attribute(new AttributeMetadataBuilder("name")
                .javaType("String")
                .columnName("list_name")
                .mandatory(true))
            .attribute(new AttributeMetadataBuilder("status")
                .javaType("String")
                .enumType("ListQueryModel.RecordStatus"))
            .attribute(new AttributeMetadataBuilder("active")
                .javaType("Boolean")
                .columnName("is_active"))
            .attribute(new AttributeMetadataBuilder("year")
                .javaType("Integer")
                .columnName("list_year")
                .minValue("1900")
                .maxValue("2200"))
            .attribute(new AttributeMetadataBuilder("validFrom")
                .javaType("java.time.LocalDate")
                .columnName("valid_from"))
            .attribute(new AttributeMetadataBuilder("municipality")
                .javaType("Long")
                .foreignKey(true)
                .referencedClass("ListQueryModel.Municipality"));

        modelBuilder.relationship(RelationshipMetadata.builder("Record_Municipality")
            .sourceClass("ListQueryModel.Record")
            .targetClass("ListQueryModel.Municipality")
            .sourceAttribute("municipality")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE));

        return new ModelMetadataFactory().buildValidated(modelBuilder);
    }

    private void generateScaffolding(Path appDir, ModelMetadata metadata, GenerationConfig config)
        throws IOException, InterruptedException {
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            if (classMetadata.isAbstract()) {
                continue;
            }
            runCommand(appDir, List.of("./grailsw", "generate-all",
                DOMAIN_PACKAGE + "." + registry.className(classMetadata)), COMMAND_TIMEOUT);
        }
        runCommand(appDir, List.of("./gradlew", "compileGroovy"), COMMAND_TIMEOUT);
    }

    private String listQueryIntegrationSpec() {
        return """
            package com.example

            import com.example.domain.Municipality
            import com.example.domain.Record
            import com.example.enums.RecordStatus
            import ch.interlis.generator.grails.runtime.InterlisListQuerySupport
            import ch.interlis.generator.grails.runtime.InterlisFormSupport
            import ch.interlis.generator.grails.runtime.InterlisRelationshipOptions
            import ch.interlis.generator.grails.runtime.InterlisUiDescriptorSupport
            import ch.interlis.generator.grails.runtime.InterlisWorkspaceSupport
            import grails.testing.mixin.integration.Integration
            import grails.gorm.transactions.Rollback
            import org.springframework.dao.DataIntegrityViolationException
            import spock.lang.Specification

            import java.time.LocalDate

            @Integration
            @Rollback
            class ListQueryIntegrationSpec extends Specification {

                def grailsApplication

                def "executes safe descriptor-driven list criteria on H2"() {
                    given:
                    def saveIt = { entity ->
                        try { return entity.save(failOnError: true) }
                        catch (Throwable failure) { failure.printStackTrace(); throw failure }
                    }
                    def bern = saveIt(new Municipality(name: 'Bern'))
                    def zurich = saveIt(new Municipality(name: 'Zürich'))
                    new Record(name: 'Bahnhof Bern', status: RecordStatus.ACTIVE, active: true,
                        year: 2024, validFrom: LocalDate.of(2024, 1, 1), municipality: bern).with { saveIt(it) }
                    new Record(name: 'Archiv Zürich', status: RecordStatus.ARCHIVED, active: false,
                        year: 2020, validFrom: LocalDate.of(2020, 1, 1), municipality: zurich).with { saveIt(it) }
                    def descriptor = InterlisUiDescriptorSupport.descriptor(grailsApplication, Record)
                    def workspace = InterlisWorkspaceSupport.showModel(
                        grailsApplication, Record, Record.findByName('Bahnhof Bern'), descriptor)
                    when:
                    def query = InterlisListQuerySupport.parse([
                        q: 'Bern', max: '10', offset: '0', sort: 'year', order: 'desc',
                        filter: [active: 'true', year: [min: '2024', max: '2024'],
                                 validFrom: [from: '2024-01-01', to: '2024-12-31'], municipality: bern.id.toString()]
                    ], descriptor)
                    def result = InterlisListQuerySupport.page(Record, Record, descriptor, query)

                    then:
                    result.total == 1
                    result.records*.name == ['Bahnhof Bern']
                    query.warnings.isEmpty()
                    workspace.workspaceDisplayLabel == 'Bahnhof Bern'
                    workspace.workspaceDetailSections.toString().contains('name')
                    workspace.workspaceDetailSections.toString().contains('municipality')
                    workspace.workspaceDetailSections.toString().contains('link')
                    workspace.workspaceRelationshipLinks.isEmpty()

                    when:
                    Municipality.withSession { session ->
                        session.delete(bern)
                        session.flush()
                    }
                    def danglingQuery = InterlisListQuerySupport.page(
                        Record, Record, descriptor, query)

                    then:
                    // P0-B: normale inverse MANY_TO_ONE-Referenzen erzeugen keine
                    // synthetische hasMany-Join-Tabelle mehr. Die FK-Integrität wird
                    // im echten ili2pg-Schema (--createFk) von der Datenbank
                    // durchgesetzt; H2 (GORM create-drop) exportiert keine
                    // many-to-one-FK-Constraints. Die Navigation schlägt hier laut
                    // fehl (EntityNotFoundException) statt still zu verfälschen.
                    thrown(jakarta.persistence.EntityNotFoundException)
                }

                def "builds sectioned form state and preserves submitted relationship selection"() {
                    given:
                    def municipality = new Municipality(name: 'Form Bern').save(failOnError: true)
                    def descriptor = InterlisUiDescriptorSupport.descriptor(grailsApplication, Record)
                    def invalid = new Record(name: 'Retained value', year: 1800, municipality: municipality)

                    when:
                    def viewModel = InterlisFormSupport.formViewModel(descriptor, [
                        relationshipValues: [municipality: municipality.id.toString()],
                        submittedValues: [name: invalid.name]
                    ])
                    def selected = InterlisRelationshipOptions.optionForId(
                        grailsApplication, Record, 'municipality', municipality.id.toString(), [])

                    then:
                    !invalid.validate()
                    invalid.errors.hasFieldErrors('year')
                    viewModel.formSections*.title == ['Basisdaten']
                    viewModel.relationshipValues.municipality == municipality.id.toString()
                    viewModel.submittedValues.name == 'Retained value'
                    selected.id == municipality.id.toString()
                    selected.label == 'Form Bern'
                    InterlisFormSupport.submitMode('unexpected') == 'save'
                    InterlisFormSupport.continueRedirect(invalid, [contextId: 'ctx', ownerId: municipality.id]).params == [
                        associationContext: 'ctx', associationOwnerId: municipality.id
                    ]
                }
            }
            """;
    }

    private String inverseRelationshipIntegrationSpec() {
        return """
            package com.example

            import com.example.domain.Department
            import com.example.domain.Employee
            import ch.interlis.generator.grails.runtime.InterlisInverseRelationshipContextSupport
            import grails.testing.mixin.integration.Integration
            import grails.gorm.transactions.Rollback
            import spock.lang.Specification

            @Integration
            @Rollback
            class InverseRelationshipIntegrationSpec extends Specification {

                def interlisInverseRelationshipQueryService
                def interlisInverseRelationshipCommandService
                def grailsApplication
                def sessionFactory

                def "validates and reapplies a direct relationship create context"() {
                    given:
                    def planning = new Department(name: 'Planning').save(failOnError: true, flush: true)

                    when:
                    def context = InterlisInverseRelationshipContextSupport.prepareCreateContext(
                        grailsApplication, Employee, [
                            relationshipField: 'department',
                            relationshipOwnerId: planning.id.toString()
                        ]
                    )
                    def employee = new Employee(firstName: 'Context', lastName: 'Created')
                    InterlisInverseRelationshipContextSupport.applyFixedRelationship(employee, context)

                    then:
                    context.contextKind == 'DIRECT_RELATIONSHIP'
                    context.ownerId == planning.id
                    employee.department.id == planning.id

                    when:
                    InterlisInverseRelationshipContextSupport.prepareCreateContext(
                        grailsApplication, Employee, [
                            relationshipField: 'department',
                            relationshipOwnerId: '999999999'
                        ]
                    )

                    then:
                    thrown(IllegalArgumentException)
                }

                def "requires confirmation and then moves the existing employee"() {
                    given:
                    def hr = new Department(name: 'HR').save(failOnError: true, flush: true)
                    def operations = new Department(name: 'Operations').save(failOnError: true, flush: true)
                    def employee = new Employee(
                        firstName: 'Anna', lastName: 'Keller', department: hr
                    ).save(failOnError: true, flush: true)
                    def unassigned = new Employee(
                        firstName: 'Bea', lastName: 'Meier'
                    ).save(failOnError: true, flush: true)

                    when:
                    def sections = interlisInverseRelationshipQueryService.sections(
                        Department, operations.id, 10)
                    def options = interlisInverseRelationshipQueryService.optionPage(
                        Department, operations.id, 'employees', 'Anna', 25, 0)
                    def needsConfirmation = interlisInverseRelationshipCommandService.assign(
                        Department, operations.id, 'employees', employee.id, false)
                    def firstAssignment = interlisInverseRelationshipCommandService.assign(
                        Department, operations.id, 'employees', unassigned.id, false)
                    sessionFactory.currentSession.clear()

                    then:
                    sections*.name == ['employees']
                    sections[0].total == 0
                    options.results*.id == [employee.id.toString()]
                    options.results[0].label.contains('HR')
                    needsConfirmation.httpStatus() == 409
                    needsConfirmation.code() ==
                        ch.interlis.generator.grails.runtime.api.command.CommandCode.REASSIGNMENT_CONFIRMATION_REQUIRED
                    firstAssignment.success()
                    firstAssignment.code() ==
                        ch.interlis.generator.grails.runtime.api.command.CommandCode.ASSIGNED
                    Employee.get(employee.id).department.id == hr.id
                    Employee.get(unassigned.id).department.id == operations.id

                    when:
                    def moved = interlisInverseRelationshipCommandService.assign(
                        Department, operations.id, 'employees', employee.id, true)
                    sessionFactory.currentSession.clear()
                    def operationRows = interlisInverseRelationshipQueryService.page(
                        Department, operations.id, 'employees', 10, 0)
                    def repeated = interlisInverseRelationshipCommandService.assign(
                        Department, operations.id, 'employees', employee.id, false)
                    def invalid = interlisInverseRelationshipCommandService.assign(
                        Department, operations.id, 'unknown', employee.id, false)

                    then:
                    moved.success()
                    moved.code() ==
                        ch.interlis.generator.grails.runtime.api.command.CommandCode.REASSIGNED
                    Employee.get(employee.id).department.id == operations.id
                    operationRows.total == 2
                    operationRows.rows*.id.toSet() == [employee.id.toString(), unassigned.id.toString()].toSet()
                    repeated.success()
                    repeated.code() ==
                        ch.interlis.generator.grails.runtime.api.command.CommandCode.ALREADY_ASSIGNED
                    invalid.httpStatus() == 400
                    invalid.code() ==
                        ch.interlis.generator.grails.runtime.api.command.CommandCode.RELATIONSHIP_INVALID
                }
            }
            """;
    }

    private String multiDomainWorkspaceIntegrationSpec() {
        return """
            package com.example

            import com.example.domain.Building
            import com.example.domain.Owner
            import com.example.domain.Parcel
            import ch.interlis.generator.grails.runtime.InterlisNavigationSupport
            import grails.testing.mixin.integration.Integration
            import grails.gorm.transactions.Rollback
            import spock.lang.Specification

            @Integration
            @Rollback
            class ParcelWorkspaceIntegrationSpec extends Specification {

                def grailsApplication
                def parcelWorkspaceService
                def parcelWorkspaceCommandService
                def sessionFactory

                def "renders populated and empty multi-domain workspace sections"() {
                    given:
                    def selected = new Parcel(anumber: 'P-100').save(failOnError: true)
                    def empty = new Parcel(anumber: 'P-200').save(failOnError: true)
                    new Building(aname: 'Haus A', parcel: selected).save(failOnError: true)
                    new Owner(aname: 'Anna Beispiel', parcel: selected).save(failOnError: true)

                    when:
                    def populated = parcelWorkspaceService.showModel(selected.id)
                    def emptyModel = parcelWorkspaceService.showModel(empty.id)
                    def navigation = InterlisNavigationSupport.navigationModel(grailsApplication)

                    then:
                    populated.workspaceRoot.anumber == 'P-100'
                    populated.workspaceDetailSections.toString().contains('anumber')
                    populated.workspaceTableSections*.key == ['buildings', 'owners']
                    populated.workspaceTableSections[0].rows[0].values.name == 'Haus A'
                    populated.workspaceTableSections[0].rows[0].links.name.controller == 'building'
                    populated.workspaceTableSections[0].rows[0].links.name.action == 'show'
                    populated.workspaceTableSections[1].rows[0].values.name == 'Anna Beispiel'
                    populated.workspaceTableSections[1].rows[0].links.name.controller == 'owner'
                    emptyModel.workspaceTableSections.every { it.rows.isEmpty() && it.emptyMessage }
                    navigation.workspaces*.id == ['parcel-workspace']
                    navigation.workspaces[0].controller == 'parcelWorkspace'
                    navigation.domains*.controller.containsAll(['parcel', 'building', 'owner'])
                }

                def "saves parcel building and owner in one command"() {
                    given:
                    def parcel = new Parcel(anumber: 'P-100').save(failOnError: true, flush: true)
                    def building = new Building(aname: 'Haus A', parcel: parcel).save(failOnError: true, flush: true)
                    def owner = new Owner(aname: 'Anna Beispiel', parcel: parcel).save(failOnError: true, flush: true)
                    def command = command(parcel, building, owner, 'P-101', 'Haus B', 'Bea Beispiel')

                    when:
                    parcelWorkspaceCommandService.updateWorkspace(parcel.id, command)
                    sessionFactory.currentSession.clear()

                    then:
                    Parcel.get(parcel.id).anumber == 'P-101'
                    Building.get(building.id).aname == 'Haus B'
                    Owner.get(owner.id).aname == 'Bea Beispiel'
                }

                def "rolls back every part when one nested domain value is invalid"() {
                    given:
                    def parcel = new Parcel(anumber: 'P-100').save(failOnError: true, flush: true)
                    def building = new Building(aname: 'Haus A', parcel: parcel).save(failOnError: true, flush: true)
                    def owner = new Owner(aname: 'Anna Beispiel', parcel: parcel).save(failOnError: true, flush: true)
                    def command = command(parcel, building, owner, 'P-101', 'Haus B', '')

                    when:
                    parcelWorkspaceCommandService.updateWorkspace(parcel.id, command)

                    then:
                    def failure = thrown(ParcelWorkspaceCommandException)
                    failure.code == 'precondition-failed'
                    failure.fieldErrors.keySet().any { it.contains('ownerEdits[0].name') }
                    sessionFactory.currentSession.clear()
                    Parcel.get(parcel.id).anumber == 'P-100'
                    Building.get(building.id).aname == 'Haus A'
                    Owner.get(owner.id).aname == 'Anna Beispiel'
                }

                def "rolls back when a related object has an old optimistic-lock version"() {
                    given:
                    def parcel = new Parcel(anumber: 'P-100').save(failOnError: true, flush: true)
                    def building = new Building(aname: 'Haus A', parcel: parcel).save(failOnError: true, flush: true)
                    def owner = new Owner(aname: 'Anna Beispiel', parcel: parcel).save(failOnError: true, flush: true)
                    def staleVersion = building.version
                    building.aname = 'Concurrent change'
                    building.save(failOnError: true, flush: true)
                    sessionFactory.currentSession.clear()
                    def command = new ParcelWorkspaceCommand(
                        parcelId: parcel.id, parcelVersion: parcel.version, parcelNumber: 'P-101',
                        buildingEdits: [new BuildingEditCommand(id: building.id, version: staleVersion, name: 'Haus B')],
                        ownerEdits: [new OwnerEditCommand(id: owner.id, version: owner.version, name: 'Bea Beispiel')]
                    )

                    when:
                    parcelWorkspaceCommandService.updateWorkspace(parcel.id, command)

                    then:
                    def failure = thrown(ParcelWorkspaceCommandException)
                    failure.code == 'optimistic-locking'
                    sessionFactory.currentSession.clear()
                    Parcel.get(parcel.id).anumber == 'P-100'
                    Building.get(building.id).aname == 'Concurrent change'
                    Owner.get(owner.id).aname == 'Anna Beispiel'
                }

                def "rejects foreign related ids and foreign remove ids"() {
                    given:
                    def parcel = new Parcel(anumber: 'P-100').save(failOnError: true, flush: true)
                    def foreignParcel = new Parcel(anumber: 'P-200').save(failOnError: true, flush: true)
                    def foreignBuilding = new Building(aname: 'Fremdes Haus', parcel: foreignParcel).save(failOnError: true, flush: true)
                    def foreignOwner = new Owner(aname: 'Fremde Person', parcel: foreignParcel).save(failOnError: true, flush: true)
                    def command = new ParcelWorkspaceCommand(
                        parcelId: parcel.id, parcelVersion: parcel.version, parcelNumber: 'P-101',
                        buildingEdits: [new BuildingEditCommand(id: foreignBuilding.id, version: foreignBuilding.version, name: 'Manipuliert')],
                        removedOwnerIds: [foreignOwner.id]
                    )

                    when:
                    parcelWorkspaceCommandService.updateWorkspace(parcel.id, command)

                    then:
                    def failure = thrown(ParcelWorkspaceCommandException)
                    failure.code == 'precondition-failed'
                    failure.fieldErrors.values().any { it.contains('gehört nicht') || it.contains('Fremde') }
                    sessionFactory.currentSession.clear()
                    Parcel.get(parcel.id).anumber == 'P-100'
                    Building.get(foreignBuilding.id).aname == 'Fremdes Haus'
                    Owner.get(foreignOwner.id).aname == 'Fremde Person'
                }

                def "removes only explicitly selected children and keeps omitted children"() {
                    given:
                    def parcel = new Parcel(anumber: 'P-100').save(failOnError: true, flush: true)
                    def changed = new Building(aname: 'Haus A', parcel: parcel).save(failOnError: true, flush: true)
                    def removed = new Building(aname: 'Haus B', parcel: parcel).save(failOnError: true, flush: true)
                    def omitted = new Building(aname: 'Haus C', parcel: parcel).save(failOnError: true, flush: true)
                    def owner = new Owner(aname: 'Anna Beispiel', parcel: parcel).save(failOnError: true, flush: true)
                    def command = new ParcelWorkspaceCommand(
                        parcelId: parcel.id, parcelVersion: parcel.version, parcelNumber: 'P-100',
                        buildingEdits: [new BuildingEditCommand(id: changed.id, version: changed.version, name: 'Haus A neu')],
                        ownerEdits: [new OwnerEditCommand(id: owner.id, version: owner.version, name: owner.aname)],
                        removedBuildingIds: [removed.id]
                    )

                    when:
                    parcelWorkspaceCommandService.updateWorkspace(parcel.id, command)
                    sessionFactory.currentSession.clear()

                    then:
                    Building.get(changed.id).aname == 'Haus A neu'
                    Building.get(removed.id) == null
                    Building.get(omitted.id).aname == 'Haus C'
                    Owner.get(owner.id).aname == 'Anna Beispiel'
                }

                private ParcelWorkspaceCommand command(Parcel parcel, Building building, Owner owner,
                                                       String number, String buildingName, String ownerName) {
                    new ParcelWorkspaceCommand(
                        parcelId: parcel.id, parcelVersion: parcel.version, parcelNumber: number,
                        buildingEdits: [new BuildingEditCommand(id: building.id, version: building.version, name: buildingName)],
                        ownerEdits: [new OwnerEditCommand(id: owner.id, version: owner.version, name: ownerName)]
                    )
                }
            }
            """;
    }

    private GenerationConfig grailsConfig(Path appDir, boolean geometryEnabled) {
        return grailsConfig(appDir, geometryEnabled, geometryEnabled);
    }

    private GenerationConfig grailsConfig(Path appDir, boolean geometryEnabled, boolean bootstrapTheme) {
        return GenerationConfig.builder(appDir, BASE_PACKAGE)
            .domainPackage(DOMAIN_PACKAGE)
            .controllerPackage(BASE_PACKAGE)
            .enumPackage(ENUM_PACKAGE)
            .jdbcUrl(JDBC_URL)
            .schema("public")
            .uiTheme(bootstrapTheme ? GenerationConfig.UI_THEME_BOOTSTRAP : GenerationConfig.UI_THEME_DEFAULT)
            .mapEditor(geometryEnabled ? GenerationConfig.MAP_EDITOR_OPENLAYERS : GenerationConfig.MAP_EDITOR_NONE)
            .geometryEnabled(geometryEnabled)
            .build();
    }

    private static void runCommand(Path workingDir, List<String> command)
        throws IOException, InterruptedException {
        runCommand(workingDir, command, COMMAND_TIMEOUT);
    }

    private static void runCommand(Path workingDir, List<String> command, Duration timeout)
        throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("Command timed out after " + timeout + ": "
                + String.join(" ", command) + "\nOutput:\n" + output);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String testDiagnostics = readTestDiagnostics(workingDir);
            throw new IOException("Command failed (exit " + exitCode + "): "
                + String.join(" ", command) + "\nOutput:\n" + output + testDiagnostics);
        }
    }

    private static String readTestDiagnostics(Path workingDir) {
        Path testResults = workingDir.resolve("build/test-results");
        if (!Files.isDirectory(testResults)) {
            return "";
        }
        try (var files = Files.walk(testResults)) {
            String diagnostics = files
                .filter(path -> path.getFileName().toString().startsWith("TEST-"))
                .filter(path -> path.getFileName().toString().endsWith(".xml"))
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (IOException ignored) {
                        return "";
                    }
                })
                .filter(content -> content.contains("<failure"))
                .collect(java.util.stream.Collectors.joining("\n"));
            if (diagnostics.isBlank()) {
                return "";
            }
            return "\nTest diagnostics:\n"
                + diagnostics.substring(0, Math.min(diagnostics.length(), 20_000));
        } catch (IOException ignored) {
            return "";
        }
    }

    private ModelMetadata collisionMetadata() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("SmokeModel");

        modelBuilder.enumBuilder("SmokeModel.TopicA.Status")
            .value("ACTIVE", 0)
            .value("in.Betrieb", 1)
            .value("class", 2)
            .value("a.b", 3)
            .value("a_b", 4);

        modelBuilder.enumBuilder("SmokeModel.TopicB.Status")
            .value("ACTIVE", 0);

        modelBuilder.classBuilder("SmokeModel.TopicA.Gebaeude")
            .tableName("gebaeude_a")
            .attribute(enumAttribute("status", "SmokeModel.TopicA.Status", true))
            .attribute(geometryAttribute("position"))
            .attribute(textAttribute("display-name", "name"))
            .attribute(textAttribute("primary_name", "name"));

        modelBuilder.classBuilder("SmokeModel.TopicB.Gebaeude")
            .tableName("gebaeude_b")
            .attribute(enumAttribute("status", "SmokeModel.TopicB.Status", true))
            .attribute(new AttributeMetadataBuilder("owner")
                .foreignKey(true)
                .referencedClass("SmokeModel.TopicA.Gebaeude")
                .javaType("Long")
                .mandatory(false));

        modelBuilder.relationship(RelationshipMetadata.builder("TopicB_Gebaeude_owner")
            .sourceClass("SmokeModel.TopicB.Gebaeude")
            .targetClass("SmokeModel.TopicA.Gebaeude")
            .sourceAttribute("owner")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK));

        modelBuilder.classBuilder("SmokeModel.TopicA.Component")
            .kind(ClassMetadata.ClassKind.STRUCTURE)
            .attribute(textAttribute("label", "label"));

        modelBuilder.relationship(RelationshipMetadata.builder("TopicA_Gebaeude_components")
            .sourceClass("SmokeModel.TopicA.Gebaeude")
            .targetClass("SmokeModel.TopicA.Component")
            .sourceAttribute("Components")
            .type(RelationshipMetadata.RelationType.ONE_TO_MANY)
            .semanticKind(RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE)
            .composition(true)
            .cardinality(1, 1, 0, -1));

        modelBuilder.classBuilder("SmokeModel.TopicA.GebaeudeLink")
            .kind(ClassMetadata.ClassKind.ASSOCIATION)
            .tableName("gebaeude_link");

        modelBuilder.relationship(RelationshipMetadata.builder("GebaeudeLink_Source")
            .sourceClass("SmokeModel.TopicA.GebaeudeLink")
            .targetClass("SmokeModel.TopicA.Gebaeude")
            .targetRoleName("Source")
            .type(RelationshipMetadata.RelationType.ASSOCIATION)
            .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .mandatory(true));

        modelBuilder.relationship(RelationshipMetadata.builder("GebaeudeLink_Target")
            .sourceClass("SmokeModel.TopicA.GebaeudeLink")
            .targetClass("SmokeModel.TopicB.Gebaeude")
            .targetRoleName("Target")
            .type(RelationshipMetadata.RelationType.ASSOCIATION)
            .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .mandatory(true));

        return new ModelMetadataFactory().buildValidated(modelBuilder);
    }

    private ModelMetadata simpleMetadata() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("SmokeModel");

        modelBuilder.classBuilder("SmokeModel.People.Person")
            .tableName("person")
            .attribute(textAttribute("firstName", "first_name"));

        modelBuilder.classBuilder("SmokeModel.Addresses.Address")
            .tableName("address")
            .attribute(textAttribute("street", "street"));

        modelBuilder.classBuilder("SmokeModel.People.PersonAddress")
            .kind(ClassMetadata.ClassKind.ASSOCIATION)
            .tableName("person_address");

        modelBuilder.relationship(RelationshipMetadata.builder("PersonAddress_Person")
            .sourceClass("SmokeModel.People.PersonAddress")
            .targetClass("SmokeModel.People.Person")
            .targetRoleName("Person")
            .type(RelationshipMetadata.RelationType.ASSOCIATION)
            .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .mandatory(true));

        modelBuilder.relationship(RelationshipMetadata.builder("PersonAddress_Address")
            .sourceClass("SmokeModel.People.PersonAddress")
            .targetClass("SmokeModel.Addresses.Address")
            .targetRoleName("Address")
            .type(RelationshipMetadata.RelationType.ASSOCIATION)
            .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .mandatory(true));

        return new ModelMetadataFactory().buildValidated(modelBuilder);
    }

    private ModelMetadata inverseRelationshipMetadata() {
        ModelMetadataBuilder modelBuilder = ModelMetadataBuilder.model("InverseRelationshipModel");

        modelBuilder.classBuilder("InverseRelationshipModel.Organization.Department")
            .kind(ClassMetadata.ClassKind.CLASS)
            .tableName("department")
            .attribute(textAttribute("name", "name").mandatory(true));

        modelBuilder.classBuilder("InverseRelationshipModel.Organization.Employee")
            .kind(ClassMetadata.ClassKind.CLASS)
            .tableName("employee")
            .attribute(textAttribute("firstName", "first_name").mandatory(true))
            .attribute(textAttribute("lastName", "last_name").mandatory(true))
            .attribute(new AttributeMetadataBuilder("department")
                .javaType("Long")
                .foreignKey(true)
                .referencedClass("InverseRelationshipModel.Organization.Department")
                .columnName("department")
                .sqlName("department")
                .mandatory(false));

        modelBuilder.relationship(RelationshipMetadata.builder(
                "InverseRelationshipModel.Organization.Employee_Department")
            .sourceClass("InverseRelationshipModel.Organization.Employee")
            .targetClass("InverseRelationshipModel.Organization.Department")
            .sourceAttribute("department")
            .targetRoleName("Department")
            .physicalName("department")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK)
            .mandatory(false));

        return new ModelMetadataFactory().buildValidated(modelBuilder);
    }

    private AttributeMetadataBuilder enumAttribute(String name, String enumType, boolean mandatory) {
        return new AttributeMetadataBuilder(name)
            .enumType(enumType)
            .mandatory(mandatory);
    }

    private AttributeMetadataBuilder geometryAttribute(String name) {
        return new AttributeMetadataBuilder(name)
            .geometry(true)
            .geometryKind("POINT")
            .geometrySrid(2056)
            .javaType("org.locationtech.jts.geom.Geometry")
            .mandatory(false);
    }

    private AttributeMetadataBuilder textAttribute(String name, String sqlName) {
        return new AttributeMetadataBuilder(name)
            .sqlName(sqlName)
            .columnName(sqlName)
            .javaType("String")
            .maxLength(100)
            .mandatory(false);
    }
}

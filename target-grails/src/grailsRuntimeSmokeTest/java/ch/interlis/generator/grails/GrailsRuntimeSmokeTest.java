package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        if (!isGrailsAvailable()) {
            throw new TestAbortedException("grails CLI not available in PATH; skipping runtime smoke test");
        }
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

        Path descriptorSupportFile = appDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisUiDescriptorSupport.groovy");
        assertThat(descriptorSupportFile).exists();
        Path workspaceSupportFile = appDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisWorkspaceSupport.groovy");
        assertThat(workspaceSupportFile).exists();
        Path listQuerySupportFile = appDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisListQuerySupport.groovy");
        assertThat(listQuerySupportFile).exists();

        Path navigationSupportFile = appDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisNavigationSupport.groovy");
        assertThat(navigationSupportFile).exists();

        Path uiControllerFile = appDir.resolve(
            "grails-app/controllers/ch/interlis/generator/grails/runtime/InterlisUiController.groovy");
        assertThat(uiControllerFile).exists();

        Path uiTagLibFile = appDir.resolve(
            "grails-app/taglib/ch/interlis/generator/grails/runtime/InterlisUiTagLib.groovy");
        assertThat(uiTagLibFile).exists();

        Path explorerViewFile = appDir.resolve("grails-app/views/interlisUi/index.gsp");
        assertThat(explorerViewFile).exists();

        Path explorerResultsFile = appDir.resolve("grails-app/views/interlisUi/_explorer-results.gsp");
        assertThat(explorerResultsFile).exists();
        assertThat(appDir.resolve("grails-app/views/interlisUi/_workspace-header.gsp")).exists();
        assertThat(appDir.resolve("grails-app/views/interlisUi/_workspace-details.gsp")).exists();
        assertThat(appDir.resolve("grails-app/views/interlisUi/_workspace-relationships.gsp")).exists();
        assertThat(appDir.resolve("grails-app/views/interlisUi/_workspace-danger-zone.gsp")).exists();

        Path navigationJsFile = appDir.resolve("grails-app/assets/javascripts/ili-navigation.js");
        assertThat(navigationJsFile).exists();

        Path supportFile = appDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisAssociationRegistrySupport.groovy");
        assertThat(supportFile).exists();

        Path queryServiceFile = appDir.resolve(
            "grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationQueryService.groovy");
        assertThat(queryServiceFile).exists();

        Path commandServiceFile = appDir.resolve(
            "grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationCommandService.groovy");
        assertThat(commandServiceFile).exists();

        Path associationSectionsGsp = appDir.resolve(
            "src/main/templates/scaffolding/_association-sections.gsp");
        assertThat(associationSectionsGsp).exists();

        Path associationRowActionsGsp = appDir.resolve(
            "src/main/templates/scaffolding/_association-row-actions.gsp");
        assertThat(associationRowActionsGsp).exists();

        Path associationQuickAddGsp = appDir.resolve(
            "src/main/templates/scaffolding/_association-quick-add.gsp");
        assertThat(associationQuickAddGsp).exists();
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
            .doesNotContain("Audit", "Verlauf", "Protokoll", "Timeline", "Restore");

        // generate-all must render the association partials into the view folder
        // (proves the templates survive scaffolding-time evaluation without errors).
        Path viewDir = appDir.resolve("grails-app/views").resolve(registry.viewPath(personAddress));
        String listGsp = Files.readString(viewDir.resolve("index.gsp"));
        assertThat(listGsp).contains("list-filters").contains("list-table").contains("list-pagination");
        Path generatedSections = viewDir.resolve("_association-sections.gsp");
        Path generatedQuickAdd = viewDir.resolve("_association-quick-add.gsp");
        assertThat(generatedSections).exists();
        assertThat(generatedQuickAdd).exists();
        String generatedQuickAddContent = Files.readString(generatedQuickAdd);
        assertThat(generatedQuickAddContent).contains("associationCreate");
        assertThat(generatedQuickAddContent).contains("data-relationship-context");
        assertThat(generatedQuickAddContent).doesNotContain("raw(");
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

    private Path createGrailsApp() throws Exception {
        runCommand(tempDir, List.of("grails", "create-app", APP_NAME));
        Path appDir = tempDir.resolve(APP_NAME);
        appDir.resolve("gradlew").toFile().setExecutable(true);
        appDir.resolve("grailsw").toFile().setExecutable(true);
        assertThat(appDir.resolve("build.gradle")).exists();
        assertThat(appDir.resolve("grailsw")).exists();
        return appDir;
    }

    private ModelMetadata listQueryMetadata() {
        ModelMetadata metadata = new ModelMetadata("ListQueryModel");
        EnumMetadata status = new EnumMetadata("ListQueryModel.RecordStatus");
        status.setValues(List.of(
            new EnumMetadata.EnumValue("ACTIVE", 0),
            new EnumMetadata.EnumValue("ARCHIVED", 1),
            new EnumMetadata.EnumValue("DRAFT", 2)
        ));
        metadata.addEnum(status);

        ClassMetadata municipality = new ClassMetadata("ListQueryModel.Municipality");
        municipality.setTableName("list_municipality");
        AttributeMetadata municipalityName = new AttributeMetadata("name");
        municipalityName.setJavaType("String");
        municipalityName.setColumnName("list_name");
        municipalityName.setMandatory(true);
        municipality.addAttribute(municipalityName);

        ClassMetadata record = new ClassMetadata("ListQueryModel.Record");
        record.setTableName("list_record");
        AttributeMetadata name = new AttributeMetadata("name");
        name.setJavaType("String");
        name.setColumnName("list_name");
        name.setMandatory(true);
        record.addAttribute(name);
        AttributeMetadata recordStatus = new AttributeMetadata("status");
        recordStatus.setJavaType("String");
        recordStatus.setEnumType(status.getName());
        record.addAttribute(recordStatus);
        AttributeMetadata active = new AttributeMetadata("active");
        active.setJavaType("Boolean");
        active.setColumnName("is_active");
        record.addAttribute(active);
        AttributeMetadata year = new AttributeMetadata("year");
        year.setJavaType("Integer");
        year.setColumnName("list_year");
        year.setMinValue("1900");
        year.setMaxValue("2200");
        record.addAttribute(year);
        AttributeMetadata validFrom = new AttributeMetadata("validFrom");
        validFrom.setJavaType("java.time.LocalDate");
        validFrom.setColumnName("valid_from");
        record.addAttribute(validFrom);
        AttributeMetadata municipalityRef = new AttributeMetadata("municipality");
        municipalityRef.setJavaType("Long");
        municipalityRef.setForeignKey(true);
        municipalityRef.setReferencedClass(municipality.getName());
        record.addAttribute(municipalityRef);

        RelationshipMetadata relation = new RelationshipMetadata("Record_Municipality");
        relation.setSourceClass(record.getName());
        relation.setTargetClass(municipality.getName());
        relation.setSourceAttribute("municipality");
        relation.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        record.addRelationship(relation);
        metadata.addRelationship(relation);
        metadata.addClass(municipality);
        metadata.addClass(record);
        return metadata;
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
                    def municipalityLink = workspace.workspaceRelationshipLinks.find { it.name == 'municipality' }

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
                    !workspace.workspaceDetailSections.toString().contains('municipality')
                    municipalityLink.id == bern.id.toString()
                    municipalityLink.controller == 'municipality'

                    when:
                    Municipality.withSession { session ->
                        session.delete(bern)
                        session.flush()
                    }

                    then:
                    thrown(DataIntegrityViolationException)
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
                    viewModel.formSections*.title.contains('Allgemein')
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

    private static boolean isGrailsAvailable() throws IOException, InterruptedException {
        try {
            runCommand(Path.of(".").toAbsolutePath().normalize(), List.of("grails", "--version"), Duration.ofSeconds(30));
            return true;
        } catch (IOException e) {
            return false;
        }
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
            throw new IOException("Command failed (exit " + exitCode + "): "
                + String.join(" ", command) + "\nOutput:\n" + output);
        }
    }

    private ModelMetadata collisionMetadata() {
        ModelMetadata metadata = new ModelMetadata("SmokeModel");

        EnumMetadata topicAStatus = new EnumMetadata("SmokeModel.TopicA.Status");
        topicAStatus.setValues(List.of(
            new EnumMetadata.EnumValue("ACTIVE", 0),
            new EnumMetadata.EnumValue("in.Betrieb", 1),
            new EnumMetadata.EnumValue("class", 2),
            new EnumMetadata.EnumValue("a.b", 3),
            new EnumMetadata.EnumValue("a_b", 4)
        ));
        metadata.addEnum(topicAStatus);

        EnumMetadata topicBStatus = new EnumMetadata("SmokeModel.TopicB.Status");
        topicBStatus.setValues(List.of(new EnumMetadata.EnumValue("ACTIVE", 0)));
        metadata.addEnum(topicBStatus);

        ClassMetadata topicAGebaeude = new ClassMetadata("SmokeModel.TopicA.Gebaeude");
        topicAGebaeude.setTableName("gebaeude_a");
        topicAGebaeude.addAttribute(enumAttribute("status", topicAStatus.getName(), true));
        topicAGebaeude.addAttribute(geometryAttribute("position"));
        topicAGebaeude.addAttribute(textAttribute("display-name", "name"));
        topicAGebaeude.addAttribute(textAttribute("primary_name", "name"));
        metadata.addClass(topicAGebaeude);

        ClassMetadata topicBGebaeude = new ClassMetadata("SmokeModel.TopicB.Gebaeude");
        topicBGebaeude.setTableName("gebaeude_b");
        topicBGebaeude.addAttribute(enumAttribute("status", topicBStatus.getName(), true));
        AttributeMetadata owner = new AttributeMetadata("owner");
        owner.setForeignKey(true);
        owner.setReferencedClass(topicAGebaeude.getName());
        owner.setJavaType("Long");
        owner.setMandatory(false);
        topicBGebaeude.addAttribute(owner);
        metadata.addClass(topicBGebaeude);

        RelationshipMetadata relationship = new RelationshipMetadata("TopicB_Gebaeude_owner");
        relationship.setSourceClass(topicBGebaeude.getName());
        relationship.setTargetClass(topicAGebaeude.getName());
        relationship.setSourceAttribute("owner");
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        metadata.addRelationship(relationship);

        ClassMetadata component = new ClassMetadata("SmokeModel.TopicA.Component");
        component.setKind(ClassMetadata.ClassKind.STRUCTURE);
        component.addAttribute(textAttribute("label", "label"));
        metadata.addClass(component);

        RelationshipMetadata composition = new RelationshipMetadata("TopicA_Gebaeude_components");
        composition.setSourceClass(topicAGebaeude.getName());
        composition.setTargetClass(component.getName());
        composition.setSourceAttribute("Components");
        composition.setType(RelationshipMetadata.RelationType.ONE_TO_MANY);
        composition.setSemanticKind(RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE);
        composition.setComposition(true);
        composition.setCardinality(new RelationshipMetadata.Cardinality(1, 1, 0, -1));
        metadata.addRelationship(composition);

        ClassMetadata gebaeudeLink = new ClassMetadata("SmokeModel.TopicA.GebaeudeLink");
        gebaeudeLink.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        gebaeudeLink.setTableName("gebaeude_link");
        metadata.addClass(gebaeudeLink);

        RelationshipMetadata sourceRole = new RelationshipMetadata("GebaeudeLink_Source");
        sourceRole.setSourceClass(gebaeudeLink.getName());
        sourceRole.setTargetClass(topicAGebaeude.getName());
        sourceRole.setTargetRoleName("Source");
        sourceRole.setType(RelationshipMetadata.RelationType.ASSOCIATION);
        sourceRole.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
        sourceRole.setMandatory(true);
        metadata.addRelationship(sourceRole);

        RelationshipMetadata targetRole = new RelationshipMetadata("GebaeudeLink_Target");
        targetRole.setSourceClass(gebaeudeLink.getName());
        targetRole.setTargetClass(topicBGebaeude.getName());
        targetRole.setTargetRoleName("Target");
        targetRole.setType(RelationshipMetadata.RelationType.ASSOCIATION);
        targetRole.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
        targetRole.setMandatory(true);
        metadata.addRelationship(targetRole);

        return metadata;
    }

    private ModelMetadata simpleMetadata() {
        ModelMetadata metadata = new ModelMetadata("SmokeModel");

        ClassMetadata person = new ClassMetadata("SmokeModel.People.Person");
        person.setTableName("person");
        person.addAttribute(textAttribute("firstName", "first_name"));
        metadata.addClass(person);

        ClassMetadata address = new ClassMetadata("SmokeModel.Addresses.Address");
        address.setTableName("address");
        address.addAttribute(textAttribute("street", "street"));
        metadata.addClass(address);

        ClassMetadata personAddress = new ClassMetadata("SmokeModel.People.PersonAddress");
        personAddress.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        personAddress.setTableName("person_address");
        metadata.addClass(personAddress);

        RelationshipMetadata personRole = new RelationshipMetadata("PersonAddress_Person");
        personRole.setSourceClass(personAddress.getName());
        personRole.setTargetClass(person.getName());
        personRole.setTargetRoleName("Person");
        personRole.setType(RelationshipMetadata.RelationType.ASSOCIATION);
        personRole.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
        personRole.setMandatory(true);
        metadata.addRelationship(personRole);

        RelationshipMetadata addressRole = new RelationshipMetadata("PersonAddress_Address");
        addressRole.setSourceClass(personAddress.getName());
        addressRole.setTargetClass(address.getName());
        addressRole.setTargetRoleName("Address");
        addressRole.setType(RelationshipMetadata.RelationType.ASSOCIATION);
        addressRole.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
        addressRole.setMandatory(true);
        metadata.addRelationship(addressRole);

        return metadata;
    }

    private AttributeMetadata enumAttribute(String name, String enumType, boolean mandatory) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setEnumType(enumType);
        attribute.setMandatory(mandatory);
        return attribute;
    }

    private AttributeMetadata geometryAttribute(String name) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setGeometry(true);
        attribute.setGeometryKind("POINT");
        attribute.setGeometrySrid(2056);
        attribute.setJavaType("org.locationtech.jts.geom.Geometry");
        attribute.setMandatory(false);
        return attribute;
    }

    private AttributeMetadata textAttribute(String name, String sqlName) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setSqlName(sqlName);
        attribute.setColumnName(sqlName);
        attribute.setJavaType("String");
        attribute.setMaxLength(100);
        attribute.setMandatory(false);
        return attribute;
    }
}

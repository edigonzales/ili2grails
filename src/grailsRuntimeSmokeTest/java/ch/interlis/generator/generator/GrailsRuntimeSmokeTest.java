package ch.interlis.generator.generator;

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
        GenerationConfig config = grailsConfig(appDir, false);

        new GrailsCrudGenerator().generate(metadata, config);

        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        ClassMetadata person = metadata.getClass("SmokeModel.People.Person");
        String domainClass = DOMAIN_PACKAGE + "." + registry.className(person);

        runCommand(appDir, List.of("./grailsw", "generate-all", domainClass));
        runCommand(appDir, List.of("./gradlew", "compileGroovy"));
    }

    private Path createGrailsApp() throws Exception {
        runCommand(tempDir, List.of("grails", "create-app", APP_NAME, "--grails-version", grailsVersion()));
        Path appDir = tempDir.resolve(APP_NAME);
        appDir.resolve("gradlew").toFile().setExecutable(true);
        appDir.resolve("grailsw").toFile().setExecutable(true);
        assertThat(appDir.resolve("build.gradle")).exists();
        assertThat(appDir.resolve("grailsw")).exists();
        return appDir;
    }

    private GenerationConfig grailsConfig(Path appDir, boolean geometryEnabled) {
        return GenerationConfig.builder(appDir, BASE_PACKAGE)
            .domainPackage(DOMAIN_PACKAGE)
            .controllerPackage(BASE_PACKAGE)
            .enumPackage(ENUM_PACKAGE)
            .jdbcUrl(JDBC_URL)
            .schema("public")
            .uiTheme(geometryEnabled ? GenerationConfig.UI_THEME_BOOTSTRAP : GenerationConfig.UI_THEME_DEFAULT)
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

    private static String grailsVersion() {
        String version = System.getProperty("grailsSmokeVersion");
        return version == null || version.isBlank() ? "7.0.6" : version;
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

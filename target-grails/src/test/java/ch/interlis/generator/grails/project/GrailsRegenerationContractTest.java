package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.GenerationConfig;
import ch.interlis.generator.grails.GrailsCrudGenerator;
import ch.interlis.generator.grails.project.plan.GenerationDiagnosticCode;
import ch.interlis.generator.grails.project.plan.GenerationPlan;
import ch.interlis.generator.grails.project.plan.ProjectChangeType;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regenerationsverträge (Spezifikation §48): vollständiger Plan vor Write,
 * Idempotenz, User-Änderungen blockieren, Manifest zuletzt.
 */
class GrailsRegenerationContractTest {

    @TempDir
    Path tempDir;

    @Test
    void dryRunDoesNotModifyAnyProjectFile() throws Exception {
        Path projectDir = tempDir.resolve("dry-run-project");
        Files.createDirectories(projectDir);
        Path userFile = projectDir.resolve("grails-app/conf/application.yml");
        Files.createDirectories(userFile.getParent());
        Files.writeString(userFile, "environments:\n  development:\n    dataSource:\n      url: x\n");

        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();
        GenerationPlan plan = generator.plan(sampleMetadata(), config);

        String before = Files.readString(userFile);
        // plan darf nichts schreiben
        assertThat(plan.hasBlockingDiagnostics()).isFalse();
        assertThat(Files.readString(userFile)).isEqualTo(before);
        assertThat(Files.list(projectDir))
            .extracting(path -> projectDir.relativize(path).toString())
            .containsExactly("grails-app");
    }

    @Test
    void firstGenerationCreatesManifest() throws Exception {
        Path projectDir = tempDir.resolve("first-gen");
        Files.createDirectories(projectDir);
        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();

        generator.generate(sampleMetadata(), config);

        Path manifest = projectDir.resolve(".ili2grails/generation-manifest.json");
        assertThat(manifest).exists();
        String content = Files.readString(manifest);
        assertThat(content).contains("schemaVersion");
        assertThat(content).doesNotContain("timestamp").doesNotContain("user.home");
    }

    @Test
    void secondIdenticalGenerationHasOnlyUnchangedActions() throws Exception {
        Path projectDir = tempDir.resolve("idempotent");
        Files.createDirectories(projectDir);
        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();

        generator.generate(sampleMetadata(), config);
        GenerationPlan secondPlan = generator.plan(sampleMetadata(), config);

        assertThat(secondPlan.hasBlockingDiagnostics()).isFalse();
        assertThat(secondPlan.mutatingChanges())
            .as("second identical generation must not mutate")
            .isEmpty();
        assertThat(secondPlan.changes())
            .extracting(change -> change.type())
            .allMatch(type -> type == ProjectChangeType.UNCHANGED);
    }

    @Test
    void userModifiedDomainFileBlocksEntireApply() throws Exception {
        Path projectDir = tempDir.resolve("user-modified");
        Files.createDirectories(projectDir);
        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();

        generator.generate(sampleMetadata(), config);

        Path domainFile = projectDir.resolve(
            "grails-app/domain/com/example/domain/Sample.groovy");
        Files.writeString(domainFile, "class Sample { String hacked = 'x' }");

        GenerationPlan plan = generator.plan(sampleMetadata(), config);
        assertThat(plan.hasBlockingDiagnostics()).isTrue();
        assertThat(plan.diagnostics())
            .filteredOn(diagnostic -> diagnostic.code() == GenerationDiagnosticCode.USER_MODIFIED_MANAGED_FILE)
            .isNotEmpty();

        // Ein Blocker verhindert sämtliche Änderungen (auch scheinbar unabhängige)
        assertThat(plan.blockedChanges()).isNotEmpty();
        assertThatThrownBy(() -> generator.generate(sampleMetadata(), config))
            .isInstanceOf(ch.interlis.generator.grails.GrailsGenerationBlockedException.class);
    }

    @Test
    void noOtherFileIsChangedWhenPlanContainsBlocker() throws Exception {
        Path projectDir = tempDir.resolve("blocker-no-changes");
        Files.createDirectories(projectDir);
        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();
        generator.generate(sampleMetadata(), config);

        // enum-Datei verändern -> Blocker
        Path enumFile = projectDir.resolve("src/main/groovy/com/example/enums/Status.groovy");
        Files.writeString(enumFile, "enum Status { HACKED }");

        String domainBefore = Files.readString(projectDir.resolve(
            "grails-app/domain/com/example/domain/Sample.groovy"));

        assertThatThrownBy(() -> generator.generate(sampleMetadata(), config))
            .isInstanceOf(ch.interlis.generator.grails.GrailsGenerationBlockedException.class);

        assertThat(Files.readString(projectDir.resolve(
            "grails-app/domain/com/example/domain/Sample.groovy"))).isEqualTo(domainBefore);
    }

    @Test
    void applicationOwnedMainLayoutIsNeverOverwritten() throws Exception {
        Path projectDir = tempDir.resolve("main-layout");
        Files.createDirectories(projectDir);
        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();
        generator.generate(sampleMetadata(), config);

        Path mainGsp = projectDir.resolve("grails-app/views/layouts/main.gsp");
        Files.createDirectories(mainGsp.getParent());
        Files.writeString(mainGsp, "user layout content");

        GenerationPlan plan = generator.plan(sampleMetadata(), config);
        assertThat(plan.changes())
            .filteredOn(change -> change.relativePath().toString()
                .equals("grails-app/views/layouts/main.gsp"))
            .isEmpty();
        assertThat(Files.readString(mainGsp)).isEqualTo("user layout content");
    }

    @Test
    void manifestIsWrittenLastAndFailedWriteDoesNotPublishNewManifest() throws Exception {
        Path projectDir = tempDir.resolve("manifest-last");
        Files.createDirectories(projectDir);
        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();
        generator.generate(sampleMetadata(), config);

        Path manifest = projectDir.resolve(".ili2grails/generation-manifest.json");
        String firstManifest = Files.readString(manifest);

        // Domain-Datei entfernen -> CREATE geplant; das Domain-Verzeichnis
        // wird anschliessend durch eine Datei blockiert, sodass der Write
        // scheitert und das Manifest nicht publiziert wird.
        Path domainDir = projectDir.resolve("grails-app/domain/com/example/domain");
        Files.delete(domainDir.resolve("Sample.groovy"));

        GenerationPlan createPlan = generator.plan(sampleMetadata(), config);
        assertThat(createPlan.mutatingChanges())
            .filteredOn(change -> change.type() == ProjectChangeType.CREATE)
            .isNotEmpty();

        Files.deleteIfExists(domainDir);
        Files.writeString(domainDir, "blocked");
        assertThatThrownBy(() -> generator.apply(createPlan, config))
            .isInstanceOf(Exception.class);
        assertThat(Files.readString(manifest)).isEqualTo(firstManifest);
    }

    @Test
    void removedModelClassDeletesOnlyUnmodifiedManagedFile() throws Exception {
        Path projectDir = tempDir.resolve("removed-class");
        Files.createDirectories(projectDir);
        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();
        generator.generate(sampleMetadata(), config);

        // Zweite Klasse im Modell: nur eine Klasse vorhanden -> Modell mit
        // entferntem Enum entfernt die Enum-Datei.
        ModelMetadata reduced = reducedMetadata();
        GenerationPlan plan = generator.plan(reduced, config);
        assertThat(plan.mutatingChanges())
            .filteredOn(change -> change.type() == ProjectChangeType.DELETE)
            .extracting(change -> change.relativePath().toString())
            .contains("src/main/groovy/com/example/enums/Status.groovy");
        assertThat(plan.hasBlockingDiagnostics()).isFalse();
    }

    @Test
    void changedModelUpdatesOnlyAffectedManagedFiles() throws Exception {
        Path projectDir = tempDir.resolve("changed-model");
        Files.createDirectories(projectDir);
        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();
        generator.generate(sampleMetadata(), config);

        String domainBefore = Files.readString(projectDir.resolve(
            "grails-app/domain/com/example/domain/Sample.groovy"));

        ModelMetadata changed = sampleMetadataWithExtraAttribute();
        GenerationPlan plan = generator.plan(changed, config);
        assertThat(plan.mutatingChanges())
            .filteredOn(change -> change.type() == ProjectChangeType.UPDATE)
            .extracting(change -> change.relativePath().toString())
            .contains("grails-app/domain/com/example/domain/Sample.groovy");
        assertThat(plan.hasBlockingDiagnostics()).isFalse();
        assertThat(Files.readString(projectDir.resolve(
            "grails-app/domain/com/example/domain/Sample.groovy"))).isEqualTo(domainBefore);
    }

    @Test
    void modifiedLegacyRuntimeFileBlocksEntireApply() throws Exception {
        Path projectDir = tempDir.resolve("legacy-modified");
        Files.createDirectories(projectDir);
        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();
        generator.generate(sampleMetadata(), config);

        // Legacy-Runtime-Datei mit verändertem Hash anlegen (Herkunfts-Evidenz:
        // ein weiterer Legacy-Runtime-Pfad existiert).
        Path legacyDir = projectDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/runtime");
        Files.createDirectories(legacyDir);
        Files.writeString(legacyDir.resolve("InterlisLegacyProbe.groovy"),
            "class InterlisLegacyProbe {}\n");
        Path legacyService = projectDir.resolve(
            "grails-app/services/ch/interlis/generator/grails/runtime/"
                + "InterlisAssociationQueryService.groovy");
        Files.createDirectories(legacyService.getParent());
        Files.writeString(legacyService,
            "class InterlisAssociationQueryService { String hacked }\n");

        GenerationPlan plan = generator.plan(sampleMetadata(), config);
        assertThat(plan.hasBlockingDiagnostics()).isTrue();
        assertThat(plan.diagnostics())
            .filteredOn(diagnostic ->
                diagnostic.code() == GenerationDiagnosticCode.MODIFIED_LEGACY_RUNTIME_FILE)
            .isNotEmpty();
        assertThatThrownBy(() -> generator.generate(sampleMetadata(), config))
            .isInstanceOf(ch.interlis.generator.grails.GrailsGenerationBlockedException.class)
            .hasMessageContaining("no project files were changed");
    }

    @Test
    void textEditsPlanAssetsAndSpringResources() throws Exception {
        Path projectDir = tempDir.resolve("text-edits");
        Files.createDirectories(projectDir.resolve("grails-app/assets/javascripts"));
        Files.createDirectories(projectDir.resolve("grails-app/assets/stylesheets"));
        Files.createDirectories(projectDir.resolve("grails-app/conf/spring"));
        Files.writeString(projectDir.resolve("build.gradle"),
            "buildscript {}\n\ndependencies {\n    implementation \"x:y:1\"\n}\n");
        Files.writeString(projectDir.resolve("grails-app/conf/application.yml"),
            "environments:\n  development:\n    dataSource:\n      url: jdbc:h2:mem:x\n");
        Files.writeString(projectDir.resolve("grails-app/assets/javascripts/application.js"),
            "//= require_self\n");
        Files.writeString(projectDir.resolve("grails-app/assets/stylesheets/application.css"),
            "/*= require_self */\n");
        Files.writeString(projectDir.resolve("grails-app/conf/spring/resources.groovy"),
            "beans {}\n");

        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();
        GenerationPlan plan = generator.plan(sampleMetadata(), config);
        assertThat(plan.hasBlockingDiagnostics()).isFalse();
        assertThat(plan.changes())
            .filteredOn(change -> change.relativePath().toString().endsWith("application.js"))
            .isNotEmpty();
        assertThat(plan.changes())
            .filteredOn(change -> change.relativePath().toString().endsWith("resources.groovy"))
            .isNotEmpty();
    }

    @Test
    void planOrderingIsDeterministic() throws Exception {
        Path projectDir = tempDir.resolve("ordering");
        Files.createDirectories(projectDir);
        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();

        GenerationPlan first = generator.plan(sampleMetadata(), config);
        GenerationPlan second = generator.plan(sampleMetadata(), config);
        assertThat(second.changes())
            .extracting(change -> change.relativePath().toString())
            .containsExactlyElementsOf(first.changes().stream()
                .map(change -> change.relativePath().toString())
                .toList());
    }

    @Test
    void secretsAreAbsentFromManifestAndPlanReports() throws Exception {
        Path projectDir = tempDir.resolve("secrets");
        Files.createDirectories(projectDir);
        GenerationConfig config = GenerationConfig.builder(projectDir, "com.example")
            .jdbcUrl("jdbc:postgresql://localhost:5432/edit?user=postgres&password=secret")
            .build();
        GrailsCrudGenerator generator = new GrailsCrudGenerator();
        generator.generate(sampleMetadata(), config);

        String manifest = Files.readString(projectDir.resolve(".ili2grails/generation-manifest.json"));
        assertThat(manifest)
            .doesNotContain("secret")
            .doesNotContain("password=")
            .doesNotContain("jdbc:");
    }

    @Test
    void targetPathTraversalIsRejected() throws Exception {
        Path projectDir = tempDir.resolve("traversal");
        Files.createDirectories(projectDir);
        GenerationConfig config = config(projectDir);
        GrailsCrudGenerator generator = new GrailsCrudGenerator();

        GeneratorProbe probe = new GeneratorProbe();
        GenerationPlan plan = probe.planWithTraversal(sampleMetadata(), config);
        assertThat(plan.hasBlockingDiagnostics()).isTrue();
        assertThat(plan.diagnostics())
            .filteredOn(diagnostic -> diagnostic.code() == GenerationDiagnosticCode.TARGET_PATH_OUTSIDE_PROJECT)
            .isNotEmpty();
        // Apply mit blockierendem Plan darf keine Datei verändern
        assertThatThrownBy(() -> generator.apply(plan, config))
            .isInstanceOf(Exception.class);
        assertThat(Files.list(projectDir)).isEmpty();
    }

    private ModelMetadata reducedMetadata() throws Exception {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("RegenModel");
        builder.classBuilder("RegenModel.Sample")
            .tableName("sample")
            .attribute(new AttributeMetadataBuilder("name").javaType("String").maxLength(50).mandatory(true));
        return new ModelMetadataFactory().buildValidated(builder);
    }

    private ModelMetadata sampleMetadataWithExtraAttribute() throws Exception {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("RegenModel");
        builder.enumBuilder("RegenModel.Status")
            .value("ACTIVE", 0)
            .value("INACTIVE", 1);
        builder.classBuilder("RegenModel.Sample")
            .tableName("sample")
            .attribute(new AttributeMetadataBuilder("name").javaType("String").maxLength(50).mandatory(true))
            .attribute(new AttributeMetadataBuilder("status")
                .enumType("RegenModel.Status")
                .javaType("String")
                .mandatory(false))
            .attribute(new AttributeMetadataBuilder("extra")
                .javaType("String")
                .maxLength(20)
                .mandatory(false));
        return new ModelMetadataFactory().buildValidated(builder);
    }

    private GenerationConfig config(Path projectDir) {
        return GenerationConfig.builder(projectDir, "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .build();
    }

    private ModelMetadata sampleMetadata() throws Exception {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("RegenModel");
        builder.enumBuilder("RegenModel.Status")
            .value("ACTIVE", 0)
            .value("INACTIVE", 1);
        builder.classBuilder("RegenModel.Sample")
            .tableName("sample")
            .attribute(new AttributeMetadataBuilder("name").javaType("String").maxLength(50).mandatory(true))
            .attribute(new AttributeMetadataBuilder("status")
                .enumType("RegenModel.Status")
                .javaType("String")
                .mandatory(false));
        return new ModelMetadataFactory().buildValidated(builder);
    }

    /**
     * Erzeugt einen Plan mit Pfadtraversal über die Ownership-Validierung.
     */
    private static final class GeneratorProbe {
        GenerationPlan planWithTraversal(ModelMetadata metadata, GenerationConfig config)
            throws Exception {
            ch.interlis.generator.grails.project.plan.GenerationOwnershipValidator validator =
                new ch.interlis.generator.grails.project.plan.GenerationOwnershipValidator();
            List<ch.interlis.generator.grails.project.plan.GenerationDiagnostic> diagnostics =
                validator.validate(List.of(
                    ch.interlis.generator.grails.project.plan.PlannedProjectFile.text(
                        java.nio.file.Path.of("../escape.groovy"),
                        GrailsProjectFileOwner.GENERATOR_MANAGED, "class X {}", "traversal")));
            return new ch.interlis.generator.grails.project.plan.GenerationPlan(
                1, metadata.getModelName(), "model", "config", List.of(), diagnostics);
        }
    }
}

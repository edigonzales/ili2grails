package ch.interlis.generator.grails.project.plan;

import ch.interlis.generator.grails.GenerationConfig;
import ch.interlis.generator.grails.GrailsApplicationYamlUpdater;
import ch.interlis.generator.grails.GrailsAssociationRegistryGenerator;
import ch.interlis.generator.grails.GrailsAssociationPlanner;
import ch.interlis.generator.grails.GrailsBuildGradleUpdater;
import ch.interlis.generator.grails.GrailsCrudGenerator;
import ch.interlis.generator.grails.GrailsDomainGenerator;
import ch.interlis.generator.grails.GrailsEnumGenerator;
import ch.interlis.generator.grails.GrailsInverseRelationshipPlanner;
import ch.interlis.generator.grails.GrailsRelationshipMapper;
import ch.interlis.generator.grails.GrailsUiRegistryGenerator;
import ch.interlis.generator.grails.RuntimeDescriptorPlan;
import ch.interlis.generator.grails.RuntimeDescriptorDiagnostic;
import ch.interlis.generator.grails.RuntimeDescriptorPlanner;
import ch.interlis.generator.grails.TargetNameRegistry;
import ch.interlis.generator.grails.project.GrailsApplicationConfigurationUpdater;
import ch.interlis.generator.grails.project.GrailsAssetManifestUpdater;
import ch.interlis.generator.grails.project.GrailsProjectFileOwner;
import ch.interlis.generator.grails.project.GrailsProjectFileOwnership;
import ch.interlis.generator.grails.project.GrailsScaffoldingTemplateInstaller;
import ch.interlis.generator.grails.project.LegacyFileMatch;
import ch.interlis.generator.grails.project.LegacyRuntimeScanResult;
import ch.interlis.generator.grails.project.LegacyRuntimeScanner;
import ch.interlis.generator.grails.project.ProjectCustomizationDiagnostic;
import ch.interlis.generator.grails.project.RuntimeCoordinates;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFingerprint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plant ALLE Projektänderungen vor dem ersten Write (Spezifikation §43):
 * Runtime-Descriptor-Gate, Generator-Artefakte, Legacy-Scan, Text-Updater,
 * Manifest-Vergleich und Ownership-Validierung. {@code plan(...)} verändert
 * keine Datei.
 */
public final class GrailsGenerationPlanner {

    public static final int PLAN_SCHEMA_VERSION = 1;

    private final GrailsDomainGenerator domainGenerator = new GrailsDomainGenerator();
    private final GrailsEnumGenerator enumGenerator = new GrailsEnumGenerator();
    private final GrailsAssociationRegistryGenerator associationRegistryGenerator =
        new GrailsAssociationRegistryGenerator();
    private final GrailsUiRegistryGenerator uiRegistryGenerator = new GrailsUiRegistryGenerator();
    private final GrailsBuildGradleUpdater buildGradleUpdater = new GrailsBuildGradleUpdater();
    private final GrailsApplicationYamlUpdater applicationYamlUpdater =
        new GrailsApplicationYamlUpdater();
    private final GrailsAssetManifestUpdater assetUpdater = new GrailsAssetManifestUpdater();
    private final GrailsApplicationConfigurationUpdater configUpdater =
        new GrailsApplicationConfigurationUpdater();
    private final GrailsScaffoldingTemplateInstaller scaffoldingInstaller =
        new GrailsScaffoldingTemplateInstaller();
    private final LegacyRuntimeScanner legacyScanner = new LegacyRuntimeScanner();

    public GenerationPlan plan(ModelMetadata metadata,
                               GenerationConfig config,
                               RuntimeCoordinates runtimeCoordinates,
                               Optional<GeneratedProjectManifest> previousManifest)
        throws IOException {
        Path projectRoot = config.getOutputDir();
        List<GenerationDiagnostic> diagnostics = new ArrayList<>();

        // 1. Runtime-Descriptor-Plan und Gate
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper relationshipMapper =
            GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner associationPlanner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, relationshipMapper);
        GrailsInverseRelationshipPlanner inversePlanner =
            GrailsInverseRelationshipPlanner.forMetadata(metadata, config, registry, relationshipMapper);
        RuntimeDescriptorPlan descriptorPlan = new RuntimeDescriptorPlanner(
            registry, relationshipMapper, associationPlanner, inversePlanner)
            .plan(metadata, config);
        for (RuntimeDescriptorDiagnostic diagnostic : descriptorPlan.diagnostics()) {
            Map<String, String> details = new LinkedHashMap<>(diagnostic.details());
            details.put("runtimeCode", diagnostic.code().name());
            if (diagnostic.subject() != null) {
                details.put("subject", diagnostic.subject());
            }
            ProjectCustomizationDiagnostic.Level level = switch (diagnostic.severity()) {
                case INFO -> ProjectCustomizationDiagnostic.Level.INFO;
                case WARNING -> ProjectCustomizationDiagnostic.Level.WARNING;
                case ERROR -> ProjectCustomizationDiagnostic.Level.ERROR;
            };
            diagnostics.add(new GenerationDiagnostic(
                level,
                GenerationDiagnosticCode.RUNTIME_DESCRIPTOR_INVALID,
                null,
                diagnostic.message(),
                details));
        }

        // 2. Generator-Artefakte (reine Planungsfunktionen)
        List<PlannedProjectFile> plannedFiles = new ArrayList<>();
        plannedFiles.addAll(domainGenerator.plan(metadata, config, registry, relationshipMapper,
            inversePlanner));
        plannedFiles.addAll(enumGenerator.plan(metadata, config, registry));
        plannedFiles.add(associationRegistryGenerator.plan(descriptorPlan, config));
        plannedFiles.add(uiRegistryGenerator.plan(descriptorPlan, config, registry));
        if (GenerationConfig.UI_THEME_BOOTSTRAP.equals(config.getUiTheme())) {
            plannedFiles.addAll(scaffoldingInstaller.plan());
            plannedFiles.addAll(scaffoldingInstaller.planUiViews());
            plannedFiles.addAll(scaffoldingInstaller.planUiAssets());
            plannedFiles.addAll(scaffoldingInstaller.planUiStylesheet());
        }

        // 3. Text-Updater für application-owned idempotente Dateien
        plannedFiles.addAll(planTextEdits(projectRoot, config, runtimeCoordinates));

        // 4. Legacy-Scan (Spezifikation §42.2, P2-D012): Nur Dateien in den
        // Runtime-Package-Verzeichnissen haben eindeutige Legacy-Herkunft;
        // dort blockieren Modifikationen. Pfade, die auch die App selbst
        // besitzt (main.gsp, Assets, i18n), erzeugen nur eine WARNING und
        // werden nie angefasst.
        LegacyRuntimeScanResult scanResult = legacyScanner.scan(projectRoot);
        boolean legacyProvenance = hasLegacyRuntimeFiles(projectRoot);
        for (LegacyFileMatch modified : scanResult.modifiedFiles()) {
            boolean runtimePackage = isRuntimePackagePath(modified.relativePath());
            diagnostics.add(new GenerationDiagnostic(
                runtimePackage
                    ? ProjectCustomizationDiagnostic.Level.ERROR
                    : ProjectCustomizationDiagnostic.Level.WARNING,
                GenerationDiagnosticCode.MODIFIED_LEGACY_RUNTIME_FILE,
                modified.relativePath(),
                "Modified legacy runtime file requires manual migration; it will not be deleted.",
                Map.of("sha256", modified.actualSha256(),
                    "runtimePackage", String.valueOf(runtimePackage))));
        }
        for (Path unknown : scanResult.unknownRuntimeFiles()) {
            diagnostics.add(new GenerationDiagnostic(
                ProjectCustomizationDiagnostic.Level.ERROR,
                GenerationDiagnosticCode.UNKNOWN_LEGACY_RUNTIME_FILE,
                unknown,
                "Unknown legacy runtime file blocks generation; remove it manually.",
                Map.of()));
        }
        Set<String> plannedPaths = plannedFiles.stream()
            .map(file -> file.relativePath().toString())
            .collect(java.util.stream.Collectors.toSet());
        for (LegacyFileMatch known : scanResult.knownUnmodifiedFiles()) {
            // main.gsp ist immer APPLICATION_OWNED (P2-D003): Die Löschung
            // eines lokalen main.gsp ist nur über den expliziten
            // Legacy-Migrationspfad erlaubt, wenn das Projekt nachweislich
            // Legacy-Runtime-Dateien besitzt (Herkunfts-Evidenz).
            if (known.relativePath().toString().endsWith("views/layouts/main.gsp")
                && !legacyProvenance) {
                continue;
            }
            // Pfade, die der Generator jetzt selbst verwaltet (z.B. die
            // app-lokalen UI-Views, P2-D014), werden nicht als Legacy
            // gelöscht - der Legacy-Migrationspfad gilt nur für Dateien,
            // die nicht erneut generiert werden.
            if (plannedPaths.contains(known.relativePath().toString())) {
                continue;
            }
            plannedFiles.add(new PlannedProjectFile(
                known.relativePath(),
                GrailsProjectFileOwner.LEGACY_RUNTIME,
                new byte[0],
                "known unmodified legacy runtime copy (delete when migrating)"));
        }

        // 5. Ownership validieren
        diagnostics.addAll(new GenerationOwnershipValidator().validate(plannedFiles));

        // 6. Manifest-Vergleich
        List<ProjectChange> changes = decideChanges(projectRoot, plannedFiles, previousManifest,
            diagnostics);

        String modelFingerprint = ModelMetadataFingerprint.of(metadata);
        String configFingerprint = configFingerprint(config);
        changes.sort(Comparator.comparing(change -> change.relativePath().toString()));
        return new GenerationPlan(PLAN_SCHEMA_VERSION, metadata.getModelName(),
            modelFingerprint, configFingerprint, changes, diagnostics);
    }

    private boolean hasLegacyRuntimeFiles(Path projectRoot) {
        List<String> runtimeDirs = List.of(
            "src/main/groovy/ch/interlis/generator/grails/runtime",
            "grails-app/services/ch/interlis/generator/grails/runtime",
            "grails-app/controllers/ch/interlis/generator/grails/runtime",
            "grails-app/taglib/ch/interlis/generator/grails/runtime");
        for (String dir : runtimeDirs) {
            Path path = projectRoot.resolve(dir);
            if (Files.isDirectory(path)) {
                try (var files = Files.list(path)) {
                    if (files.findAny().isPresent()) {
                        return true;
                    }
                } catch (IOException ignored) {
                    // nicht lesbar zählt nicht als Herkunft
                }
            }
        }
        return false;
    }

    private boolean isRuntimePackagePath(Path relativePath) {
        String path = relativePath.toString().replace('\\', '/');
        return path.startsWith("src/main/groovy/ch/interlis/generator/grails/runtime")
            || path.startsWith("grails-app/services/ch/interlis/generator/grails/runtime")
            || path.startsWith("grails-app/controllers/ch/interlis/generator/grails/runtime")
            || path.startsWith("grails-app/taglib/ch/interlis/generator/grails/runtime");
    }

    /**
     * P2-D015: Das unveränderte Grails-Scaffold-main.gsp (byte-identisch zum
     * bekannten create-app-Stand) wird einmalig durch die Plugin-Delegation
     * ersetzt, damit die generierte App die ili2grails-Shell rendert. Ein
     * benutzerverändertes main.gsp wird nie angefasst (APPLICATION_OWNED).
     */
    static final String GRAILS_SCAFFOLD_MAIN_GSP_SHA256 =
        "5c32efe05e1084384905ca872e6fe0c7e8d01a3b6012b45f373d7802af9881ea";

    private static final String MAIN_GSP_DELEGATION = readMainGspShellLayout();

    /**
     * Grails 7 kann Layouts nicht über Meta-Tags verketten: Die app-lokale
     * main.gsp muss daher den vollständigen Shell-Layout-Inhalt enthalten
     * (P2-D015). Die Quelle ist das Plugin-Layout im Overlay.
     */
    private static String readMainGspShellLayout() {
        try (var input = GrailsGenerationPlanner.class.getClassLoader()
            .getResourceAsStream("grails/overlays/ui-views/layouts/ili2grails.gsp")) {
            if (input == null) {
                throw new IllegalStateException("Missing overlay layout ili2grails.gsp");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read overlay layout ili2grails.gsp", e);
        }
    }

    private List<PlannedProjectFile> planTextEdits(Path projectRoot,
                                                   GenerationConfig config,
                                                   RuntimeCoordinates runtimeCoordinates)
        throws IOException {
        List<PlannedProjectFile> planned = new ArrayList<>();
        Map<Path, TextFileEdit> edits = new LinkedHashMap<>();

        Path buildGradle = projectRoot.resolve("build.gradle");
        if (Files.isRegularFile(buildGradle)) {
            String content = Files.readString(buildGradle, StandardCharsets.UTF_8);
            edits.put(Path.of("build.gradle"), buildGradleUpdater.plan(
                Path.of("build.gradle"), content, config, runtimeCoordinates));
        }
        Path applicationYaml = projectRoot.resolve("grails-app/conf/application.yml");
        if (Files.isRegularFile(applicationYaml)) {
            String content = Files.readString(applicationYaml, StandardCharsets.UTF_8);
            edits.put(Path.of("grails-app/conf/application.yml"), applicationYamlUpdater.plan(
                Path.of("grails-app/conf/application.yml"), content, config.getJdbcUrl(),
                config.getSchema(), config.isGeometryEnabled(), config.getDefaultSrid(),
                config.getLanguage()));
        }
        boolean bootstrapTheme = GenerationConfig.UI_THEME_BOOTSTRAP.equals(config.getUiTheme());
        if (bootstrapTheme) {
            Path applicationJs = projectRoot.resolve("grails-app/assets/javascripts/application.js");
            if (Files.isRegularFile(applicationJs)) {
                edits.put(Path.of("grails-app/assets/javascripts/application.js"),
                    assetUpdater.plan(Path.of("grails-app/assets/javascripts/application.js"),
                        Files.readString(applicationJs, StandardCharsets.UTF_8)));
            }
            Path applicationCss = projectRoot.resolve("grails-app/assets/stylesheets/application.css");
            if (Files.isRegularFile(applicationCss)) {
                edits.put(Path.of("grails-app/assets/stylesheets/application.css"),
                    assetUpdater.plan(Path.of("grails-app/assets/stylesheets/application.css"),
                        Files.readString(applicationCss, StandardCharsets.UTF_8)));
            }
        }
        Path resourcesGroovy = projectRoot.resolve("grails-app/conf/spring/resources.groovy");
        if (Files.isRegularFile(resourcesGroovy)) {
            edits.put(Path.of("grails-app/conf/spring/resources.groovy"),
                configUpdater.plan(Path.of("grails-app/conf/spring/resources.groovy"),
                    Files.readString(resourcesGroovy, StandardCharsets.UTF_8), config));
        }
        if (bootstrapTheme) {
            Path mainGsp = projectRoot.resolve("grails-app/views/layouts/main.gsp");
            if (Files.isRegularFile(mainGsp)
                && ModelMetadataFingerprint.sha256(Files.readAllBytes(mainGsp))
                    .equals(GRAILS_SCAFFOLD_MAIN_GSP_SHA256)) {
                planned.add(PlannedProjectFile.text(Path.of("grails-app/views/layouts/main.gsp"),
                    GrailsProjectFileOwner.APPLICATION_OWNED, MAIN_GSP_DELEGATION,
                    "replace unmodified Grails scaffold main.gsp with the ili2grails shell layout"));
            }
        }

        for (TextFileEdit edit : edits.values()) {
            if (edit.changed()) {
                planned.add(PlannedProjectFile.text(edit.relativePath(),
                    GrailsProjectFileOwner.APPLICATION_OWNED, edit.updatedContent(),
                    edit.reason()));
            }
        }
        return planned;
    }

    private List<ProjectChange> decideChanges(Path projectRoot,
                                              List<PlannedProjectFile> plannedFiles,
                                              Optional<GeneratedProjectManifest> previousManifest,
                                              List<GenerationDiagnostic> diagnostics)
        throws IOException {
        Map<String, PlannedProjectFile> plannedByPath = new LinkedHashMap<>();
        for (PlannedProjectFile planned : plannedFiles) {
            plannedByPath.put(planned.relativePath().toString(), planned);
        }
        Map<String, ManagedFileManifestEntry> manifestEntries = new LinkedHashMap<>();
        if (previousManifest.isPresent()) {
            for (ManagedFileManifestEntry entry : previousManifest.get().files()) {
                manifestEntries.put(entry.path(), entry);
            }
        }

        List<ProjectChange> changes = new ArrayList<>();
        for (PlannedProjectFile planned : plannedByPath.values()) {
            Path path = projectRoot.resolve(planned.relativePath());
            String plannedSha = planned.sha256();
            String relative = planned.relativePath().toString();
            if (!Files.exists(path)) {
                changes.add(change(relative, ProjectChangeType.CREATE, planned.owner(),
                    null, plannedSha, "create " + planned.reason(), planned.content()));
                continue;
            }
            String currentSha = Files.exists(path)
                ? ModelMetadataFingerprint.sha256(Files.readAllBytes(path)) : null;
            if (planned.owner() == GrailsProjectFileOwner.LEGACY_RUNTIME) {
                changes.add(change(relative, ProjectChangeType.DELETE, planned.owner(),
                    currentSha, plannedSha,
                    "known unmodified legacy runtime copy deleted on migration", null));
                continue;
            }
            if (planned.owner() == GrailsProjectFileOwner.APPLICATION_OWNED) {
                if (java.util.Arrays.equals(planned.content(), Files.readAllBytes(path))) {
                    changes.add(change(relative, ProjectChangeType.UNCHANGED, planned.owner(),
                        currentSha, plannedSha, "idempotent update unchanged", null));
                } else {
                    changes.add(change(relative, ProjectChangeType.UPDATE, planned.owner(),
                        currentSha, plannedSha, "idempotent managed block update", planned.content()));
                }
                continue;
            }
            if (java.util.Arrays.equals(planned.content(), Files.readAllBytes(path))) {
                changes.add(change(relative, ProjectChangeType.UNCHANGED, planned.owner(),
                    currentSha, plannedSha, "content identical", null));
                continue;
            }
            ManagedFileManifestEntry manifestEntry = manifestEntries.get(relative);
            if (manifestEntry == null) {
                diagnostics.add(new GenerationDiagnostic(
                    ProjectCustomizationDiagnostic.Level.ERROR,
                    GenerationDiagnosticCode.USER_MODIFIED_MANAGED_FILE,
                    planned.relativePath(),
                    "Existing file is not managed by the manifest; refusing to overwrite: "
                        + relative,
                    Map.of("sha256", currentSha)));
                changes.add(change(relative, ProjectChangeType.BLOCKED, planned.owner(),
                    currentSha, plannedSha, "unmanaged existing file", planned.content()));
                continue;
            }
            if (!manifestEntry.sha256().equals(currentSha)) {
                diagnostics.add(new GenerationDiagnostic(
                    ProjectCustomizationDiagnostic.Level.ERROR,
                    GenerationDiagnosticCode.USER_MODIFIED_MANAGED_FILE,
                    planned.relativePath(),
                    "User-modified managed file blocks generation: " + relative,
                    Map.of("manifestSha256", manifestEntry.sha256(), "currentSha256", currentSha)));
                changes.add(change(relative, ProjectChangeType.BLOCKED, planned.owner(),
                    currentSha, plannedSha, "user modified managed file", planned.content()));
                continue;
            }
            changes.add(change(relative, ProjectChangeType.UPDATE, planned.owner(),
                currentSha, plannedSha, "managed file update", planned.content()));
        }

        // Obsolete Manifest-Dateien
        for (ManagedFileManifestEntry entry : manifestEntries.values()) {
            if (plannedByPath.containsKey(entry.path())) {
                continue;
            }
            Path path = projectRoot.resolve(entry.path());
            String currentSha = Files.exists(path)
                ? ModelMetadataFingerprint.sha256(Files.readAllBytes(path)) : null;
            if (Files.exists(path) && entry.sha256().equals(currentSha)) {
                changes.add(change(entry.path(), ProjectChangeType.DELETE, entry.owner(),
                    currentSha, entry.sha256(), "obsolete managed file removed", null));
            } else if (Files.exists(path)) {
                diagnostics.add(new GenerationDiagnostic(
                    ProjectCustomizationDiagnostic.Level.ERROR,
                    GenerationDiagnosticCode.ORPHANED_MANAGED_FILE,
                    Path.of(entry.path()),
                    "Orphaned managed file was modified by the user: " + entry.path(),
                    Map.of("manifestSha256", entry.sha256(), "currentSha256", currentSha)));
                changes.add(change(entry.path(), ProjectChangeType.BLOCKED, entry.owner(),
                    currentSha, entry.sha256(), "orphaned managed file", null));
            }
        }
        return changes;
    }

    private static ProjectChange change(String relativePath,
                                        ProjectChangeType type,
                                        GrailsProjectFileOwner owner,
                                        String previousSha256,
                                        String plannedSha256,
                                        String reason,
                                        byte[] plannedContent) {
        return new ProjectChange(Path.of(relativePath), type, owner, previousSha256,
            plannedSha256, reason, plannedContent);
    }

    private String configFingerprint(GenerationConfig config) throws IOException {
        List<String> values = List.of(
            config.getBasePackage() == null ? "" : config.getBasePackage(),
            config.getDomainPackage() == null ? "" : config.getDomainPackage(),
            config.getEnumPackage() == null ? "" : config.getEnumPackage(),
            config.getControllerPackage() == null ? "" : config.getControllerPackage(),
            config.getUiTheme() == null ? "" : config.getUiTheme(),
            config.getMapEditor() == null ? "" : config.getMapEditor(),
            String.valueOf(config.isGeometryEnabled()),
            config.getDefaultSrid() == null ? "" : String.valueOf(config.getDefaultSrid()),
            config.getLanguage() == null ? "" : config.getLanguage()
        );
        return ModelMetadataFingerprint.sha256(String.join("|", values));
    }
}

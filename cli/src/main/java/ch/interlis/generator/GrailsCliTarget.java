package ch.interlis.generator;

import ch.interlis.generator.grails.GenerationConfig;
import ch.interlis.generator.grails.GrailsCrudGenerator;
import ch.interlis.generator.grails.GrailsRelationshipMapper;
import ch.interlis.generator.grails.GrailsTemplateOverlayInstaller;
import ch.interlis.generator.grails.TargetNameRegistry;
import ch.interlis.generator.model.ModelMetadata;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class GrailsCliTarget implements CliTargetAdapter {

    private final MetadataCommandOptions metadataOptions;
    private final GrailsCliOptions options;

    GrailsCliTarget(MetadataCommandOptions metadataOptions, GrailsCliOptions options) {
        this.metadataOptions = Objects.requireNonNull(metadataOptions, "metadataOptions");
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public CliTarget id() {
        return CliTarget.GRAILS;
    }

    @Override
    public boolean isConfigured() {
        return options.isConfigured();
    }

    @Override
    public void validateSelected(CommandLine commandLine) {
        if (options.outputDir() == null) {
            throw new ParameterException(commandLine, "Option --grails-output is required for --target grails.");
        }
        if (options.initRequested() && options.outputDir() == null) {
            throw new ParameterException(commandLine, "Option --grails-init requires --grails-output.");
        }
        if (options.generateAll() && !options.initRequested()) {
            throw new ParameterException(commandLine, "Option --grails-generate-all requires --grails-init.");
        }
        if (options.uiTheme() != null && !isSupportedUiTheme(options.uiTheme())) {
            throw new ParameterException(commandLine, "Unsupported value for --grails-ui-theme: " + options.uiTheme());
        }
        if (options.mapEditor() != null && !isSupportedMapEditor(options.mapEditor())) {
            throw new ParameterException(
                commandLine,
                "Unsupported value for --grails-map-editor: " + options.mapEditor()
            );
        }
        if (options.defaultSrid() != null && options.defaultSrid() <= 0) {
            throw new ParameterException(commandLine, "Option --grails-default-srid must be greater than zero.");
        }
        if (options.associationUi() != null && !isSupportedAssociationUi(options.associationUi())) {
            throw new ParameterException(
                commandLine,
                "Unsupported value for --grails-association-ui: " + options.associationUi()
            );
        }
        if (options.associationNavigation() != null
            && !isSupportedAssociationNavigation(options.associationNavigation())) {
            throw new ParameterException(
                commandLine,
                "Unsupported value for --grails-association-navigation: " + options.associationNavigation()
            );
        }
        if (options.associationPageSize() != null
            && (options.associationPageSize() < 1 || options.associationPageSize() > 100)) {
            throw new ParameterException(
                commandLine,
                "Option --grails-association-page-size must be between 1 and 100."
            );
        }
    }

    @Override
    public void validateNotSelected(CommandLine commandLine) {
        if (isConfigured()) {
            throw new ParameterException(commandLine, "Grails options require --target grails.");
        }
    }

    @Override
    public void generate(ModelMetadata metadata) throws IOException, InterruptedException {
        Path grailsProjectDir = resolveProjectDir();
        GenerationConfig config = buildConfig(metadata, grailsProjectDir);

        System.out.println("Installing Grails UI overlay...");
        new GrailsTemplateOverlayInstaller().install(grailsProjectDir, config);
        System.out.println("Generating Grails domains and supporting artifacts...");
        new GrailsCrudGenerator().generate(metadata, config);
        if (options.generateAll()) {
            runGrailsGenerateAll(metadata, config, grailsProjectDir);
        }
        System.out.println();
        System.out.println("===================================================");
        System.out.println("Grails CRUD artifacts generated in: " + grailsProjectDir.toAbsolutePath());
    }

    GenerationConfig buildConfig(ModelMetadata metadata, Path grailsProjectDir) {
        String basePackage = options.basePackage() != null ? options.basePackage() : "com.example";
        String uiTheme = resolveUiTheme();
        String mapEditor = resolveMapEditor(uiTheme);
        int defaultSrid = options.defaultSrid() != null ? options.defaultSrid() : 2056;
        boolean geometryEnabled = hasGeometryAttributes(metadata)
            || GenerationConfig.MAP_EDITOR_OPENLAYERS.equals(mapEditor);

        GenerationConfig.Builder builder = GenerationConfig.builder(grailsProjectDir, basePackage);
        builder.jdbcUrl(metadataOptions.jdbcUrl());
        builder.schema(metadataOptions.schema());
        builder.uiTheme(uiTheme);
        builder.mapEditor(mapEditor);
        builder.defaultSrid(defaultSrid);
        builder.geometryEnabled(geometryEnabled);
        if (options.domainPackage() != null) {
            builder.domainPackage(options.domainPackage());
        }
        if (options.controllerPackage() != null) {
            builder.controllerPackage(options.controllerPackage());
        }
        if (options.enumPackage() != null) {
            builder.enumPackage(options.enumPackage());
        }
        if (options.associationUi() != null) {
            builder.associationUiMode(options.associationUi());
        }
        if (options.associationPageSize() != null) {
            builder.associationPageSize(options.associationPageSize());
        }
        if (options.associationNavigation() != null) {
            builder.associationNavigation(options.associationNavigation());
        }
        return builder.build();
    }

    String resolveUiTheme() {
        if (options.uiTheme() == null || options.uiTheme().isBlank()) {
            return GenerationConfig.UI_THEME_DEFAULT;
        }
        return options.uiTheme();
    }

    String resolveMapEditor(String uiTheme) {
        if (options.mapEditor() != null && !options.mapEditor().isBlank()) {
            return options.mapEditor();
        }
        if (GenerationConfig.UI_THEME_BOOTSTRAP.equals(uiTheme)) {
            return GenerationConfig.MAP_EDITOR_OPENLAYERS;
        }
        return GenerationConfig.MAP_EDITOR_NONE;
    }

    private Path resolveProjectDir() throws IOException, InterruptedException {
        Path outputDir = Objects.requireNonNull(options.outputDir(), "outputDir");
        if (options.initRequested()) {
            return scaffoldGrailsProjectIfNeeded(outputDir);
        }
        return outputDir;
    }

    private Path scaffoldGrailsProjectIfNeeded(Path outputDir) throws IOException, InterruptedException {
        String appName = options.initAppName();
        Path appDir = outputDir;
        if (appName == null || appName.isBlank()) {
            Path fileName = outputDir.getFileName();
            appName = fileName != null ? fileName.toString() : "grails-app";
        } else {
            appDir = outputDir.resolve(appName);
        }

        if (isGrailsProject(appDir)) {
            throw new IllegalStateException(
                "Grails scaffold blocked: existing project detected at "
                    + appDir.toAbsolutePath()
            );
        }

        if (Files.exists(outputDir) && !Files.isDirectory(outputDir)) {
            throw new IllegalStateException("Grails scaffold blocked: target is not a directory: "
                + outputDir.toAbsolutePath());
        }
        if (Files.exists(outputDir) && !isDirectoryEmpty(outputDir)) {
            throw new IllegalStateException("Grails scaffold blocked: target directory is not empty: "
                + outputDir.toAbsolutePath());
        }
        if (!appDir.equals(outputDir) && Files.exists(appDir)) {
            throw new IllegalStateException("Grails scaffold blocked: app directory already exists: "
                + appDir.toAbsolutePath());
        }

        Path workingDir = appDir.equals(outputDir)
            ? resolveWorkingDir(outputDir)
            : outputDir.toAbsolutePath().normalize();
        runGrailsCreateApp(workingDir, appName);
        return appDir;
    }

    private boolean isGrailsProject(Path outputDir) {
        return Files.exists(outputDir.resolve("build.gradle"))
            || Files.exists(outputDir.resolve("settings.gradle"))
            || Files.exists(outputDir.resolve("grails-app"));
    }

    private boolean isDirectoryEmpty(Path outputDir) throws IOException {
        try (var stream = Files.list(outputDir)) {
            return stream.findAny().isEmpty();
        }
    }

    private void runGrailsCreateApp(Path workingDir, String appName)
        throws IOException, InterruptedException {
        Files.createDirectories(workingDir);

        List<String> command = new ArrayList<>();
        command.add("grails");
        command.add("create-app");
        command.add(appName);

        System.out.println("Creating Grails application with: " + String.join(" ", command));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.inheritIO();
        Process process = builder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Grails CLI failed (exit " + exitCode + ") while creating app in "
                + workingDir.toAbsolutePath().normalize().resolve(appName));
        }
    }

    private void runGrailsGenerateAll(ModelMetadata metadata, GenerationConfig config, Path grailsProjectDir)
        throws IOException, InterruptedException {
        Path grailsWrapper = grailsProjectDir.resolve("grailsw");
        if (!Files.exists(grailsWrapper)) {
            throw new IllegalStateException("Grails wrapper not found at: " + grailsWrapper.toAbsolutePath());
        }
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        List<String> domainClasses = mapper.generatedClasses().stream()
            .map(classMetadata -> config.getDomainPackage() + "." + registry.className(classMetadata))
            .sorted()
            .toList();
        for (String domainClass : domainClasses) {
            List<String> command = List.of("./grailsw", "generate-all", domainClass);
            System.out.println("Running Grails generate-all for " + domainClass + "...");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(grailsProjectDir.toFile());
            builder.inheritIO();
            Process process = builder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Grails generate-all failed (exit " + exitCode + ") for "
                    + domainClass);
            }
        }
    }

    private Path resolveWorkingDir(Path outputDir) {
        Path absoluteOutputDir = outputDir.toAbsolutePath().normalize();
        Path workingDir = absoluteOutputDir.getParent();
        if (workingDir == null) {
            workingDir = Path.of(".").toAbsolutePath().normalize();
        }
        return workingDir;
    }

    private boolean hasGeometryAttributes(ModelMetadata metadata) {
        return metadata.getAllClasses().stream()
            .flatMap(clazz -> clazz.getAllAttributes().stream())
            .anyMatch(attributeMetadata -> attributeMetadata.isGeometry());
    }

    private boolean isSupportedUiTheme(String uiTheme) {
        return GenerationConfig.UI_THEME_DEFAULT.equals(uiTheme)
            || GenerationConfig.UI_THEME_BOOTSTRAP.equals(uiTheme);
    }

    private boolean isSupportedMapEditor(String mapEditor) {
        return GenerationConfig.MAP_EDITOR_NONE.equals(mapEditor)
            || GenerationConfig.MAP_EDITOR_OPENLAYERS.equals(mapEditor);
    }

    private boolean isSupportedAssociationUi(String mode) {
        return GenerationConfig.ASSOCIATION_UI_AUTO.equals(mode)
            || GenerationConfig.ASSOCIATION_UI_OFF.equals(mode)
            || GenerationConfig.ASSOCIATION_UI_READ_ONLY.equals(mode)
            || GenerationConfig.ASSOCIATION_UI_EDITABLE.equals(mode);
    }

    private boolean isSupportedAssociationNavigation(String navigation) {
        return GenerationConfig.ASSOCIATION_NAVIGATION_AUTO.equals(navigation)
            || GenerationConfig.ASSOCIATION_NAVIGATION_SHOW.equals(navigation)
            || GenerationConfig.ASSOCIATION_NAVIGATION_HIDE.equals(navigation);
    }
}

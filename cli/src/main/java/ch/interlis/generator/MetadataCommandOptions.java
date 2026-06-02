package ch.interlis.generator;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParameterException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class MetadataCommandOptions {

    private static final List<String> DEFAULT_MODEL_REPOSITORIES = List.of(
        "https://models.interlis.ch/",
        "https://models.geo.admin.ch/"
    );

    @Parameters(index = "0", paramLabel = "<jdbcUrl>", description = "JDBC connection URL.")
    private String jdbcUrl;

    @Parameters(index = "1", paramLabel = "<modelName>", description = "INTERLIS model name to process.")
    private String modelName;

    @Parameters(index = "2", arity = "0..1", paramLabel = "[schema]", description = "Database schema name.")
    private String schema;

    @Option(names = "--model-file", paramLabel = "<file>", description = "Explicit INTERLIS model file path.")
    private Path modelFilePath;

    @Option(
        names = "--model-repos",
        split = "[,;]",
        paramLabel = "<r1;r2>",
        description = "Repository list for model lookup. Accepts comma or semicolon separated values."
    )
    private List<String> modelRepositories = new ArrayList<>();

    @Option(names = "--metadata-json", paramLabel = "<file>", description = "Write deterministic metadata IR JSON.")
    private Path metadataJsonPath;

    @Option(
        names = "--merge-report",
        paramLabel = "<dir>",
        description = "Write relationship merge diagnostics Markdown and JSON."
    )
    private Path mergeReportDir;

    void validate(CommandLine commandLine) {
        if (modelName == null || modelName.isBlank()) {
            throw new ParameterException(commandLine, "Model name is required.");
        }
        if (looksLikeModelFile(modelName)) {
            throw new ParameterException(
                commandLine,
                "Model file must be passed with --model-file, not as a positional argument."
            );
        }
        if (modelFilePath != null && !Files.isRegularFile(modelFilePath)) {
            throw new ParameterException(commandLine, "Model file not found: " + modelFilePath);
        }
    }

    String jdbcUrl() {
        return jdbcUrl;
    }

    String modelName() {
        return modelName;
    }

    String schema() {
        return schema;
    }

    Path modelFilePath() {
        return modelFilePath;
    }

    Path metadataJsonPath() {
        return metadataJsonPath;
    }

    Path mergeReportDir() {
        return mergeReportDir;
    }

    List<String> modelRepositories() {
        if (modelRepositories == null || modelRepositories.isEmpty()) {
            return DEFAULT_MODEL_REPOSITORIES;
        }
        List<String> repositories = modelRepositories.stream()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
        if (repositories.isEmpty()) {
            return DEFAULT_MODEL_REPOSITORIES;
        }
        return repositories;
    }

    private boolean looksLikeModelFile(String value) {
        return value != null && value.endsWith(".ili");
    }
}

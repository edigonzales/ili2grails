package ch.interlis.generator;

import ch.interlis.generator.django.DjangoGenerationConfig;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataReaderAppCliTest {

    @Test
    void topLevelInvocationWithoutSubcommandIsRejected() {
        CliResult result = execute();

        assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(result.err()).contains("missing required subcommand");
    }

    @Test
    void readAcceptsCommonMetadataOptions() {
        CommandLine.ParseResult parseResult = parse(
            "read",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "public",
            "--model-file", "test-models/SimpleAddressModel.ili",
            "--model-repos", "https://models.interlis.ch/;file:/repo",
            "--metadata-json", "build/metadata/simple.json",
            "--merge-report", "build/reports/metadata-merge"
        );

        assertThat(parseResult.subcommand().commandSpec().name()).isEqualTo("read");
        ReadCommand command = (ReadCommand) parseResult.subcommand().commandSpec().userObject();
        MetadataCommandOptions options = readField(command, "metadataOptions");
        assertThat(options.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/test");
        assertThat(options.modelName()).isEqualTo("SimpleModel");
        assertThat(options.schema()).isEqualTo("public");
        assertThat(options.modelFilePath()).isEqualTo(Path.of("test-models/SimpleAddressModel.ili"));
        assertThat(options.metadataJsonPath()).isEqualTo(Path.of("build/metadata/simple.json"));
        assertThat(options.mergeReportDir()).isEqualTo(Path.of("build/reports/metadata-merge"));
        assertThat(options.modelRepositories()).containsExactly("https://models.interlis.ch/", "file:/repo");
    }

    @Test
    void generateRequiresAtLeastOneTarget() {
        CliResult result = execute(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel"
        );

        assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(result.err()).contains("Missing required option: '--target");
    }

    @Test
    void repeatedTargetsParseInUserSpecifiedOrder() {
        CommandLine.ParseResult parseResult = parse(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "grails",
            "--target", "django",
            "--grails-output", "generated-grails",
            "--django-output", "generated-django",
            "--django-app", "simple_app"
        );

        GenerateCommand command = (GenerateCommand) parseResult.subcommand().commandSpec().userObject();
        List<CliTarget> targets = readField(command, "targets");
        assertThat(targets)
            .containsExactly(CliTarget.GRAILS, CliTarget.DJANGO);
    }

    @Test
    void duplicateTargetsFailBeforeMetadataRead() {
        CliResult result = execute(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "django",
            "--target", "django",
            "--django-output", "generated-django",
            "--django-app", "simple_app"
        );

        assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(result.err()).contains("Duplicate target selected: django");
    }

    @Test
    void grailsTargetRequiresOutputDirectory() {
        CliResult result = execute(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "grails"
        );

        assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(result.err()).contains("Option --grails-output is required for --target grails.");
    }

    @Test
    void grailsLanguageOptionIsParsedAndValidated() {
        CommandLine.ParseResult parseResult = parse(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "grails",
            "--grails-output", "generated-grails",
            "--grails-language", "en"
        );
        GenerateCommand command = (GenerateCommand) parseResult.subcommand().commandSpec().userObject();
        GrailsCliOptions options = readField(command, "grailsOptions");

        assertThat(options.language()).isEqualTo("en");
    }

    @Test
    void unsupportedGrailsLanguageIsRejected() {
        CliResult result = execute(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "grails",
            "--grails-output", "generated-grails",
            "--grails-language", "fr"
        );

        assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(result.err()).contains("Unsupported value for --grails-language: fr");
    }

    @Test
    void djangoTargetRequiresOutputDirectoryAndAppName() {
        CliResult missingOutput = execute(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "django",
            "--django-app", "simple_app"
        );
        CliResult missingApp = execute(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "django",
            "--django-output", "generated-django"
        );

        assertThat(missingOutput.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(missingOutput.err()).contains("Option --django-output is required for --target django.");
        assertThat(missingApp.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(missingApp.err()).contains("Option --django-app is required for --target django.");
    }

    @Test
    void djangoAppMustBePythonPackageSegment() {
        CliResult result = execute(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "django",
            "--django-output", "generated-django",
            "--django-app", "simple-app"
        );

        assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(result.err()).contains("Option --django-app must be a valid Python package segment");
    }

    @Test
    void targetSpecificOptionsWithoutMatchingTargetFail() {
        CliResult grailsOptionWithoutGrails = execute(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "django",
            "--django-output", "generated-django",
            "--django-app", "simple_app",
            "--grails-output", "generated-grails"
        );
        CliResult djangoOptionWithoutDjango = execute(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "grails",
            "--grails-output", "generated-grails",
            "--django-output", "generated-django"
        );

        assertThat(grailsOptionWithoutGrails.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(grailsOptionWithoutGrails.err()).contains("Grails options require --target grails.");
        assertThat(djangoOptionWithoutDjango.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(djangoOptionWithoutDjango.err()).contains("Django options require --target django.");
    }

    @Test
    void metadataJsonIsCommonOutputOptionForGenerate() {
        CommandLine.ParseResult parseResult = parse(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "django",
            "--django-output", "generated-django",
            "--django-app", "simple_app",
            "--metadata-json", "build/metadata/simple.json"
        );

        GenerateCommand command = (GenerateCommand) parseResult.subcommand().commandSpec().userObject();
        MetadataCommandOptions options = readField(command, "metadataOptions");
        assertThat(options.metadataJsonPath()).isEqualTo(Path.of("build/metadata/simple.json"));
    }

    @Test
    void positionalModelFileIsRejected() {
        CliResult result = execute(
            "read",
            "jdbc:postgresql://localhost:5432/test",
            "test-models/SimpleAddressModel.ili"
        );

        assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(result.err()).contains("Model file must be passed with --model-file");
    }

    @Test
    void djangoCliTargetBuildsConfigForModelsPyOutput() {
        DjangoCliOptions options = parseDjangoOptions(
            "generate",
            "jdbc:postgresql://localhost:5432/test",
            "SimpleModel",
            "--target", "django",
            "--django-output", "generated-django",
            "--django-app", "simple_app"
        );

        DjangoGenerationConfig config = new DjangoCliTarget(options).buildConfig();

        assertThat(config.getOutputDir()).isEqualTo(Path.of("generated-django"));
        assertThat(config.getAppName()).isEqualTo("simple_app");
        assertThat(config.getModelsFile()).isEqualTo(Path.of("generated-django/simple_app/models.py"));
    }

    private CommandLine.ParseResult parse(String... args) {
        return MetadataReaderApp.newCommandLine().parseArgs(args);
    }

    private CliResult execute(String... args) {
        CommandLine commandLine = MetadataReaderApp.newCommandLine();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        int exitCode = commandLine.execute(args);
        return new CliResult(exitCode, out.toString(), err.toString());
    }

    private DjangoCliOptions parseDjangoOptions(String... args) {
        CommandLine.ParseResult parseResult = parse(args);
        GenerateCommand command = (GenerateCommand) parseResult.subcommand().commandSpec().userObject();
        return readField(command, "djangoOptions");
    }

    @SuppressWarnings("unchecked")
    private <T> T readField(Object target, String fieldName) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private record CliResult(int exitCode, String out, String err) {
    }
}

package ch.interlis.generator;

import ch.interlis.generator.django.DjangoGenerationConfig;
import ch.interlis.generator.django.DjangoModelsGenerator;
import ch.interlis.generator.model.ModelMetadata;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

import java.util.Objects;

final class DjangoCliTarget implements CliTargetAdapter {

    private static final String PYTHON_PACKAGE_SEGMENT = "[A-Za-z_][A-Za-z0-9_]*";

    private final DjangoCliOptions options;

    DjangoCliTarget(DjangoCliOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public CliTarget id() {
        return CliTarget.DJANGO;
    }

    @Override
    public boolean isConfigured() {
        return options.isConfigured();
    }

    @Override
    public void validateSelected(CommandLine commandLine) {
        if (options.outputDir() == null) {
            throw new ParameterException(commandLine, "Option --django-output is required for --target django.");
        }
        if (options.appName() == null || options.appName().isBlank()) {
            throw new ParameterException(commandLine, "Option --django-app is required for --target django.");
        }
        if (!options.appName().matches(PYTHON_PACKAGE_SEGMENT)) {
            throw new ParameterException(
                commandLine,
                "Option --django-app must be a valid Python package segment: " + PYTHON_PACKAGE_SEGMENT
            );
        }
    }

    @Override
    public void validateNotSelected(CommandLine commandLine) {
        if (isConfigured()) {
            throw new ParameterException(commandLine, "Django options require --target django.");
        }
    }

    @Override
    public void generate(ModelMetadata metadata) throws Exception {
        DjangoGenerationConfig config = buildConfig();
        new DjangoModelsGenerator().generate(metadata, config);
        System.out.println();
        System.out.println("===================================================");
        System.out.println("Django models.py generated in: " + config.getModelsFile().toAbsolutePath());
    }

    DjangoGenerationConfig buildConfig() {
        return DjangoGenerationConfig.builder(options.outputDir(), options.appName()).build();
    }
}

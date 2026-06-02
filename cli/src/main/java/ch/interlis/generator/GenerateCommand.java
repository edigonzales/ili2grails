package ch.interlis.generator;

import ch.interlis.generator.model.ModelMetadata;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(
    name = "generate",
    mixinStandardHelpOptions = true,
    description = "Reads metadata once and generates one or more selected targets."
)
final class GenerateCommand implements Callable<Integer> {

    @Mixin
    private MetadataCommandOptions metadataOptions;

    @Option(
        names = "--target",
        required = true,
        converter = CliTargetConverter.class,
        paramLabel = "<grails|django>",
        description = "Target to generate. Can be repeated and is executed in the given order."
    )
    private List<CliTarget> targets = new ArrayList<>();

    @Mixin
    private GrailsCliOptions grailsOptions;

    @Mixin
    private DjangoCliOptions djangoOptions;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try {
            metadataOptions.validate(spec.commandLine());
            validateTargets();
            CliTargetRegistry registry = new CliTargetRegistry(metadataOptions, grailsOptions, djangoOptions);
            registry.validateSelected(targets, spec.commandLine());

            ModelMetadata metadata = new MetadataReaderService().read(metadataOptions);
            new MetadataOutputWriter().write(metadata, metadataOptions);
            for (CliTarget target : targets) {
                registry.adapter(target).generate(metadata);
            }
            printFooter();
            return ExitCode.OK;
        } catch (ParameterException e) {
            return usageError(e);
        } catch (Exception e) {
            return runtimeError(e);
        }
    }

    private void validateTargets() {
        Set<CliTarget> uniqueTargets = new LinkedHashSet<>();
        for (CliTarget target : targets) {
            if (!uniqueTargets.add(target)) {
                throw new ParameterException(
                    spec.commandLine(),
                    "Duplicate target selected: " + target.cliName()
                );
            }
        }
    }

    private int usageError(ParameterException e) {
        spec.commandLine().getErr().println("Error: " + e.getMessage());
        spec.commandLine().usage(spec.commandLine().getErr());
        return ExitCode.USAGE;
    }

    private int runtimeError(Exception e) {
        spec.commandLine().getErr().println("Unexpected error: " + e.getMessage());
        e.printStackTrace(spec.commandLine().getErr());
        return ExitCode.SOFTWARE;
    }

    private void printFooter() {
        spec.commandLine().getOut().println();
        spec.commandLine().getOut().println("===================================================");
    }
}

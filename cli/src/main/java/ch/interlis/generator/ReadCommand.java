package ch.interlis.generator;

import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(
    name = "read",
    mixinStandardHelpOptions = true,
    description = "Reads metadata, prints it, and optionally writes metadata diagnostics."
)
final class ReadCommand implements Callable<Integer> {

    @Mixin
    private MetadataCommandOptions metadataOptions;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try {
            metadataOptions.validate(spec.commandLine());
            var metadata = new MetadataReaderService().read(metadataOptions);
            new MetadataOutputWriter().write(metadata, metadataOptions);
            printFooter();
            return ExitCode.OK;
        } catch (ParameterException e) {
            return usageError(e);
        } catch (Exception e) {
            return runtimeError(e);
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

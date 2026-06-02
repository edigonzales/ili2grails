package ch.interlis.generator;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

public class MetadataReaderApp {

    public static void main(String[] args) {
        int exitCode = newCommandLine().execute(args);
        System.exit(exitCode);
    }

    static CommandLine newCommandLine() {
        return new CommandLine(new RootCommand());
    }

    @Command(
        name = "MetadataReaderApp",
        mixinStandardHelpOptions = true,
        subcommands = {
            ReadCommand.class,
            GenerateCommand.class
        },
        description = "Reads INTERLIS/ili2db metadata and generates target artifacts."
    )
    static final class RootCommand implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            spec.commandLine().getErr().println("Error: missing required subcommand: read or generate");
            spec.commandLine().usage(spec.commandLine().getErr());
            return ExitCode.USAGE;
        }
    }
}

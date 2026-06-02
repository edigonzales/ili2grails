package ch.interlis.generator;

import ch.interlis.generator.model.ModelMetadata;
import picocli.CommandLine;

interface CliTargetAdapter {

    CliTarget id();

    boolean isConfigured();

    void validateSelected(CommandLine commandLine);

    void validateNotSelected(CommandLine commandLine);

    void generate(ModelMetadata metadata) throws Exception;
}

package ch.interlis.generator;

import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class CliTargetRegistry {

    private final Map<CliTarget, CliTargetAdapter> adapters;

    CliTargetRegistry(MetadataCommandOptions metadataOptions,
                      GrailsCliOptions grailsOptions,
                      DjangoCliOptions djangoOptions) {
        this.adapters = new EnumMap<>(CliTarget.class);
        register(new GrailsCliTarget(metadataOptions, grailsOptions));
        register(new DjangoCliTarget(djangoOptions));
    }

    CliTargetAdapter adapter(CliTarget target) {
        return adapters.get(target);
    }

    void validateSelected(List<CliTarget> selectedTargets, CommandLine commandLine) {
        for (CliTargetAdapter adapter : adapters.values()) {
            if (selectedTargets.contains(adapter.id())) {
                adapter.validateSelected(commandLine);
            } else {
                adapter.validateNotSelected(commandLine);
            }
        }
        for (CliTarget selectedTarget : selectedTargets) {
            if (!adapters.containsKey(selectedTarget)) {
                throw new ParameterException(commandLine, "No CLI adapter registered for target: "
                    + selectedTarget.cliName());
            }
        }
    }

    private void register(CliTargetAdapter adapter) {
        adapters.put(adapter.id(), adapter);
    }
}

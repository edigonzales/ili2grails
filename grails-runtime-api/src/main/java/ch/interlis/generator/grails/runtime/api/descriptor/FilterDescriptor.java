package ch.interlis.generator.grails.runtime.api.descriptor;

import java.util.List;
import java.util.Optional;

/**
 * Immutable definition of a list filter on a domain.
 */
public record FilterDescriptor(
    String name,
    FilterType type,
    String className,
    String label,
    String labelCode,
    String targetClass,
    String targetDisplayField,
    String optionUrl,
    List<FilterOption> options
) {

    public FilterDescriptor {
        name = DescriptorValidation.requireText(name, "name");
        type = type == null ? FilterType.TEXT : type;
        options = options == null ? List.of() : List.copyOf(options);
    }

    /**
     * Immutable enum option of a filter.
     */
    public record FilterOption(String value, String label, String labelCode) {
        public FilterOption {
            value = DescriptorValidation.requireText(value, "value");
        }
    }

    public Optional<FilterOption> option(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return options.stream().filter(option -> value.equals(option.value())).findFirst();
    }
}

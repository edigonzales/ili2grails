package ch.interlis.generator.grails.runtime.api.query;

import java.util.List;

/**
 * Typed page of options for pickers.
 */
public record OptionPage(
    List<OptionItem> results,
    boolean more,
    long total,
    int nextOffset
) {

    public OptionPage {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public static OptionPage empty(int nextOffset) {
        return new OptionPage(List.of(), false, 0, nextOffset);
    }
}

package ch.interlis.generator.grails.runtime.api.query;

/**
 * Typed option item for pickers.
 */
public record OptionItem(
    String id,
    String label,
    String displayValue
) {

    public OptionItem {
        id = id == null ? "" : id;
    }
}

package ch.interlis.generator.grails.runtime.api.descriptor;

import java.util.List;

/**
 * Immutable display metadata of a domain (list display, search and label).
 */
public record DisplayDescriptor(
    String label,
    List<String> displayFields,
    List<String> searchFields
) {

    public DisplayDescriptor {
        displayFields = displayFields == null ? List.of() : List.copyOf(displayFields);
        searchFields = searchFields == null ? List.of() : List.copyOf(searchFields);
    }
}

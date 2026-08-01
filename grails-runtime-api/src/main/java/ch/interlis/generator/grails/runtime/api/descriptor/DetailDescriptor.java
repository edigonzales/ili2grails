package ch.interlis.generator.grails.runtime.api.descriptor;

import java.util.List;

/**
 * Immutable detail view layout of a domain.
 */
public record DetailDescriptor(
    List<FormSectionDescriptor> sections
) {

    public DetailDescriptor {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}

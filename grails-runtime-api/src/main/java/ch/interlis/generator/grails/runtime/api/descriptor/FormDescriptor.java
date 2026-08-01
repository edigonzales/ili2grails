package ch.interlis.generator.grails.runtime.api.descriptor;

import java.util.List;

/**
 * Immutable form layout of a domain.
 */
public record FormDescriptor(
    List<FormSectionDescriptor> sections
) {

    public FormDescriptor {
        sections = sections == null ? List.of() : List.copyOf(sections);
        List<String> titles = sections.stream().map(FormSectionDescriptor::title).toList();
        DescriptorValidation.requireDistinctNames(titles, "sections");
    }
}

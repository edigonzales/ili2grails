package ch.interlis.generator.grails.runtime.api.descriptor;

import java.util.List;

/**
 * Immutable form section of a domain form.
 */
public record FormSectionDescriptor(
    String title,
    List<String> fields
) {

    public FormSectionDescriptor {
        title = DescriptorValidation.requireText(title, "title");
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}

package ch.interlis.generator.grails.runtime.api.config;

import java.util.List;
import java.util.Map;

/**
 * Typed runtime UI overrides parsed from the {@code ili2grails.ui.domains}
 * legacy configuration path (or the new typed runtime properties).
 *
 * <p>Overrides may only restrict generated behavior; they never upgrade a
 * generated read-only capability. Applying an override always creates a new
 * descriptor instance instead of mutating an existing one.</p>
 */
public record RuntimeUiOverrides(
    String label,
    ListOverride list,
    FormOverride form,
    Map<String, RelationshipOverride> relationships
) {

    public RuntimeUiOverrides {
        list = list == null ? new ListOverride(null, null, null, null, null, null, null) : list;
        form = form == null ? new FormOverride(null) : form;
        relationships = relationships == null ? Map.of() : Map.copyOf(relationships);
    }

    public static RuntimeUiOverrides none() {
        return new RuntimeUiOverrides(null, null, null, null);
    }

    public boolean isEmpty() {
        return label == null && list.isEmpty() && form.isEmpty() && relationships.isEmpty();
    }

    public record ListOverride(
        List<String> columns,
        List<String> sortableColumns,
        String displayField,
        List<String> displayFields,
        List<String> prominentFilters,
        List<String> searchFields,
        Map<String, FilterOverride> filters
    ) {

        public ListOverride {
            columns = columns == null ? List.of() : List.copyOf(columns);
            sortableColumns = sortableColumns == null ? List.of() : List.copyOf(sortableColumns);
            displayFields = displayFields == null ? List.of() : List.copyOf(displayFields);
            prominentFilters = prominentFilters == null ? List.of() : List.copyOf(prominentFilters);
            searchFields = searchFields == null ? List.of() : List.copyOf(searchFields);
            filters = filters == null ? Map.of() : Map.copyOf(filters);
        }

        public boolean isEmpty() {
            return columns.isEmpty() && sortableColumns.isEmpty() && displayField == null
                && displayFields.isEmpty() && prominentFilters.isEmpty()
                && searchFields.isEmpty() && filters.isEmpty();
        }
    }

    public record FilterOverride(
        String label,
        String labelCode
    ) {

        public boolean isEmpty() {
            return label == null && labelCode == null;
        }
    }

    public record FormOverride(
        List<FormSectionOverride> sections
    ) {

        public FormOverride {
            sections = sections == null ? List.of() : List.copyOf(sections);
        }

        public boolean isEmpty() {
            return sections.isEmpty();
        }
    }

    public record FormSectionOverride(
        String title,
        List<String> fields
    ) {

        public FormSectionOverride {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record RelationshipOverride(
        String mode,
        String label
    ) {

        public boolean isEmpty() {
            return mode == null && label == null;
        }
    }
}

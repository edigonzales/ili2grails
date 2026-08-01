package ch.interlis.generator.grails.runtime.api.descriptor;

import java.util.List;

/**
 * Immutable list layout of a domain.
 */
public record ListDescriptor(
    List<String> columns,
    List<String> searchFields,
    List<SearchFieldDescriptor> searchDefinitions,
    List<String> sortableColumns,
    String displayField,
    List<String> displayFields,
    List<String> prominentFilters,
    List<FilterDescriptor> filters
) {

    public ListDescriptor {
        columns = columns == null ? List.of() : List.copyOf(columns);
        searchFields = searchFields == null ? List.of() : List.copyOf(searchFields);
        searchDefinitions = searchDefinitions == null
            ? List.of()
            : List.copyOf(searchDefinitions);
        sortableColumns = sortableColumns == null ? List.of() : List.copyOf(sortableColumns);
        displayFields = displayFields == null ? List.of() : List.copyOf(displayFields);
        prominentFilters = prominentFilters == null ? List.of() : List.copyOf(prominentFilters);
        filters = filters == null ? List.of() : List.copyOf(filters);
        DescriptorValidation.requireDistinctNames(columns, "columns");
        DescriptorValidation.requireDistinctNames(sortableColumns, "sortableColumns");
        List<String> filterNames = filters.stream().map(FilterDescriptor::name).toList();
        DescriptorValidation.requireDistinctNames(filterNames, "filters");
    }
}

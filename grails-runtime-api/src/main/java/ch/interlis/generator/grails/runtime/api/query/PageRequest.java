package ch.interlis.generator.grails.runtime.api.query;

/**
 * Typed page request for runtime list queries.
 */
public record PageRequest(
    int max,
    int offset,
    String sort,
    String order,
    String query
) {

    public PageRequest {
        max = Math.max(1, max);
        offset = Math.max(0, offset);
    }

    public static PageRequest of(int max, int offset) {
        return new PageRequest(max, offset, null, null, null);
    }

    public boolean hasSort() {
        return sort != null && !sort.isBlank();
    }
}

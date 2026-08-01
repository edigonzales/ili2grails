package ch.interlis.generator.grails.runtime.api.query;

import java.util.List;

/**
 * Typed page of rows for runtime list queries.
 *
 * @param <T> row model type
 */
public record PageResult<T>(
    long total,
    int max,
    int offset,
    boolean more,
    List<T> rows
) {

    public PageResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}

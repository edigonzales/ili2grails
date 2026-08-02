package ch.interlis.generator.reader.ili2db.metrics;

import java.util.Map;

/**
 * Zählung der JDBC-Aufrufe eines Lesedurchgangs (Spezifikation §50.2).
 */
public record JdbcInvocationSummary(
    Map<JdbcInvocationKind, Long> counts,
    Map<String, Long> normalizedSqlCounts
) {

    public JdbcInvocationSummary {
        counts = Map.copyOf(counts);
        normalizedSqlCounts = Map.copyOf(normalizedSqlCounts);
    }

    public long count(JdbcInvocationKind kind) {
        return counts.getOrDefault(kind, 0L);
    }

    public long total() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }
}

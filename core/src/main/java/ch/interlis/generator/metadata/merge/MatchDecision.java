package ch.interlis.generator.metadata.merge;

import java.util.List;
import java.util.Objects;

/**
 * Entscheidung über einen Match zwischen physischen und semantischen Elementen.
 *
 * <p>Es gibt kein first-match-wins: Bei mehrdeutigen Kandidatenmengen wird der
 * Status {@link Status#AMBIGUOUS} geliefert und kein Element ausgewählt.</p>
 */
public record MatchDecision<T>(
    Status status,
    T physical,
    T semantic,
    MatchReason reason,
    String token,
    List<MatchCandidate<T>> candidates
) {

    public enum Status {
        MATCHED,
        UNMATCHED,
        AMBIGUOUS,
        PHYSICAL_ALREADY_USED
    }

    public MatchDecision {
        Objects.requireNonNull(status, "status");
        candidates = candidates == null
            ? List.of()
            : List.copyOf(candidates);
    }

    public boolean matched() {
        return status == Status.MATCHED;
    }
}

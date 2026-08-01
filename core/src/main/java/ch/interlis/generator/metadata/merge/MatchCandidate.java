package ch.interlis.generator.metadata.merge;

import java.util.Objects;

/**
 * Ein einzelner Match-Kandidat zwischen einem physischen und einem semantischen Element.
 */
public record MatchCandidate<T>(
    T physical,
    T semantic,
    MatchReason reason,
    int priority,
    String token
) {

    public MatchCandidate {
        Objects.requireNonNull(physical, "physical");
        Objects.requireNonNull(semantic, "semantic");
        Objects.requireNonNull(reason, "reason");
    }
}

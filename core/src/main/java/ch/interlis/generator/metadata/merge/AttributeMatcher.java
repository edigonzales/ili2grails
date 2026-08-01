package ch.interlis.generator.metadata.merge;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Matcht Attribute einer physischen Klasse (ili2db) mit denen einer semantischen
 * Klasse (ili2c) in expliziten Phasen.
 *
 * <p>Matchphasen (stärkste zuerst):</p>
 * <ol>
 *   <li>{@link MatchReason#EXACT_QUALIFIED_NAME}</li>
 *   <li>{@link MatchReason#EXACT_NAME}</li>
 *   <li>{@link MatchReason#EXACT_COLUMN_NAME}</li>
 *   <li>{@link MatchReason#NORMALIZED_FULL_TOKEN}</li>
 *   <li>{@link MatchReason#NORMALIZED_ID_SUFFIX}</li>
 * </ol>
 *
 * <p>Pro Phase wird ein Kandidatengraph gebildet. Akzeptiert wird nur eine Komponente
 * mit genau einem semantischen und einem physischen Element. 1:n, n:1 oder n:m ist
 * {@link MatchDecision.Status#AMBIGUOUS}. Bei Ambiguität wird nicht in schwächeren
 * Phasen weitergesucht. Sortierung dient nur der stabilen Ausgabe, nie als fachlicher
 * Tie-Breaker.</p>
 */
public final class AttributeMatcher {

    private final MergeTokenNormalizer normalizer;

    public AttributeMatcher(MergeTokenNormalizer normalizer) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
    }

    public List<MatchDecision<AttributeMetadata>> match(
        ClassMetadata physicalClass,
        ClassMetadata semanticClass
    ) {
        Objects.requireNonNull(physicalClass, "physicalClass");
        Objects.requireNonNull(semanticClass, "semanticClass");

        List<AttributeMetadata> allPhysical =
            new ArrayList<>(physicalClass.getAllAttributes());
        List<AttributeMetadata> unmatchedPhysical = new ArrayList<>(allPhysical);
        List<AttributeMetadata> unmatchedSemantic =
            new ArrayList<>(semanticClass.getAllAttributes());
        List<MatchDecision<AttributeMetadata>> decisions = new ArrayList<>();
        Set<AttributeMetadata> consumedPhysical = new LinkedHashSet<>();

        for (Phase phase : Phase.values()) {
            processPhase(
                phase,
                unmatchedPhysical,
                unmatchedSemantic,
                consumedPhysical,
                decisions
            );
        }

        for (AttributeMetadata semantic : unmatchedSemantic) {
            List<MatchCandidate<AttributeMetadata>> allMatches = allCandidates(semantic, allPhysical);
            if (!allMatches.isEmpty() && allMatches.stream()
                .allMatch(candidate -> consumedPhysical.contains(candidate.physical()))) {
                decisions.add(new MatchDecision<>(
                    MatchDecision.Status.PHYSICAL_ALREADY_USED,
                    null,
                    semantic,
                    MatchReason.NO_MATCH,
                    null,
                    allMatches
                ));
            } else {
                decisions.add(new MatchDecision<>(
                    MatchDecision.Status.UNMATCHED,
                    null,
                    semantic,
                    MatchReason.NO_MATCH,
                    null,
                    List.of()
                ));
            }
        }

        return decisions.stream()
            .sorted(java.util.Comparator
                .comparing((MatchDecision<AttributeMetadata> decision) ->
                    displayName(decision.semantic()))
                .thenComparing(decision -> displayName(decision.physical())))
            .toList();
    }

    private void processPhase(Phase phase,
                              List<AttributeMetadata> unmatchedPhysical,
                              List<AttributeMetadata> unmatchedSemantic,
                              Set<AttributeMetadata> consumedPhysical,
                              List<MatchDecision<AttributeMetadata>> decisions) {
        Map<AttributeMetadata, List<MatchCandidate<AttributeMetadata>>> edges = new LinkedHashMap<>();
        for (AttributeMetadata semantic : unmatchedSemantic) {
            for (AttributeMetadata physical : unmatchedPhysical) {
                String token = phase.matchToken(this, semantic, physical);
                if (token != null) {
                    edges.computeIfAbsent(semantic, key -> new ArrayList<>())
                        .add(new MatchCandidate<>(
                            physical, semantic, phase.reason(), phase.priority(), token));
                }
            }
        }

        List<Set<AttributeMetadata>> components =
            connectedComponents(edges);

        for (Set<AttributeMetadata> component : components) {
            Set<AttributeMetadata> physicals = new LinkedHashSet<>();
            Set<AttributeMetadata> semantics = new LinkedHashSet<>();
            for (AttributeMetadata element : component) {
                if (edges.containsKey(element)) {
                    semantics.add(element);
                } else {
                    physicals.add(element);
                }
            }
            if (physicals.size() == 1 && semantics.size() == 1) {
                AttributeMetadata physical = physicals.iterator().next();
                AttributeMetadata semantic = semantics.iterator().next();
                MatchCandidate<AttributeMetadata> candidate = edges.get(semantic).get(0);
                decisions.add(new MatchDecision<>(
                    MatchDecision.Status.MATCHED,
                    physical,
                    semantic,
                    candidate.reason(),
                    candidate.token(),
                    List.of(candidate)
                ));
                unmatchedPhysical.remove(physical);
                unmatchedSemantic.remove(semantic);
                consumedPhysical.add(physical);
            } else {
                for (AttributeMetadata semantic : semantics) {
                    decisions.add(new MatchDecision<>(
                        MatchDecision.Status.AMBIGUOUS,
                        null,
                        semantic,
                        phase.reason(),
                        null,
                        edges.getOrDefault(semantic, List.of())
                    ));
                }
                unmatchedSemantic.removeAll(semantics);
                unmatchedPhysical.removeAll(physicals);
            }
        }
    }

    private List<Set<AttributeMetadata>> connectedComponents(
        Map<AttributeMetadata, List<MatchCandidate<AttributeMetadata>>> edges
    ) {
        Map<AttributeMetadata, Set<AttributeMetadata>> reverse =
            new LinkedHashMap<>();
        for (Map.Entry<AttributeMetadata, List<MatchCandidate<AttributeMetadata>>> entry : edges.entrySet()) {
            for (MatchCandidate<AttributeMetadata> candidate : entry.getValue()) {
                reverse.computeIfAbsent(candidate.physical(), key -> new LinkedHashSet<>())
                    .add(entry.getKey());
            }
        }

        List<Set<AttributeMetadata>> components = new ArrayList<>();
        Set<AttributeMetadata> seen = new LinkedHashSet<>();
        for (AttributeMetadata semantic : edges.keySet()) {
            if (!seen.add(semantic)) {
                continue;
            }
            Set<AttributeMetadata> component = new LinkedHashSet<>();
            java.util.Deque<AttributeMetadata> queue = new java.util.ArrayDeque<>();
            queue.add(semantic);
            while (!queue.isEmpty()) {
                AttributeMetadata current = queue.removeFirst();
                if (!component.add(current)) {
                    continue;
                }
                for (MatchCandidate<AttributeMetadata> candidate
                    : edges.getOrDefault(current, List.of())) {
                    AttributeMetadata physical = candidate.physical();
                    if (seen.add(physical)) {
                        queue.add(physical);
                    }
                }
                for (AttributeMetadata semanticNeighbor
                    : reverse.getOrDefault(current, Set.of())) {
                    if (seen.add(semanticNeighbor)) {
                        queue.add(semanticNeighbor);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    private List<MatchCandidate<AttributeMetadata>> allCandidates(
        AttributeMetadata semantic,
        List<AttributeMetadata> physicals
    ) {
        List<MatchCandidate<AttributeMetadata>> candidates = new ArrayList<>();
        for (Phase phase : Phase.values()) {
            for (AttributeMetadata physical : physicals) {
                String token = phase.matchToken(this, semantic, physical);
                if (token != null) {
                    candidates.add(new MatchCandidate<>(
                        physical, semantic, phase.reason(), phase.priority(), token));
                }
            }
        }
        return candidates;
    }

    private String displayName(AttributeMetadata attribute) {
        if (attribute == null) {
            return "";
        }
        if (attribute.getQualifiedName() != null) {
            return attribute.getQualifiedName();
        }
        return attribute.getName() == null ? "" : attribute.getName();
    }

    private enum Phase {
        EXACT_QUALIFIED_NAME(MatchReason.EXACT_QUALIFIED_NAME, 1) {
            @Override
            String matchToken(AttributeMatcher matcher,
                              AttributeMetadata semantic,
                              AttributeMetadata physical) {
                String semanticName = semantic.getQualifiedName();
                String physicalName = physical.getQualifiedName();
                if (semanticName == null || physicalName == null) {
                    return null;
                }
                return semanticName.equals(physicalName) ? semanticName : null;
            }
        },
        EXACT_NAME(MatchReason.EXACT_NAME, 2) {
            @Override
            String matchToken(AttributeMatcher matcher,
                              AttributeMetadata semantic,
                              AttributeMetadata physical) {
                String semanticName = semantic.getName();
                String physicalName = physical.getName();
                if (semanticName == null || physicalName == null) {
                    return null;
                }
                return semanticName.equals(physicalName) ? semanticName : null;
            }
        },
        EXACT_COLUMN_NAME(MatchReason.EXACT_COLUMN_NAME, 3) {
            @Override
            String matchToken(AttributeMatcher matcher,
                              AttributeMetadata semantic,
                              AttributeMetadata physical) {
                String semanticName = semantic.getName();
                if (semanticName == null) {
                    return null;
                }
                for (String column : new String[] {
                    physical.getColumnName(), physical.getSqlName()}) {
                    if (column != null && column.equalsIgnoreCase(semanticName)) {
                        return column;
                    }
                }
                return null;
            }
        },
        NORMALIZED_FULL_TOKEN(MatchReason.NORMALIZED_FULL_TOKEN, 4) {
            @Override
            String matchToken(AttributeMatcher matcher,
                              AttributeMetadata semantic,
                              AttributeMetadata physical) {
                Set<MergeTokenNormalizer.NormalizedToken> semanticTokens =
                    matcher.attributeTokens(semantic);
                Set<MergeTokenNormalizer.NormalizedToken> physicalTokens =
                    matcher.attributeTokens(physical);
                for (MergeTokenNormalizer.NormalizedToken semanticToken : semanticTokens) {
                    if (semanticToken.reason() != MatchReason.NORMALIZED_FULL_TOKEN) {
                        continue;
                    }
                    for (MergeTokenNormalizer.NormalizedToken physicalToken : physicalTokens) {
                        if (physicalToken.reason() == MatchReason.NORMALIZED_FULL_TOKEN
                            && physicalToken.value().equals(semanticToken.value())) {
                            return semanticToken.value();
                        }
                    }
                }
                return null;
            }
        },
        NORMALIZED_ID_SUFFIX(MatchReason.NORMALIZED_ID_SUFFIX, 5) {
            @Override
            String matchToken(AttributeMatcher matcher,
                              AttributeMetadata semantic,
                              AttributeMetadata physical) {
                Set<MergeTokenNormalizer.NormalizedToken> semanticTokens =
                    matcher.attributeTokens(semantic);
                Set<MergeTokenNormalizer.NormalizedToken> physicalTokens =
                    matcher.attributeTokens(physical);
                for (MergeTokenNormalizer.NormalizedToken semanticToken : semanticTokens) {
                    for (MergeTokenNormalizer.NormalizedToken physicalToken : physicalTokens) {
                        if (physicalToken.value().equals(semanticToken.value())
                            && (semanticToken.reason() == MatchReason.NORMALIZED_ID_SUFFIX
                            || physicalToken.reason() == MatchReason.NORMALIZED_ID_SUFFIX)) {
                            return semanticToken.value();
                        }
                    }
                }
                return null;
            }
        };

        private final MatchReason reason;
        private final int priority;

        Phase(MatchReason reason, int priority) {
            this.reason = reason;
            this.priority = priority;
        }

        MatchReason reason() {
            return reason;
        }

        int priority() {
            return priority;
        }

        abstract String matchToken(AttributeMatcher matcher,
                                   AttributeMetadata semantic,
                                   AttributeMetadata physical);
    }

    private Set<MergeTokenNormalizer.NormalizedToken> attributeTokens(AttributeMetadata attribute) {
        Set<MergeTokenNormalizer.NormalizedToken> tokens = new LinkedHashSet<>();
        tokens.addAll(normalizer.tokens(attribute.getQualifiedName()));
        tokens.addAll(normalizer.tokens(attribute.getName()));
        tokens.addAll(normalizer.tokens(attribute.getColumnName()));
        tokens.addAll(normalizer.tokens(attribute.getSqlName()));
        return tokens;
    }
}

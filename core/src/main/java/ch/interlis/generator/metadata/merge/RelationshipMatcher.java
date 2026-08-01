package ch.interlis.generator.metadata.merge;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Matcht physische Relationships (ili2db-FKs) mit semantischen Relationships
 * (ili2c-Referenzen, -Kompositionen, -Associations-Rollen).
 *
 * <p>Matchphasen (stärkste zuerst):</p>
 * <ol>
 *   <li>Association-Name plus exakter Rollenname</li>
 *   <li>exaktes {@code sourceAttribute}</li>
 *   <li>exaktes {@code physicalName}</li>
 *   <li>exakter {@code targetRoleName}</li>
 *   <li>exakter Relationship-Name</li>
 *   <li>normalisierter vollständiger Token</li>
 *   <li>normalisierter ID-Suffix-Token</li>
 * </ol>
 *
 * <p>Ein physisches Relationship darf nur einmal konsumiert werden (stabiler
 * Identity-Key). Bei Ambiguität wird kein Kandidat ausgewählt.</p>
 */
public final class RelationshipMatcher {

    private final MergeTokenNormalizer normalizer;

    public RelationshipMatcher(MergeTokenNormalizer normalizer) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
    }

    public List<MatchDecision<RelationshipMetadata>> match(
        ModelMetadata physical,
        ModelMetadata semantic
    ) {
        Objects.requireNonNull(physical, "physical");
        Objects.requireNonNull(semantic, "semantic");

        List<RelationshipMetadata> allPhysical =
            new ArrayList<>(physical.getRelationships());
        List<RelationshipMetadata> unmatchedPhysical = new ArrayList<>(allPhysical);
        List<RelationshipMetadata> unmatchedSemantic =
            new ArrayList<>(semantic.getRelationships());
        List<MatchDecision<RelationshipMetadata>> decisions = new ArrayList<>();
        Set<String> consumedPhysicalKeys = new LinkedHashSet<>();

        for (Phase phase : Phase.values()) {
            processPhase(
                phase,
                unmatchedPhysical,
                unmatchedSemantic,
                consumedPhysicalKeys,
                decisions
            );
        }

        for (RelationshipMetadata semanticRelationship : unmatchedSemantic) {
            List<MatchCandidate<RelationshipMetadata>> allMatches =
                allCandidates(semanticRelationship, allPhysical);
            if (!allMatches.isEmpty() && allMatches.stream()
                .allMatch(candidate -> consumedPhysicalKeys.contains(
                    relationshipIdentity(candidate.physical())))) {
                decisions.add(new MatchDecision<>(
                    MatchDecision.Status.PHYSICAL_ALREADY_USED,
                    null,
                    semanticRelationship,
                    MatchReason.NO_MATCH,
                    null,
                    allMatches
                ));
            } else {
                decisions.add(new MatchDecision<>(
                    MatchDecision.Status.UNMATCHED,
                    null,
                    semanticRelationship,
                    MatchReason.NO_MATCH,
                    null,
                    List.of()
                ));
            }
        }

        return decisions.stream()
            .sorted(java.util.Comparator
                .comparing((MatchDecision<RelationshipMetadata> decision) ->
                    displayName(decision.semantic()))
                .thenComparing(decision -> displayName(decision.physical())))
            .toList();
    }

    private void processPhase(Phase phase,
                              List<RelationshipMetadata> unmatchedPhysical,
                              List<RelationshipMetadata> unmatchedSemantic,
                              Set<String> consumedPhysicalKeys,
                              List<MatchDecision<RelationshipMetadata>> decisions) {
        Map<RelationshipMetadata, List<MatchCandidate<RelationshipMetadata>>> edges =
            new LinkedHashMap<>();
        for (RelationshipMetadata semantic : unmatchedSemantic) {
            for (RelationshipMetadata physical : unmatchedPhysical) {
                if (!compatible(physical, semantic)) {
                    continue;
                }
                String token = phase.matchToken(this, semantic, physical);
                if (token != null) {
                    edges.computeIfAbsent(semantic, key -> new ArrayList<>())
                        .add(new MatchCandidate<>(
                            physical, semantic, phase.reason(), phase.priority(), token));
                }
            }
        }

        List<Set<RelationshipMetadata>> components = connectedComponents(edges);

        for (Set<RelationshipMetadata> component : components) {
            Set<RelationshipMetadata> physicals = new LinkedHashSet<>();
            Set<RelationshipMetadata> semantics = new LinkedHashSet<>();
            for (RelationshipMetadata element : component) {
                if (edges.containsKey(element)) {
                    semantics.add(element);
                } else {
                    physicals.add(element);
                }
            }
            if (physicals.size() == 1 && semantics.size() == 1) {
                RelationshipMetadata physical = physicals.iterator().next();
                RelationshipMetadata semantic = semantics.iterator().next();
                MatchCandidate<RelationshipMetadata> candidate = edges.get(semantic).get(0);
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
                consumedPhysicalKeys.add(relationshipIdentity(physical));
            } else {
                for (RelationshipMetadata semantic : semantics) {
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

    private List<Set<RelationshipMetadata>> connectedComponents(
        Map<RelationshipMetadata, List<MatchCandidate<RelationshipMetadata>>> edges
    ) {
        Map<RelationshipMetadata, Set<RelationshipMetadata>> reverse =
            new LinkedHashMap<>();
        for (Map.Entry<RelationshipMetadata, List<MatchCandidate<RelationshipMetadata>>> entry : edges.entrySet()) {
            for (MatchCandidate<RelationshipMetadata> candidate : entry.getValue()) {
                reverse.computeIfAbsent(candidate.physical(), key -> new LinkedHashSet<>())
                    .add(entry.getKey());
            }
        }

        List<Set<RelationshipMetadata>> components = new ArrayList<>();
        Set<RelationshipMetadata> seen = new LinkedHashSet<>();
        for (RelationshipMetadata semantic : edges.keySet()) {
            if (!seen.add(semantic)) {
                continue;
            }
            Set<RelationshipMetadata> component = new LinkedHashSet<>();
            java.util.Deque<RelationshipMetadata> queue = new java.util.ArrayDeque<>();
            queue.add(semantic);
            while (!queue.isEmpty()) {
                RelationshipMetadata current = queue.removeFirst();
                if (!component.add(current)) {
                    continue;
                }
                for (MatchCandidate<RelationshipMetadata> candidate
                    : edges.getOrDefault(current, List.of())) {
                    RelationshipMetadata physical = candidate.physical();
                    if (seen.add(physical)) {
                        queue.add(physical);
                    }
                }
                for (RelationshipMetadata semanticNeighbor
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

    /**
     * Vorausfilter: Kandidaten werden nur verglichen, wenn Source/Target identisch,
     * Association-Namen nicht widersprechen und die Relationship-Arten kompatibel sind.
     */
    private boolean compatible(RelationshipMetadata physical, RelationshipMetadata semantic) {
        if (!Objects.equals(physical.getSourceClass(), semantic.getSourceClass())
            || !Objects.equals(physical.getTargetClass(), semantic.getTargetClass())) {
            return false;
        }
        String physicalAssociation = physical.getAssociationName();
        String semanticAssociation = semantic.getAssociationName();
        if (physicalAssociation != null && semanticAssociation != null
            && !physicalAssociation.equals(semanticAssociation)) {
            return false;
        }
        if (physical.getSemanticKind() != RelationshipMetadata.SemanticKind.ILI2DB_FK) {
            return false;
        }
        return semantic.getSemanticKind() == RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE
            || semantic.getSemanticKind() == RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
            || semantic.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE;
    }

    private List<MatchCandidate<RelationshipMetadata>> allCandidates(
        RelationshipMetadata semantic,
        List<RelationshipMetadata> physicals
    ) {
        List<MatchCandidate<RelationshipMetadata>> candidates = new ArrayList<>();
        for (Phase phase : Phase.values()) {
            for (RelationshipMetadata physical : physicals) {
                if (!compatible(physical, semantic)) {
                    continue;
                }
                String token = phase.matchToken(this, semantic, physical);
                if (token != null) {
                    candidates.add(new MatchCandidate<>(
                        physical, semantic, phase.reason(), phase.priority(), token));
                }
            }
        }
        return candidates;
    }

    /**
     * Stabiler Identity-Key eines physischen Relationships.
     */
    private String relationshipIdentity(RelationshipMetadata relationship) {
        RelationshipIdentity identity = new RelationshipIdentity(
            relationship.getSourceClass(),
            relationship.getTargetClass(),
            relationship.getSourceAttribute(),
            relationship.getPhysicalName(),
            relationship.getTargetRoleName(),
            relationship.getSemanticKind()
        );
        return identity.toString();
    }

    record RelationshipIdentity(
        String sourceClass,
        String targetClass,
        String sourceAttribute,
        String physicalName,
        String targetRoleName,
        RelationshipMetadata.SemanticKind semanticKind
    ) {
    }

    private String displayName(RelationshipMetadata relationship) {
        if (relationship == null) {
            return "";
        }
        if (relationship.getName() != null) {
            return relationship.getName();
        }
        return String.valueOf(relationship.getSourceClass()) + "."
            + String.valueOf(relationship.getTargetClass());
    }

    private enum Phase {
        ASSOCIATION_NAME_AND_ROLE(MatchReason.EXACT_TARGET_ROLE, 1) {
            @Override
            String matchToken(RelationshipMatcher matcher,
                              RelationshipMetadata semantic,
                              RelationshipMetadata physical) {
                String semanticAssociation = semantic.getAssociationName();
                String physicalAssociation = physical.getAssociationName();
                String semanticRole = semantic.getTargetRoleName();
                String physicalRole = physical.getTargetRoleName();
                if (semanticAssociation == null || physicalAssociation == null
                    || semanticRole == null || physicalRole == null) {
                    return null;
                }
                return semanticAssociation.equals(physicalAssociation)
                    && semanticRole.equals(physicalRole) ? semanticRole : null;
            }
        },
        EXACT_SOURCE_ATTRIBUTE(MatchReason.EXACT_SOURCE_ATTRIBUTE, 2) {
            @Override
            String matchToken(RelationshipMatcher matcher,
                              RelationshipMetadata semantic,
                              RelationshipMetadata physical) {
                String semanticAttribute = semantic.getSourceAttribute();
                String physicalAttribute = physical.getSourceAttribute();
                if (semanticAttribute == null || physicalAttribute == null) {
                    return null;
                }
                return semanticAttribute.equals(physicalAttribute) ? semanticAttribute : null;
            }
        },
        EXACT_PHYSICAL_NAME(MatchReason.EXACT_PHYSICAL_NAME, 3) {
            @Override
            String matchToken(RelationshipMatcher matcher,
                              RelationshipMetadata semantic,
                              RelationshipMetadata physical) {
                if (physical.getPhysicalName() != null
                    && physical.getPhysicalName().equals(semantic.getSourceAttribute())) {
                    return physical.getPhysicalName();
                }
                if (semantic.getPhysicalName() != null
                    && semantic.getPhysicalName().equals(physical.getSourceAttribute())) {
                    return semantic.getPhysicalName();
                }
                return null;
            }
        },
        EXACT_TARGET_ROLE(MatchReason.EXACT_TARGET_ROLE, 4) {
            @Override
            String matchToken(RelationshipMatcher matcher,
                              RelationshipMetadata semantic,
                              RelationshipMetadata physical) {
                String semanticRole = semantic.getTargetRoleName();
                String physicalRole = physical.getTargetRoleName();
                if (semanticRole == null || physicalRole == null) {
                    return null;
                }
                return semanticRole.equals(physicalRole) ? semanticRole : null;
            }
        },
        EXACT_NAME(MatchReason.EXACT_NAME, 5) {
            @Override
            String matchToken(RelationshipMatcher matcher,
                              RelationshipMetadata semantic,
                              RelationshipMetadata physical) {
                String semanticName = semantic.getName();
                String physicalName = physical.getName();
                if (semanticName == null || physicalName == null) {
                    return null;
                }
                return semanticName.equals(physicalName) ? semanticName : null;
            }
        },
        NORMALIZED_FULL_TOKEN(MatchReason.NORMALIZED_FULL_TOKEN, 6) {
            @Override
            String matchToken(RelationshipMatcher matcher,
                              RelationshipMetadata semantic,
                              RelationshipMetadata physical) {
                Set<MergeTokenNormalizer.NormalizedToken> semanticTokens =
                    matcher.relationshipTokens(semantic);
                Set<MergeTokenNormalizer.NormalizedToken> physicalTokens =
                    matcher.relationshipTokens(physical);
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
        NORMALIZED_ID_SUFFIX(MatchReason.NORMALIZED_ID_SUFFIX, 7) {
            @Override
            String matchToken(RelationshipMatcher matcher,
                              RelationshipMetadata semantic,
                              RelationshipMetadata physical) {
                Set<MergeTokenNormalizer.NormalizedToken> semanticTokens =
                    matcher.relationshipTokens(semantic);
                Set<MergeTokenNormalizer.NormalizedToken> physicalTokens =
                    matcher.relationshipTokens(physical);
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

        abstract String matchToken(RelationshipMatcher matcher,
                                   RelationshipMetadata semantic,
                                   RelationshipMetadata physical);
    }

    private Set<MergeTokenNormalizer.NormalizedToken> relationshipTokens(RelationshipMetadata relationship) {
        Set<MergeTokenNormalizer.NormalizedToken> tokens = new LinkedHashSet<>();
        tokens.addAll(normalizer.tokens(relationship.getTargetRoleName()));
        tokens.addAll(normalizer.tokens(relationship.getSourceAttribute()));
        tokens.addAll(normalizer.tokens(relationship.getPhysicalName()));
        tokens.addAll(normalizer.tokens(relationship.getSemanticName()));
        tokens.addAll(normalizer.tokens(relationship.getName()));
        return tokens;
    }
}

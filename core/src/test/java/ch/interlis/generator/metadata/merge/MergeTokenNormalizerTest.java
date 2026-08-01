package ch.interlis.generator.metadata.merge;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class MergeTokenNormalizerTest {

    private final MergeTokenNormalizer normalizer = new MergeTokenNormalizer();

    private Set<String> tokenValues(String value) {
        return normalizer.tokens(value).stream()
            .map(MergeTokenNormalizer.NormalizedToken::value)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Test
    void ownerFk() {
        assertThat(tokenValues("owner_fk"))
            .containsExactly("owner_fk", "fk");
    }

    @Test
    void ownerId() {
        assertThat(tokenValues("owner_id"))
            .containsExactly("owner_id", "owner", "id");
    }

    @Test
    void ownerCamelCaseId() {
        assertThat(tokenValues("ownerId"))
            .containsExactly("ownerid", "owner");
    }

    @Test
    void qualifiedName() {
        assertThat(tokenValues("Model.Topic.Class.Owner"))
            .containsExactly("model.topic.class.owner", "owner");
    }

    @Test
    void semanticOwner() {
        assertThat(tokenValues("semantic-owner"))
            .containsExactly("semantic_owner", "owner");
    }

    @Test
    void gridMustNotBecomeGr() {
        assertThat(tokenValues("grid"))
            .containsExactly("grid");
    }

    @Test
    void invalidMustNotBeCut() {
        assertThat(tokenValues("invalid"))
            .containsExactly("invalid");
    }

    @Test
    void plainId() {
        assertThat(tokenValues("id"))
            .containsExactly("id");
    }

    @Test
    void upperAndLowerCaseAreNormalized() {
        assertThat(tokenValues("OWNER_ID"))
            .containsExactly("owner_id", "owner", "id");
    }

    @Test
    void nullAndBlankProduceEmptySet() {
        assertThat(normalizer.tokens(null)).isEmpty();
        assertThat(normalizer.tokens("")).isEmpty();
        assertThat(normalizer.tokens("   ")).isEmpty();
    }

    @Test
    void reasonsAreAttached() {
        Set<MergeTokenNormalizer.NormalizedToken> tokens = normalizer.tokens("owner_id");
        assertThat(tokens.stream()
            .filter(token -> token.value().equals("owner_id"))
            .allMatch(token -> token.reason() == MatchReason.NORMALIZED_FULL_TOKEN)).isTrue();
        assertThat(tokens.stream()
            .filter(token -> token.value().equals("owner"))
            .allMatch(token -> token.reason() == MatchReason.NORMALIZED_ID_SUFFIX)).isTrue();
        assertThat(tokens.stream()
            .filter(token -> token.value().equals("id"))
            .allMatch(token -> token.reason() == MatchReason.NORMALIZED_ID_SUFFIX)).isTrue();
    }

    @Test
    void normalizeExactTrimsAndNormalizes() {
        assertThat(normalizer.normalizeExact("  Semantic-Owner  ")).isEqualTo("semantic_owner");
        assertThat(normalizer.normalizeExact(null)).isNull();
    }

    @Test
    void noDuplicateTokens() {
        assertThat(tokenValues("owner_id_owner"))
            .containsExactly("owner_id_owner", "owner");
        assertThat(normalizer.tokens("owner_id_owner")).hasSize(2);
    }
}

package ch.interlis.generator.metadata.merge;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AttributeMatcherTest {

    private final AttributeMatcher matcher =
        new AttributeMatcher(new MergeTokenNormalizer());

    private static ClassMetadata physicalClass(AttributeMetadata... attributes) {
        ClassMetadata clazz = new ClassMetadata("Model.Topic.Physical");
        for (AttributeMetadata attribute : attributes) {
            clazz.addAttribute(attribute);
        }
        return clazz;
    }

    private static ClassMetadata semanticClass(AttributeMetadata... attributes) {
        ClassMetadata clazz = new ClassMetadata("Model.Topic.Semantic");
        for (AttributeMetadata attribute : attributes) {
            clazz.addAttribute(attribute);
        }
        return clazz;
    }

    private static AttributeMetadata physical(String name, String columnName) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setQualifiedName("Model.Topic.Physical." + name);
        attribute.setColumnName(columnName);
        attribute.setSqlName(columnName);
        return attribute;
    }

    private static AttributeMetadata semantic(String name) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setQualifiedName("Model.Topic.Semantic." + name);
        return attribute;
    }

    @Test
    void exactQualifiedNameMatches() {
        AttributeMetadata physical = new AttributeMetadata("street");
        physical.setQualifiedName("Model.Topic.Physical.street");
        AttributeMetadata semantic = new AttributeMetadata("street");
        semantic.setQualifiedName("Model.Topic.Physical.street");

        List<MatchDecision<AttributeMetadata>> decisions =
            matcher.match(physicalClass(physical), semanticClass(semantic));

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.EXACT_QUALIFIED_NAME);
            assertThat(decision.physical()).isSameAs(physical);
        });
    }

    @Test
    void exactNameMatches() {
        List<MatchDecision<AttributeMetadata>> decisions =
            matcher.match(physicalClass(physical("street", "astreet")),
                semanticClass(semantic("street")));

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.EXACT_NAME);
        });
    }

    @Test
    void exactColumnNameMatches() {
        List<MatchDecision<AttributeMetadata>> decisions =
            matcher.match(physicalClass(physical("Street", "street")),
                semanticClass(semantic("street")));

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.EXACT_COLUMN_NAME);
        });
    }

    @Test
    void uniqueIdSuffixFallbackMatches() {
        AttributeMetadata physical = physical("OwnerRef", "owner_id");
        AttributeMetadata semantic = semantic("Owner");

        List<MatchDecision<AttributeMetadata>> decisions =
            matcher.match(physicalClass(physical), semanticClass(semantic));

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.NORMALIZED_ID_SUFFIX);
            assertThat(decision.token()).isEqualTo("owner");
        });
    }

    @Test
    void twoPhysicalCandidatesAreAmbiguous() {
        List<MatchDecision<AttributeMetadata>> decisions =
            matcher.match(
                physicalClass(physical("OwnerRef", "owner_id"), physical("TheOwnerRef", "the_owner")),
                semanticClass(semantic("Owner")));

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.AMBIGUOUS);
            assertThat(decision.candidates()).hasSize(2);
        });
    }

    @Test
    void twoSemanticCandidatesAreAmbiguous() {
        AttributeMetadata physical = physical("OwnerRef", "owner_id");
        AttributeMetadata semanticOne = semantic("Owner");
        AttributeMetadata semanticTwo = semantic("The_Owner");

        List<MatchDecision<AttributeMetadata>> decisions =
            matcher.match(physicalClass(physical), semanticClass(semanticOne, semanticTwo));

        assertThat(decisions).hasSize(2)
            .allSatisfy(decision -> {
                assertThat(decision.status()).isEqualTo(MatchDecision.Status.AMBIGUOUS);
                assertThat(decision.candidates()).singleElement()
                    .extracting(candidate -> candidate.physical())
                    .isEqualTo(physical);
            });
    }

    @Test
    void reversingInputOrderProducesIdenticalDecisions() {
        AttributeMetadata first = physical("OwnerRef", "owner_id");
        AttributeMetadata second = physical("NameRef", "name_id");
        AttributeMetadata semanticFirst = semantic("Owner");
        AttributeMetadata semanticSecond = semantic("Name");

        List<MatchDecision<AttributeMetadata>> forward = matcher.match(
            physicalClass(first, second), semanticClass(semanticFirst, semanticSecond));
        List<MatchDecision<AttributeMetadata>> reversed = matcher.match(
            physicalClass(second, first), semanticClass(semanticSecond, semanticFirst));

        assertThat(forward.stream().map(MatchDecision::status).toList())
            .containsExactlyElementsOf(reversed.stream().map(MatchDecision::status).toList());
        assertThat(forward.stream().map(MatchDecision::token).toList())
            .containsExactlyElementsOf(reversed.stream().map(MatchDecision::token).toList());
    }

    @Test
    void weakTokenDoesNotOverrideExactMatch() {
        AttributeMetadata exact = physical("Name", "name");
        AttributeMetadata weak = physical("NameId", "name_id");
        AttributeMetadata semantic = semantic("Name");

        List<MatchDecision<AttributeMetadata>> decisions =
            matcher.match(physicalClass(exact, weak), semanticClass(semantic));

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.EXACT_NAME);
            assertThat(decision.physical()).isSameAs(exact);
        });
    }

    @Test
    void unmatchedSemanticAttributeIsReported() {
        List<MatchDecision<AttributeMetadata>> decisions =
            matcher.match(physicalClass(physical("name", "name")),
                semanticClass(semantic("onlyInSemantic")));

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.UNMATCHED);
            assertThat(decision.semantic().getName()).isEqualTo("onlyInSemantic");
        });
    }

    @Test
    void physicalAlreadyUsedIsReported() {
        AttributeMetadata used = physical("Name", "name");
        AttributeMetadata semanticOne = semantic("Name");
        AttributeMetadata semanticTwo = semantic("The_Name");

        List<MatchDecision<AttributeMetadata>> decisions =
            matcher.match(physicalClass(used), semanticClass(semanticOne, semanticTwo));

        assertThat(decisions).hasSize(2);
        assertThat(decisions).filteredOn(decision -> decision.status()
                == MatchDecision.Status.MATCHED).singleElement()
            .satisfies(decision -> assertThat(decision.semantic()).isSameAs(semanticOne));
        assertThat(decisions).filteredOn(decision -> decision.status()
                == MatchDecision.Status.PHYSICAL_ALREADY_USED).singleElement()
            .satisfies(decision -> assertThat(decision.semantic()).isSameAs(semanticTwo));
    }
}

package ch.interlis.generator.metadata.merge;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RelationshipMatcherTest {

    private final RelationshipMatcher matcher =
        new RelationshipMatcher(new MergeTokenNormalizer());

    private static ModelMetadata model() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        metadata.addClass(new ClassMetadata("TestModel.Topic.Parent"));
        metadata.addClass(new ClassMetadata("TestModel.Topic.Child"));
        metadata.addClass(new ClassMetadata("TestModel.Topic.Other"));
        metadata.addClass(new ClassMetadata("TestModel.Topic.PersonAddress"));
        return metadata;
    }

    private static RelationshipMetadata physicalFk(String name,
                                                   String sourceClass,
                                                   String targetClass,
                                                   String sourceAttribute,
                                                   String targetRoleName) {
        RelationshipMetadata relationship = new RelationshipMetadata(name);
        relationship.setSourceClass(sourceClass);
        relationship.setTargetClass(targetClass);
        relationship.setSourceAttribute(sourceAttribute);
        relationship.setPhysicalName(sourceAttribute);
        relationship.setTargetRoleName(targetRoleName);
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        relationship.setSource("ili2db");
        return relationship;
    }

    private static RelationshipMetadata semanticReference(String name,
                                                          String sourceClass,
                                                          String targetClass,
                                                          String sourceAttribute,
                                                          String targetRoleName) {
        RelationshipMetadata relationship = new RelationshipMetadata(name);
        relationship.setSourceClass(sourceClass);
        relationship.setTargetClass(targetClass);
        relationship.setSourceAttribute(sourceAttribute);
        relationship.setTargetRoleName(targetRoleName);
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE);
        relationship.setSource("ili2c");
        relationship.setSemanticName(sourceClass + "." + sourceAttribute);
        return relationship;
    }

    private static RelationshipMetadata semanticAssociationRole(String name,
                                                                String sourceClass,
                                                                String targetClass,
                                                                String sourceAttribute,
                                                                String targetRoleName,
                                                                String associationName) {
        RelationshipMetadata relationship = new RelationshipMetadata(name);
        relationship.setSourceClass(sourceClass);
        relationship.setTargetClass(targetClass);
        relationship.setSourceAttribute(sourceAttribute);
        relationship.setTargetRoleName(targetRoleName);
        relationship.setAssociationName(associationName);
        relationship.setType(RelationshipMetadata.RelationType.ASSOCIATION);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
        relationship.setSource("ili2c");
        relationship.setSemanticName(associationName + "." + targetRoleName);
        return relationship;
    }

    @Test
    void exactSourceAttributeMatches() {
        ModelMetadata physical = model();
        physical.addRelationship(physicalFk("Child_parent",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "parent_id", "Parent"));
        ModelMetadata semantic = model();
        semantic.addRelationship(semanticReference("Child.Parent",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "parent_id", "Parent"));

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.EXACT_SOURCE_ATTRIBUTE);
        });
    }

    @Test
    void exactAssociationRoleMatches() {
        ModelMetadata physical = model();
        physical.addRelationship(physicalFk("PersonAddress_person",
            "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "person_id", "Person"));
        ModelMetadata semantic = model();
        semantic.addRelationship(semanticAssociationRole("PersonAddress.Person",
            "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "Person", "Person",
            "TestModel.Topic.PersonAddress"));

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.EXACT_TARGET_ROLE);
        });
    }

    @Test
    void physicallyDeviatingRoleNameMatchesViaWeakToken() {
        ModelMetadata physical = model();
        physical.addRelationship(physicalFk("MismatchChild_owner",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "owner_fk", "OwnerRef"));
        ModelMetadata semantic = model();
        semantic.addRelationship(semanticReference("Child.Owner",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "Owner", "Owner"));

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.NORMALIZED_ID_SUFFIX);
            assertThat(decision.token()).isEqualTo("owner");
        });
    }

    @Test
    void twoFksToSameTargetClassRemainSeparate() {
        ModelMetadata physical = model();
        physical.addRelationship(physicalFk("Journey_departure",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "departure_id", "Departure"));
        physical.addRelationship(physicalFk("Journey_arrival",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "arrival_id", "Arrival"));
        ModelMetadata semantic = model();
        semantic.addRelationship(semanticReference("Child.Departure",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "Departure", "Departure"));
        semantic.addRelationship(semanticReference("Child.Arrival",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "Arrival", "Arrival"));

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).hasSize(2)
            .allSatisfy(decision -> assertThat(decision.status())
                .isEqualTo(MatchDecision.Status.MATCHED));
        assertThat(decisions.stream().map(MatchDecision::token).toList())
            .containsExactlyInAnyOrder("Departure", "Arrival");
    }

    @Test
    void twoAssociationRolesOnSameTargetClassRemainSeparate() {
        ModelMetadata physical = model();
        physical.addRelationship(physicalFk("SameTarget_primary",
            "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "primaryowner", "PrimaryOwner"));
        physical.addRelationship(physicalFk("SameTarget_secondary",
            "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "secondaryowner", "SecondaryOwner"));
        ModelMetadata semantic = model();
        semantic.addRelationship(semanticAssociationRole("SameTarget.PrimaryOwner",
            "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "PrimaryOwner", "PrimaryOwner",
            "TestModel.Topic.SameTarget"));
        semantic.addRelationship(semanticAssociationRole("SameTarget.SecondaryOwner",
            "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "SecondaryOwner", "SecondaryOwner",
            "TestModel.Topic.SameTarget"));

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).hasSize(2)
            .allSatisfy(decision -> assertThat(decision.status())
                .isEqualTo(MatchDecision.Status.MATCHED));
        assertThat(decisions.stream().map(MatchDecision::reason).toList())
            .containsOnly(MatchReason.EXACT_TARGET_ROLE);
    }

    @Test
    void physicalRelationshipAlreadyUsedIsReported() {
        ModelMetadata physical = model();
        RelationshipMetadata used = physicalFk("Child_parent",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "parent_id", "Parent");
        physical.addRelationship(used);
        ModelMetadata semantic = model();
        semantic.addRelationship(semanticReference("Child.Parent",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "Parent", "Parent"));
        semantic.addRelationship(semanticReference("Child.The_Parent",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "The_Parent", "The_Parent"));

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).hasSize(2);
        assertThat(decisions).filteredOn(MatchDecision::matched).singleElement()
            .satisfies(decision -> assertThat(decision.semantic().getName())
                .isEqualTo("Child.Parent"));
        assertThat(decisions).filteredOn(decision -> decision.status()
                == MatchDecision.Status.PHYSICAL_ALREADY_USED).singleElement()
            .satisfies(decision -> assertThat(decision.semantic().getName())
                .isEqualTo("Child.The_Parent"));
    }

    @Test
    void uniqueNormalizedMatchWorks() {
        ModelMetadata physical = model();
        physical.addRelationship(physicalFk("Child_parent",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "parent_id", "ParentRef"));
        ModelMetadata semantic = model();
        semantic.addRelationship(semanticReference("Child.Parent",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "Parent", "Parent"));

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.NORMALIZED_ID_SUFFIX);
            assertThat(decision.token()).isEqualTo("parent");
        });
    }

    @Test
    void ambiguousNormalizedMatchIsReported() {
        ModelMetadata physical = model();
        physical.addRelationship(physicalFk("Child_owner",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "owner_id", "OwnerRef"));
        physical.addRelationship(physicalFk("Child_theOwner",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "the_owner", "TheOwnerRef"));
        ModelMetadata semantic = model();
        semantic.addRelationship(semanticReference("Child.Owner",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "Owner", "Owner"));

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.AMBIGUOUS);
            assertThat(decision.candidates()).hasSize(2);
        });
    }

    @Test
    void reversingOrderProducesIdenticalDecisions() {
        ModelMetadata physical = model();
        RelationshipMetadata first = physicalFk("Child_parent",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "parent_id", "Parent");
        RelationshipMetadata second = physicalFk("Child_other",
            "TestModel.Topic.Child", "TestModel.Topic.Other", "other_id", "Other");
        physical.addRelationship(first);
        physical.addRelationship(second);
        ModelMetadata semantic = model();
        semantic.addRelationship(semanticReference("Child.Parent",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "Parent", "Parent"));
        semantic.addRelationship(semanticReference("Child.Other",
            "TestModel.Topic.Child", "TestModel.Topic.Other", "Other", "Other"));

        List<MatchDecision<RelationshipMetadata>> forward = matcher.match(physical, semantic);
        List<MatchDecision<RelationshipMetadata>> reversed = matcher.match(
            modelWith(second, first), modelWith(
                semanticReference("Child.Other", "TestModel.Topic.Child",
                    "TestModel.Topic.Other", "Other", "Other"),
                semanticReference("Child.Parent", "TestModel.Topic.Child",
                    "TestModel.Topic.Parent", "Parent", "Parent")));

        assertThat(forward.stream().map(MatchDecision::status).toList())
            .containsExactlyElementsOf(reversed.stream().map(MatchDecision::status).toList());
        assertThat(forward.stream().map(MatchDecision::token).toList())
            .containsExactlyElementsOf(reversed.stream().map(MatchDecision::token).toList());
    }

    private static ModelMetadata modelWith(RelationshipMetadata... relationships) {
        ModelMetadata metadata = model();
        for (RelationshipMetadata relationship : relationships) {
            metadata.addRelationship(relationship);
        }
        return metadata;
    }
}

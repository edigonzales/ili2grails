package ch.interlis.generator.metadata.merge;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.model.builder.RelationshipMetadataBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RelationshipMatcherTest {

    private final RelationshipMatcher matcher =
        new RelationshipMatcher(new MergeTokenNormalizer());

    private static ModelMetadata model(RelationshipMetadata... relationships) {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("TestModel");
        builder.classBuilder("TestModel.Topic.Parent");
        builder.classBuilder("TestModel.Topic.Child");
        builder.classBuilder("TestModel.Topic.Other");
        builder.classBuilder("TestModel.Topic.PersonAddress");
        for (RelationshipMetadata relationship : relationships) {
            builder.relationship(RelationshipMetadataBuilder.from(relationship));
        }
        return new ModelMetadataFactory().buildValidated(builder);
    }

    private static RelationshipMetadata physicalFk(String name,
                                                   String sourceClass,
                                                   String targetClass,
                                                   String sourceAttribute,
                                                   String targetRoleName) {
        return RelationshipMetadata.builder(name)
            .sourceClass(sourceClass)
            .targetClass(targetClass)
            .sourceAttribute(sourceAttribute)
            .physicalName(sourceAttribute)
            .targetRoleName(targetRoleName)
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK)
            .source("ili2db")
            .buildUnchecked();
    }

    private static RelationshipMetadata semanticReference(String name,
                                                          String sourceClass,
                                                          String targetClass,
                                                          String sourceAttribute,
                                                          String targetRoleName) {
        return RelationshipMetadata.builder(name)
            .sourceClass(sourceClass)
            .targetClass(targetClass)
            .sourceAttribute(sourceAttribute)
            .targetRoleName(targetRoleName)
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE)
            .source("ili2c")
            .semanticName(sourceClass + "." + sourceAttribute)
            .buildUnchecked();
    }

    private static RelationshipMetadata semanticAssociationRole(String name,
                                                                String sourceClass,
                                                                String targetClass,
                                                                String sourceAttribute,
                                                                String targetRoleName,
                                                                String associationName) {
        return RelationshipMetadata.builder(name)
            .sourceClass(sourceClass)
            .targetClass(targetClass)
            .sourceAttribute(sourceAttribute)
            .targetRoleName(targetRoleName)
            .associationName(associationName)
            .type(RelationshipMetadata.RelationType.ASSOCIATION)
            .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .source("ili2c")
            .semanticName(associationName + "." + targetRoleName)
            .buildUnchecked();
    }

    @Test
    void exactSourceAttributeMatches() {
        ModelMetadata physical = model(
            physicalFk("Child_parent",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "parent_id", "Parent")
        );
        ModelMetadata semantic = model(
            semanticReference("Child.Parent",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "parent_id", "Parent")
        );

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.EXACT_SOURCE_ATTRIBUTE);
        });
    }

    @Test
    void exactAssociationRoleMatches() {
        ModelMetadata physical = model(
            physicalFk("PersonAddress_person",
                        "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "person_id", "Person")
        );
        ModelMetadata semantic = model(
            semanticAssociationRole("PersonAddress.Person",
                        "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "Person", "Person",
                        "TestModel.Topic.PersonAddress")
        );

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.EXACT_TARGET_ROLE);
        });
    }

    @Test
    void physicallyDeviatingRoleNameMatchesViaWeakToken() {
        ModelMetadata physical = model(
            physicalFk("MismatchChild_owner",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "owner_fk", "OwnerRef")
        );
        ModelMetadata semantic = model(
            semanticReference("Child.Owner",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "Owner", "Owner")
        );

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.NORMALIZED_ID_SUFFIX);
            assertThat(decision.token()).isEqualTo("owner");
        });
    }

    @Test
    void twoFksToSameTargetClassRemainSeparate() {
        ModelMetadata physical = model(
            physicalFk("Journey_departure",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "departure_id", "Departure"),
            physicalFk("Journey_arrival",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "arrival_id", "Arrival")
        );
        ModelMetadata semantic = model(
            semanticReference("Child.Departure",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "Departure", "Departure"),
            semanticReference("Child.Arrival",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "Arrival", "Arrival")
        );

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).hasSize(2)
            .allSatisfy(decision -> assertThat(decision.status())
                .isEqualTo(MatchDecision.Status.MATCHED));
        assertThat(decisions.stream().map(MatchDecision::token).toList())
            .containsExactlyInAnyOrder("Departure", "Arrival");
    }

    @Test
    void twoAssociationRolesOnSameTargetClassRemainSeparate() {
        ModelMetadata physical = model(
            physicalFk("SameTarget_primary",
                        "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "primaryowner", "PrimaryOwner"),
            physicalFk("SameTarget_secondary",
                        "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "secondaryowner", "SecondaryOwner")
        );
        ModelMetadata semantic = model(
            semanticAssociationRole("SameTarget.PrimaryOwner",
                        "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "PrimaryOwner", "PrimaryOwner",
                        "TestModel.Topic.SameTarget"),
            semanticAssociationRole("SameTarget.SecondaryOwner",
                        "TestModel.Topic.PersonAddress", "TestModel.Topic.Parent", "SecondaryOwner", "SecondaryOwner",
                        "TestModel.Topic.SameTarget")
        );

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).hasSize(2)
            .allSatisfy(decision -> assertThat(decision.status())
                .isEqualTo(MatchDecision.Status.MATCHED));
        assertThat(decisions.stream().map(MatchDecision::reason).toList())
            .containsOnly(MatchReason.EXACT_TARGET_ROLE);
    }

    @Test
    void physicalRelationshipAlreadyUsedIsReported() {
        RelationshipMetadata used = physicalFk("Child_parent",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "parent_id", "Parent");
        ModelMetadata physical = model(used);
        ModelMetadata semantic = model(
            semanticReference("Child.Parent",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "Parent", "Parent"),
            semanticReference("Child.The_Parent",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "The_Parent", "The_Parent")
        );

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
        ModelMetadata physical = model(
            physicalFk("Child_parent",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "parent_id", "ParentRef")
        );
        ModelMetadata semantic = model(
            semanticReference("Child.Parent",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "Parent", "Parent")
        );

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.MATCHED);
            assertThat(decision.reason()).isEqualTo(MatchReason.NORMALIZED_ID_SUFFIX);
            assertThat(decision.token()).isEqualTo("parent");
        });
    }

    @Test
    void ambiguousNormalizedMatchIsReported() {
        ModelMetadata physical = model(
            physicalFk("Child_owner",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "owner_id", "OwnerRef"),
            physicalFk("Child_theOwner",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "the_owner", "TheOwnerRef")
        );
        ModelMetadata semantic = model(
            semanticReference("Child.Owner",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "Owner", "Owner")
        );

        List<MatchDecision<RelationshipMetadata>> decisions = matcher.match(physical, semantic);

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MatchDecision.Status.AMBIGUOUS);
            assertThat(decision.candidates()).hasSize(2);
        });
    }

    @Test
    void reversingOrderProducesIdenticalDecisions() {
        RelationshipMetadata first = physicalFk("Child_parent",
            "TestModel.Topic.Child", "TestModel.Topic.Parent", "parent_id", "Parent");
        RelationshipMetadata second = physicalFk("Child_other",
            "TestModel.Topic.Child", "TestModel.Topic.Other", "other_id", "Other");
        ModelMetadata physical = model(first, second);
        ModelMetadata semantic = model(
            semanticReference("Child.Parent",
                        "TestModel.Topic.Child", "TestModel.Topic.Parent", "Parent", "Parent"),
            semanticReference("Child.Other",
                        "TestModel.Topic.Child", "TestModel.Topic.Other", "Other", "Other")
        );

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
        return model(relationships);
    }
}

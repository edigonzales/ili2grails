package ch.interlis.generator.metadata.merge;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.reader.Ili2cModelReader;
import ch.interlis.generator.reader.Ili2dbMetadataReader;
import ch.interlis.generator.testsupport.MergeAmbiguityFixtures;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifiziert das Fail-closed-Verhalten des Mergers auf absichtlich
 * mehrdeutigen Fixtures.
 */
class MetadataMergeAmbiguityTest {

    private final MetadataMerger merger = MetadataMerger.defaultMerger();

    @Test
    void ambiguousAttributesProduceBlockingDiagnostics() throws Exception {
        MetadataMergeResult result = mergeAmbiguityCases();

        assertThat(result.hasBlockingDiagnostics()).isTrue();
        assertThat(result.diagnostics())
            .filteredOn(diagnostic -> diagnostic.code()
                == MergeDiagnosticCode.ATTRIBUTE_AMBIGUOUS)
            .hasSize(2)
            .allSatisfy(diagnostic -> {
                assertThat(diagnostic.isBlocking()).isTrue();
                assertThat(diagnostic.semanticElement()).isIn(
                    "Owner", "The_Owner");
                assertThat(diagnostic.details().get("candidateCount")).isEqualTo("1");
            });
    }

    @Test
    void ambiguousRelationshipsProduceBlockingDiagnostics() throws Exception {
        MetadataMergeResult result = mergeAmbiguityCases();

        assertThat(result.diagnostics())
            .filteredOn(diagnostic -> diagnostic.code()
                == MergeDiagnosticCode.RELATIONSHIP_AMBIGUOUS)
            .hasSize(2)
            .allSatisfy(diagnostic -> {
                assertThat(diagnostic.isBlocking()).isTrue();
                assertThat(diagnostic.semanticElement()).contains("Child");
            });
    }

    @Test
    void strictModeBlocksGenerationForAmbiguousFixture() throws Exception {
        MetadataMergeResult result = mergeAmbiguityCases();

        assertThatThrownBy(() -> result.throwIfBlocking())
            .isInstanceOf(MetadataMergeException.class);
    }

    @Test
    void diagnosticModeKeepsResultInspectable() throws Exception {
        MetadataMergeResult result = mergeAmbiguityCases();

        assertThat(result.metadata()).isNotNull();
        assertThat(result.metadata().getAllRelationships())
            .filteredOn(relationship -> "MergeAmbiguityCases.Base.Child"
                .equals(relationship.getSourceClass()))
            .filteredOn(relationship -> relationship.getSemanticKind()
                == RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE)
            .allSatisfy(relationship -> {
                assertThat(relationship.getSource()).isEqualTo("ili2c");
                assertThat(relationship.getMergeConfidence())
                    .isEqualTo(RelationshipMetadata.MergeConfidence.NONE);
            });
        assertThat(result.metadata().getAllRelationships())
            .filteredOn(relationship -> "ili2db".equals(relationship.getSource()))
            .allSatisfy(relationship -> {
                assertThat(relationship.getSemanticKind())
                    .isEqualTo(RelationshipMetadata.SemanticKind.ILI2DB_FK);
                assertThat(relationship.getSourceAttribute()).isEqualTo("owner_id");
            });
    }

    @Test
    void uniquePhysicalMismatchIsResolvedExactly() throws Exception {
        MetadataMergeResult result = mergeAmbiguityCases();

        assertThat(result.metadata().getAllRelationships())
            .filteredOn(relationship -> "MergeAmbiguityCases.Base.MismatchChild"
                .equals(relationship.getSourceClass()))
            .singleElement()
            .satisfies(relationship -> {
                assertThat(relationship.getSource()).isEqualTo("ili2db+ili2c");
                assertThat(relationship.getSemanticKind())
                    .isEqualTo(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE);
                assertThat(relationship.getSourceAttribute()).isEqualTo("owner_fk");
                assertThat(relationship.getMergeReason())
                    .isEqualTo(RelationshipMetadata.MergeReason.EXACT_TARGET_ROLE);
                assertThat(relationship.getMergeConfidence())
                    .isEqualTo(RelationshipMetadata.MergeConfidence.EXACT);
            });
    }

    @Test
    void sameTargetAssociationRolesRemainDistinct() throws Exception {
        MetadataMergeResult result = mergeAmbiguityCases();

        assertThat(result.metadata().getAssociation("MergeAmbiguityCases.Base.SameTargetAssoc"))
            .satisfies(association -> {
                assertThat(association.getRoles())
                    .extracting(role -> role.getName())
                    .containsExactlyInAnyOrder("PrimaryOwner", "SecondaryOwner");
                assertThat(association.getRoles())
                    .extracting(role -> role.getTargetClass())
                    .containsOnly("MergeAmbiguityCases.Base.Owner");
                assertThat(association.getRoles())
                    .extracting(role -> role.getSourceAttribute())
                    .containsExactlyInAnyOrder("primaryowner", "secondaryowner");
            });
        assertThat(result.diagnostics())
            .filteredOn(diagnostic -> diagnostic.code()
                == MergeDiagnosticCode.RELATIONSHIP_AMBIGUOUS)
            .allSatisfy(diagnostic ->
                assertThat(diagnostic.semanticElement()).doesNotContain("SameTargetAssoc"));
    }

    @Test
    void mergedRelationshipsAreDeterministicAcrossOrderings() throws Exception {
        List<String> firstKeys;
        List<String> secondKeys;
        try (Connection connection = newH2Connection("ambiguity_first")) {
            MergeAmbiguityFixtures.createMergeAmbiguityIli2dbFixture(connection);
            ModelMetadata physical = new Ili2dbMetadataReader(connection, null)
                .readMetadata("MergeAmbiguityCases");
            ModelMetadata semantic = new Ili2cModelReader(
                new File("test-models/MergeAmbiguityCases.ili"), null)
                .readMetadata("MergeAmbiguityCases");
            firstKeys = keys(merger.merge(physical, semantic));
        }
        try (Connection connection = newH2Connection("ambiguity_second")) {
            MergeAmbiguityFixtures.createMergeAmbiguityIli2dbFixture(connection);
            ModelMetadata physical = new Ili2dbMetadataReader(connection, null)
                .readMetadata("MergeAmbiguityCases");
            ModelMetadata semantic = new Ili2cModelReader(
                new File("test-models/MergeAmbiguityCases.ili"), null)
                .readMetadata("MergeAmbiguityCases");
            secondKeys = keys(merger.merge(physical, semantic));
        }
        assertThat(firstKeys).isEqualTo(secondKeys);
    }

    private List<String> keys(MetadataMergeResult result) {
        return result.diagnostics().stream()
            .map(diagnostic -> diagnostic.severity() + "|" + diagnostic.code()
                + "|" + diagnostic.semanticElement() + "|" + diagnostic.physicalElement())
            .toList();
    }

    private MetadataMergeResult mergeAmbiguityCases() throws Exception {
        try (Connection connection = newH2Connection("ambiguity")) {
            MergeAmbiguityFixtures.createMergeAmbiguityIli2dbFixture(connection);
            ModelMetadata physical = new Ili2dbMetadataReader(connection, null)
                .readMetadata("MergeAmbiguityCases");
            ModelMetadata semantic = new Ili2cModelReader(
                new File("test-models/MergeAmbiguityCases.ili"), null)
                .readMetadata("MergeAmbiguityCases");
            return merger.merge(physical, semantic);
        }
    }

    private Connection newH2Connection(String name) throws Exception {
        return DriverManager.getConnection(
            "jdbc:h2:mem:" + name + "_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    }
}

package ch.interlis.generator.report;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RelationshipMergeReporterTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    void createsSummariesAndCategoriesFromSyntheticMetadata() {
        ModelMetadata metadata = new ModelMetadata("SyntheticModel");
        metadata.addRelationship(relationship(
            "exact",
            RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE,
            RelationshipMetadata.MergeReason.EXACT_SOURCE_ATTRIBUTE,
            RelationshipMetadata.MergeConfidence.EXACT,
            "exact_id"
        ));
        metadata.addRelationship(relationship(
            "normalized",
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE,
            RelationshipMetadata.MergeReason.NORMALIZED_TOKEN,
            RelationshipMetadata.MergeConfidence.MEDIUM,
            "normalized_id"
        ));
        metadata.addRelationship(relationship(
            "dbOnly",
            RelationshipMetadata.SemanticKind.ILI2DB_FK,
            RelationshipMetadata.MergeReason.ILI2DB_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            "db_only_id"
        ));
        metadata.addRelationship(relationship(
            "ili2cOnly",
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE,
            RelationshipMetadata.MergeReason.ILI2C_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            null
        ));
        metadata.addRelationship(relationship(
            "unknown",
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE,
            null,
            null,
            null
        ));

        RelationshipMergeReport report = new RelationshipMergeReporter().create(metadata);

        assertThat(report.modelName()).isEqualTo("SyntheticModel");
        assertThat(report.totalRelationships()).isEqualTo(5);
        assertThat(report.byMergeReason())
            .containsEntry("EXACT_SOURCE_ATTRIBUTE", 1L)
            .containsEntry("ILI2C_ONLY", 1L)
            .containsEntry("ILI2DB_ONLY", 1L)
            .containsEntry("NORMALIZED_TOKEN", 1L)
            .containsEntry("UNKNOWN", 1L);
        assertThat(report.byMergeConfidence())
            .containsEntry("EXACT", 1L)
            .containsEntry("MEDIUM", 1L)
            .containsEntry("NONE", 2L)
            .containsEntry("UNKNOWN", 1L);
        assertThat(report.exactMatches())
            .extracting(RelationshipMergeReportEntry::name)
            .containsExactly("exact");
        assertThat(report.normalizedTokenMatches())
            .extracting(RelationshipMergeReportEntry::name)
            .containsExactly("normalized");
        assertThat(report.ili2dbOnly())
            .extracting(RelationshipMergeReportEntry::name)
            .containsExactly("dbOnly");
        assertThat(report.ili2cOnly())
            .extracting(RelationshipMergeReportEntry::name)
            .containsExactly("ili2cOnly");
        assertThat(report.mediumConfidence())
            .extracting(RelationshipMergeReportEntry::name)
            .containsExactly("normalized");
        assertThat(report.totalAssociationRoles()).isZero();
        assertThat(report.associationRoles()).isEmpty();
    }

    @Test
    void classifiesSuspiciousRelationshipsExactly() {
        ModelMetadata metadata = new ModelMetadata("SuspiciousModel");
        metadata.addRelationship(relationship(
            "medium",
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE,
            RelationshipMetadata.MergeReason.NORMALIZED_TOKEN,
            RelationshipMetadata.MergeConfidence.MEDIUM,
            "medium_id"
        ));
        metadata.addRelationship(relationship(
            "dbOnly",
            RelationshipMetadata.SemanticKind.ILI2DB_FK,
            RelationshipMetadata.MergeReason.ILI2DB_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            "db_only_id"
        ));
        metadata.addRelationship(relationship(
            "ili2cRef",
            RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE,
            RelationshipMetadata.MergeReason.ILI2C_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            null
        ));
        metadata.addRelationship(relationship(
            "ili2cRole",
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE,
            RelationshipMetadata.MergeReason.ILI2C_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            null
        ));
        metadata.addRelationship(relationship(
            "refMissingPhysical",
            RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE,
            RelationshipMetadata.MergeReason.EXACT_NAME,
            RelationshipMetadata.MergeConfidence.EXACT,
            null
        ));
        metadata.addRelationship(relationship(
            "ili2cComposition",
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE,
            RelationshipMetadata.MergeReason.ILI2C_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            null
        ));
        metadata.addRelationship(relationship(
            "exactWithPhysical",
            RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE,
            RelationshipMetadata.MergeReason.EXACT_SOURCE_ATTRIBUTE,
            RelationshipMetadata.MergeConfidence.EXACT,
            "exact_id"
        ));

        RelationshipMergeReport report = new RelationshipMergeReporter().create(metadata);

        assertThat(report.suspicious())
            .extracting(RelationshipMergeReportEntry::name)
            .containsExactlyInAnyOrder(
                "medium",
                "dbOnly",
                "ili2cRef",
                "ili2cRole",
                "refMissingPhysical"
            );
    }

    @Test
    void writesDeterministicMarkdownGoldenForMergedSimpleAddress() throws Exception {
        RelationshipMergeReport report = new RelationshipMergeReporter()
            .create(MetadataTestFixtures.readMergedSimpleAddressMetadata());
        String markdown = new RelationshipMergeMarkdownWriter().toMarkdown(report);

        assertGolden(markdown, "SimpleAddressModel.merged-h2.md");
    }

    @Test
    void writesDeterministicMarkdownGoldenForMergedAssociationCases() throws Exception {
        RelationshipMergeReport report = new RelationshipMergeReporter()
            .create(MetadataTestFixtures.readMergedAssociationCasesMetadata());
        String markdown = new RelationshipMergeMarkdownWriter().toMarkdown(report);

        assertGolden(markdown, "AssociationCases.merged-h2.md");
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesMachineReadableJsonForMergedSimpleAddress() throws Exception {
        RelationshipMergeReport report = new RelationshipMergeReporter()
            .create(MetadataTestFixtures.readMergedSimpleAddressMetadata());

        Map<String, Object> root = JSON_MAPPER.readValue(
            new RelationshipMergeJsonWriter().toJson(report),
            Map.class
        );

        assertThat(root).containsEntry("modelName", "SimpleAddressModel");
        assertThat(root).containsEntry("totalRelationships", 2);
        assertThat((Map<String, Object>) root.get("byMergeReason"))
            .containsEntry("NORMALIZED_TOKEN", 2);
        assertThat((Map<String, Object>) root.get("byMergeConfidence"))
            .containsEntry("MEDIUM", 2);

        List<Map<String, Object>> suspicious = (List<Map<String, Object>>) root.get("suspicious");
        assertThat(suspicious).hasSize(2);
        assertThat(suspicious)
            .extracting(entry -> entry.get("mergeReason"))
            .containsOnly("NORMALIZED_TOKEN");
        assertThat(suspicious)
            .extracting(entry -> entry.get("physicalName"))
            .containsExactly("address_id", "person_id");

        assertThat(root).containsEntry("totalAssociationRoles", 2);
        assertThat((Map<String, Object>) root.get("associationRolesByMergeReason"))
            .containsEntry("NORMALIZED_TOKEN", 2);
        List<Map<String, Object>> associationRoles =
            (List<Map<String, Object>>) root.get("associationRoles");
        assertThat(associationRoles)
            .extracting(entry -> entry.get("role"))
            .containsExactly("Address", "Person");
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesAssociationRoleDiagnosticsForMergedAssociationCases() throws Exception {
        RelationshipMergeReport report = new RelationshipMergeReporter()
            .create(MetadataTestFixtures.readMergedAssociationCasesMetadata());

        Map<String, Object> root = JSON_MAPPER.readValue(
            new RelationshipMergeJsonWriter().toJson(report),
            Map.class
        );

        assertThat(root).containsEntry("modelName", "AssociationCases");
        assertThat(root).containsEntry("totalAssociationRoles", 15);
        List<Map<String, Object>> associationRoles =
            (List<Map<String, Object>>) root.get("associationRoles");
        assertThat(associationRoles)
            .anySatisfy(role -> {
                assertThat(role).containsEntry("association",
                    "AssociationCases.Base.PhysicalMismatchAssociation");
                assertThat(role).containsEntry("role", "SemanticOwner");
                assertThat(role).containsEntry("physicalName", "owner_fk");
                assertThat(role).containsEntry("mergeReason", "EXACT_TARGET_ROLE");
                assertThat(role).containsEntry("mergeConfidence", "EXACT");
            })
            .anySatisfy(role -> {
                assertThat(role).containsEntry("association",
                    "AssociationCases.Base.SameTargetAssociation");
                assertThat(role).containsEntry("role", "PrimaryPerson");
            })
            .anySatisfy(role -> {
                assertThat(role).containsEntry("association",
                    "AssociationCases.Base.SameTargetAssociation");
                assertThat(role).containsEntry("role", "SecondaryPerson");
            });
    }

    private RelationshipMetadata relationship(String name,
                                              RelationshipMetadata.SemanticKind semanticKind,
                                              RelationshipMetadata.MergeReason mergeReason,
                                              RelationshipMetadata.MergeConfidence mergeConfidence,
                                              String physicalName) {
        RelationshipMetadata relationship = new RelationshipMetadata(name);
        relationship.setSourceClass("Synthetic.Topic." + name + "Source");
        relationship.setTargetClass("Synthetic.Topic." + name + "Target");
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        relationship.setSemanticKind(semanticKind);
        relationship.setSource(sourceFor(mergeReason));
        relationship.setPhysicalName(physicalName);
        relationship.setSemanticName("Synthetic.Topic." + name + ".Role");
        relationship.setSourceAttribute(name + "Ref");
        relationship.setTargetAttribute("T_Id");
        relationship.setTargetRoleName(name + "Role");
        relationship.setMergeReason(mergeReason);
        relationship.setMergeConfidence(mergeConfidence);
        relationship.setMergeToken(name.toLowerCase());
        relationship.setCardinality(new RelationshipMetadata.Cardinality(1, 1, 0, 1));
        return relationship;
    }

    private String sourceFor(RelationshipMetadata.MergeReason mergeReason) {
        if (mergeReason == RelationshipMetadata.MergeReason.ILI2DB_ONLY) {
            return "ili2db";
        }
        if (mergeReason == RelationshipMetadata.MergeReason.ILI2C_ONLY) {
            return "ili2c";
        }
        if (mergeReason == null) {
            return null;
        }
        return "ili2db+ili2c";
    }

    private void assertGolden(String actual, String goldenFile) throws Exception {
        Path expectedPath = Path.of("core/src/test/resources/merge-report-golden", goldenFile);
        if (Boolean.getBoolean("updateMergeReportGolden")
            || "true".equals(System.getenv("UPDATE_MERGE_REPORT_GOLDEN"))) {
            Files.writeString(expectedPath, actual);
        }
        assertThat(actual).isEqualTo(Files.readString(expectedPath));
    }
}

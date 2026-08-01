package ch.interlis.generator.report;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.model.builder.RelationshipMetadataBuilder;
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
        ModelMetadataBuilder metadataBuilder = ModelMetadataBuilder.model("SyntheticModel");
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "exact",
            RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE,
            RelationshipMetadata.MergeReason.EXACT_SOURCE_ATTRIBUTE,
            RelationshipMetadata.MergeConfidence.EXACT,
            "exact_id"
        )));
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "normalized",
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE,
            RelationshipMetadata.MergeReason.NORMALIZED_TOKEN,
            RelationshipMetadata.MergeConfidence.MEDIUM,
            "normalized_id"
        )));
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "dbOnly",
            RelationshipMetadata.SemanticKind.ILI2DB_FK,
            RelationshipMetadata.MergeReason.ILI2DB_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            "db_only_id"
        )));
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "ili2cOnly",
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE,
            RelationshipMetadata.MergeReason.ILI2C_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            null
        )));
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "unknown",
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE,
            null,
            null,
            null
        )));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(metadataBuilder);
    }

    @Test
    void classifiesSuspiciousRelationshipsExactly() {
        ModelMetadataBuilder metadataBuilder = ModelMetadataBuilder.model("SuspiciousModel");
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "medium",
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE,
            RelationshipMetadata.MergeReason.NORMALIZED_TOKEN,
            RelationshipMetadata.MergeConfidence.MEDIUM,
            "medium_id"
        )));
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "dbOnly",
            RelationshipMetadata.SemanticKind.ILI2DB_FK,
            RelationshipMetadata.MergeReason.ILI2DB_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            "db_only_id"
        )));
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "ili2cRef",
            RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE,
            RelationshipMetadata.MergeReason.ILI2C_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            null
        )));
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "ili2cRole",
            RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE,
            RelationshipMetadata.MergeReason.ILI2C_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            null
        )));
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "refMissingPhysical",
            RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE,
            RelationshipMetadata.MergeReason.EXACT_NAME,
            RelationshipMetadata.MergeConfidence.EXACT,
            null
        )));
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "ili2cComposition",
            RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE,
            RelationshipMetadata.MergeReason.ILI2C_ONLY,
            RelationshipMetadata.MergeConfidence.NONE,
            null
        )));
        metadataBuilder.relationship(RelationshipMetadataBuilder.from(relationship(
            "exactWithPhysical",
            RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE,
            RelationshipMetadata.MergeReason.EXACT_SOURCE_ATTRIBUTE,
            RelationshipMetadata.MergeConfidence.EXACT,
            "exact_id"
        )));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(metadataBuilder);

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
        assertThat(root).containsEntry("totalAssociationRoles", 17);
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
        return RelationshipMetadata.builder(name)
            .sourceClass("Synthetic.Topic." + name + "Source")
            .targetClass("Synthetic.Topic." + name + "Target")
            .type(RelationshipMetadata.RelationType.MANY_TO_ONE)
            .semanticKind(semanticKind)
            .source(sourceFor(mergeReason))
            .physicalName(physicalName)
            .semanticName("Synthetic.Topic." + name + ".Role")
            .sourceAttribute(name + "Ref")
            .targetAttribute("T_Id")
            .targetRoleName(name + "Role")
            .mergeReason(mergeReason)
            .mergeConfidence(mergeConfidence)
            .mergeToken(name.toLowerCase())
            .cardinality(ch.interlis.generator.model.Cardinality.of(1, 1, 0, 1))
            .buildUnchecked();
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

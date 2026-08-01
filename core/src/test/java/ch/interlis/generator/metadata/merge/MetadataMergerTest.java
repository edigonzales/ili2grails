package ch.interlis.generator.metadata.merge;

import ch.interlis.generator.metadata.MetadataPostProcessor;
import ch.interlis.generator.metadata.MetadataValidator;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-End-Tests für den MetadataMerger mit den etablierten Testmodellen.
 */
class MetadataMergerTest {

    private final MetadataMerger merger = MetadataMerger.defaultMerger();

    @Test
    void simpleAddressModelMergesDeterministically() throws Exception {
        try (var connection = newH2Connection("simple_address_merge")) {
            MetadataTestFixtures.createSimpleAddressIli2dbFixture(connection);
            ModelMetadata physical = new ch.interlis.generator.reader.Ili2dbMetadataReader(
                connection, null).readMetadata("SimpleAddressModel");
            ModelMetadata semantic = new ch.interlis.generator.reader.Ili2cModelReader(
                new java.io.File("test-models/SimpleAddressModel.ili"), null)
                .readMetadata("SimpleAddressModel");

            MetadataMergeResult result = merger.merge(physical, semantic);

            assertThat(result.metadata()).isNotNull();
            assertThat(result.hasBlockingDiagnostics()).isFalse();
            assertThat(result.metadata().getClasses()).isNotEmpty();

            ClassMetadata personAddress = result.metadata()
                .getClass("SimpleAddressModel.Addresses.PersonAddress");
            assertThat(personAddress).isNotNull();
            assertThat(personAddress.getAttribute("person").getColumnName())
                .isEqualTo("person_id");
            assertThat(personAddress.getAttribute("address").getColumnName())
                .isEqualTo("address_id");

            List<RelationshipMetadata> merged = result.metadata().getAllRelationships().stream()
                .filter(relationship -> "ili2db+ili2c".equals(relationship.getSource()))
                .toList();
            assertThat(merged).hasSize(2);
            assertThat(merged)
                .allSatisfy(relationship -> {
                    assertThat(relationship.getSemanticKind())
                        .isEqualTo(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
                    assertThat(relationship.getMergeConfidence())
                        .isEqualTo(RelationshipMetadata.MergeConfidence.MEDIUM);
                    assertThat(relationship.getMergeReason())
                        .isEqualTo(RelationshipMetadata.MergeReason.NORMALIZED_TOKEN);
                });
            assertThat(merged.stream().map(RelationshipMetadata::getMergeToken).toList())
                .containsExactlyInAnyOrder("person", "address");

            assertThat(result.metadata().getAssociation("SimpleAddressModel.Addresses.PersonAddress"))
                .satisfies(association -> {
                    assertThat(association.getRoles())
                        .extracting(role -> role.getName())
                        .containsExactly("Address", "Person");
                    assertThat(association.getPhysicalTable()).isEqualTo("personaddress");
                });
        }
    }

    @Test
    void coreIrTestModelMergesExactSourceAttribute() throws Exception {
        try (var connection = newH2Connection("core_ir_merge")) {
            MetadataTestFixtures.createCoreIrReferenceIli2dbFixture(connection);
            ModelMetadata physical = new ch.interlis.generator.reader.Ili2dbMetadataReader(
                connection, null).readMetadata("CoreIrTestModel");
            ModelMetadata semantic = new ch.interlis.generator.reader.Ili2cModelReader(
                new java.io.File("test-models/CoreIrTestModel.ili"), null)
                .readMetadata("CoreIrTestModel");

            MetadataMergeResult result = merger.merge(physical, semantic);

            assertThat(result.hasBlockingDiagnostics()).isFalse();
            assertThat(result.metadata().getAllRelationships())
                .filteredOn(relationship -> "CoreIrTestModel.Relations.Component"
                    .equals(relationship.getSourceClass()))
                .filteredOn(relationship -> "CoreIrTestModel.Relations.Parent"
                    .equals(relationship.getTargetClass()))
                .singleElement()
                .satisfies(relationship -> {
                    assertThat(relationship.getSource()).isEqualTo("ili2db+ili2c");
                    assertThat(relationship.getSemanticKind())
                        .isEqualTo(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE);
                    assertThat(relationship.getSourceAttribute()).isEqualTo("ParentRef");
                    assertThat(relationship.getPhysicalName()).isEqualTo("ParentRef");
                    assertThat(relationship.getSemanticName())
                        .isEqualTo("CoreIrTestModel.Relations.Component.ParentRef");
                    assertThat(relationship.getMergeReason())
                        .isEqualTo(RelationshipMetadata.MergeReason.EXACT_SOURCE_ATTRIBUTE);
                    assertThat(relationship.getMergeConfidence())
                        .isEqualTo(RelationshipMetadata.MergeConfidence.EXACT);
                    assertThat(relationship.getMergeToken()).isEqualTo("ParentRef");
                });
        }
    }

    @Test
    void associationCasesMergePreservesPhysicalColumnsAndSemanticCardinalities()
        throws Exception {
        try (var connection = newH2Connection("association_cases_merge")) {
            MetadataTestFixtures.createAssociationCasesIli2dbFixture(connection);
            ModelMetadata physical = new ch.interlis.generator.reader.Ili2dbMetadataReader(
                connection, null).readMetadata("AssociationCases");
            ModelMetadata semantic = new ch.interlis.generator.reader.Ili2cModelReader(
                new java.io.File("test-models/AssociationCases.ili"), null)
                .readMetadata("AssociationCases");

            MetadataMergeResult result = merger.merge(physical, semantic);

            assertThat(result.hasBlockingDiagnostics()).isFalse();

            AssociationMetadata sameTarget = result.metadata().getAssociation(
                "AssociationCases.Base.SameTargetAssociation");
            assertThat(sameTarget.getRoles())
                .extracting(role -> role.getName())
                .containsExactlyInAnyOrder("PrimaryPerson", "SecondaryPerson");

            AssociationMetadata physicalMismatch = result.metadata().getAssociation(
                "AssociationCases.Base.PhysicalMismatchAssociation");
            assertThat(physicalMismatch.getRoles())
                .filteredOn(role -> role.getName().equals("SemanticOwner"))
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.getSourceAttribute()).isEqualTo("owner_fk");
                    assertThat(role.getMergeReason())
                        .isEqualTo(RelationshipMetadata.MergeReason.EXACT_TARGET_ROLE);
                });
        }
    }

    @Test
    void inputPermutationDoesNotChangeOutput() throws Exception {
        MergeTokenNormalizer normalizer = new MergeTokenNormalizer();
        MetadataMerger forwardMerger = new MetadataMerger(
            new AttributeMatcher(normalizer),
            new RelationshipMatcher(normalizer),
            new MetadataPostProcessor(),
            new MetadataValidator()
        );
        MetadataMerger reversedMerger = new MetadataMerger(
            new AttributeMatcher(new MergeTokenNormalizer()),
            new RelationshipMatcher(new MergeTokenNormalizer()),
            new MetadataPostProcessor(),
            new MetadataValidator()
        );

        try (var connection = newH2Connection("simple_address_permutation")) {
            MetadataTestFixtures.createSimpleAddressIli2dbFixture(connection);
            ModelMetadata physical = new ch.interlis.generator.reader.Ili2dbMetadataReader(
                connection, null).readMetadata("SimpleAddressModel");
            ModelMetadata semantic = new ch.interlis.generator.reader.Ili2cModelReader(
                new java.io.File("test-models/SimpleAddressModel.ili"), null)
                .readMetadata("SimpleAddressModel");

            MetadataMergeResult forward = forwardMerger.merge(physical, semantic);
            MetadataMergeResult reversed = reversedMerger.merge(physical, semantic);

            assertThat(forward.diagnostics()).hasSameSizeAs(reversed.diagnostics());
            assertThat(forward.diagnostics())
                .containsExactlyElementsOf(reversed.diagnostics());
        }
    }

    @Test
    void physicalColumnsSurviveTheMerge() throws Exception {
        try (var connection = newH2Connection("simple_address_columns")) {
            MetadataTestFixtures.createSimpleAddressIli2dbFixture(connection);
            ModelMetadata physical = new ch.interlis.generator.reader.Ili2dbMetadataReader(
                connection, null).readMetadata("SimpleAddressModel");
            ModelMetadata semantic = new ch.interlis.generator.reader.Ili2cModelReader(
                new java.io.File("test-models/SimpleAddressModel.ili"), null)
                .readMetadata("SimpleAddressModel");

            MetadataMergeResult result = merger.merge(physical, semantic);
            ClassMetadata address = result.metadata()
                .getClass("SimpleAddressModel.Addresses.Address");

            assertThat(address.getAttribute("street").getColumnName()).isEqualTo("astreet");
            assertThat(address.getAttribute("houseNumber").getColumnName())
                .isEqualTo("housenumber");
            assertThat(address.getAttribute("postalCode").getColumnName())
                .isEqualTo("postalcode");
        }
    }

    @Test
    void semanticCardinalitiesSurviveTheMerge() throws Exception {
        try (var connection = newH2Connection("simple_address_cardinality")) {
            MetadataTestFixtures.createSimpleAddressIli2dbFixture(connection);
            ModelMetadata physical = new ch.interlis.generator.reader.Ili2dbMetadataReader(
                connection, null).readMetadata("SimpleAddressModel");
            ModelMetadata semantic = new ch.interlis.generator.reader.Ili2cModelReader(
                new java.io.File("test-models/SimpleAddressModel.ili"), null)
                .readMetadata("SimpleAddressModel");

            MetadataMergeResult result = merger.merge(physical, semantic);
            List<RelationshipMetadata> roles = result.metadata().getAllRelationships().stream()
                .filter(relationship -> relationship.getSemanticKind()
                    == RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
                .toList();

            assertThat(roles)
                .filteredOn(relationship -> "Person".equals(relationship.getTargetRoleName()))
                .singleElement()
                .satisfies(relationship -> {
                    assertThat(relationship.getCardinality().getMinTarget()).isZero();
                    assertThat(relationship.getCardinality().getMaxTarget()).isEqualTo(-1);
                });
            assertThat(roles)
                .filteredOn(relationship -> "Address".equals(relationship.getTargetRoleName()))
                .singleElement()
                .satisfies(relationship -> {
                    assertThat(relationship.getCardinality().getMinTarget()).isZero();
                    assertThat(relationship.getCardinality().getMaxTarget()).isEqualTo(1);
                });
        }
    }

    @Test
    void semanticInputIsNeverMutated() throws Exception {
        try (var connection = newH2Connection("simple_address_no_mutation")) {
            MetadataTestFixtures.createSimpleAddressIli2dbFixture(connection);
            ModelMetadata physical = new ch.interlis.generator.reader.Ili2dbMetadataReader(
                connection, null).readMetadata("SimpleAddressModel");
            ModelMetadata semantic = new ch.interlis.generator.reader.Ili2cModelReader(
                new java.io.File("test-models/SimpleAddressModel.ili"), null)
                .readMetadata("SimpleAddressModel");

            String semanticBefore = semantic.toString();
            int classCountBefore = semantic.getClasses().size();
            int enumCountBefore = semantic.getEnums().size();
            int relationshipCountBefore = semantic.getRelationships().size();

            merger.merge(physical, semantic);
            merger.merge(physical, semantic);

            assertThat(semantic.toString()).isEqualTo(semanticBefore);
            assertThat(semantic.getClasses()).hasSize(classCountBefore);
            assertThat(semantic.getEnums()).hasSize(enumCountBefore);
            assertThat(semantic.getRelationships()).hasSize(relationshipCountBefore);
        }
    }

    @Test
    void physicalInputIsNotReturnedAsResult() throws Exception {
        try (var connection = newH2Connection("simple_address_copy")) {
            MetadataTestFixtures.createSimpleAddressIli2dbFixture(connection);
            ModelMetadata physical = new ch.interlis.generator.reader.Ili2dbMetadataReader(
                connection, null).readMetadata("SimpleAddressModel");
            ModelMetadata semantic = new ch.interlis.generator.reader.Ili2cModelReader(
                new java.io.File("test-models/SimpleAddressModel.ili"), null)
                .readMetadata("SimpleAddressModel");

            MetadataMergeResult result = merger.merge(physical, semantic);

            assertThat(result.metadata()).isNotSameAs(physical);
            assertThat(result.metadata().getModelName()).isEqualTo(physical.getModelName());
            assertThat(physical.getRelationships()).allSatisfy(relationship ->
                assertThat(relationship.getSource()).isEqualTo("ili2db"));
        }
    }

    @Test
    void diagnosticsAreStablySorted() throws Exception {
        try (var connection = newH2Connection("simple_address_diagnostics")) {
            MetadataTestFixtures.createSimpleAddressIli2dbFixture(connection);
            ModelMetadata physical = new ch.interlis.generator.reader.Ili2dbMetadataReader(
                connection, null).readMetadata("SimpleAddressModel");
            ModelMetadata semantic = new ch.interlis.generator.reader.Ili2cModelReader(
                new java.io.File("test-models/SimpleAddressModel.ili"), null)
                .readMetadata("SimpleAddressModel");

            MetadataMergeResult first = merger.merge(physical, semantic);
            MetadataMergeResult second = merger.merge(physical, semantic);

            List<String> firstKeys = first.diagnostics().stream()
                .map(diagnostic -> diagnostic.severity() + "|" + diagnostic.code()
                    + "|" + diagnostic.semanticElement() + "|" + diagnostic.physicalElement())
                .toList();
            List<String> secondKeys = second.diagnostics().stream()
                .map(diagnostic -> diagnostic.severity() + "|" + diagnostic.code()
                    + "|" + diagnostic.semanticElement() + "|" + diagnostic.physicalElement())
                .toList();
            assertThat(firstKeys).isEqualTo(secondKeys);
        }
    }

    @Test
    void enumsAreMergedUniquely() throws Exception {
        try (var connection = newH2Connection("simple_address_enums")) {
            MetadataTestFixtures.createSimpleAddressIli2dbFixture(connection);
            ModelMetadata physical = new ch.interlis.generator.reader.Ili2dbMetadataReader(
                connection, null).readMetadata("SimpleAddressModel");
            ModelMetadata semantic = new ch.interlis.generator.reader.Ili2cModelReader(
                new java.io.File("test-models/SimpleAddressModel.ili"), null)
                .readMetadata("SimpleAddressModel");

            MetadataMergeResult result = merger.merge(physical, semantic);
            EnumMetadata status = result.metadata().getEnums()
                .get("SimpleAddressModel.Addresses.AddressStatus");

            assertThat(status).isNotNull();
            assertThat(status.getValues()).hasSize(3);
            assertThat(result.metadata().getEnums().values().stream()
                .filter(enumMetadata -> enumMetadata.getName()
                    .equals("SimpleAddressModel.Addresses.AddressStatus")))
                .hasSize(1);
        }
    }

    @Test
    void modelNameMismatchIsDiagnosed() {
        ModelMetadata physical = new ModelMetadata("PhysicalModel");
        ModelMetadata semantic = new ModelMetadata("SemanticModel");

        MetadataMergeResult result = merger.merge(physical, semantic);

        assertThat(result.diagnostics())
            .anySatisfy(diagnostic -> {
                assertThat(diagnostic.code()).isEqualTo(MergeDiagnosticCode.MODEL_NAME_MISMATCH);
                assertThat(diagnostic.isBlocking()).isFalse();
            });
    }

    @Test
    void strictModeThrowsOnBlockingDiagnostics() {
        ModelMetadata physical = new ModelMetadata("StrictModel");
        ClassMetadata physicalClass = new ClassMetadata("StrictModel.Topic.Child");
        physicalClass.setTableName("child");
        AttributeMetadata column = new AttributeMetadata("OwnerRef");
        column.setColumnName("owner_id");
        column.setSqlName("owner_id");
        column.setQualifiedName("StrictModel.Topic.Child.OwnerRef");
        physicalClass.addAttribute(column);
        physical.addClass(physicalClass);

        ModelMetadata semantic = new ModelMetadata("StrictModel");
        ClassMetadata semanticClass = new ClassMetadata("StrictModel.Topic.Child");
        AttributeMetadata first = new AttributeMetadata("Owner");
        first.setCoreType(CoreType.REFERENCE);
        AttributeMetadata second = new AttributeMetadata("The_Owner");
        second.setCoreType(CoreType.REFERENCE);
        semanticClass.addAttribute(first);
        semanticClass.addAttribute(second);
        semantic.addClass(semanticClass);

        assertThatThrownBy(() -> merger.mergeStrict(physical, semantic))
            .isInstanceOf(MetadataMergeException.class)
            .satisfies(exception -> {
                MetadataMergeException mergeException = (MetadataMergeException) exception;
                assertThat(mergeException.diagnostics())
                    .anySatisfy(diagnostic -> {
                        assertThat(diagnostic.code())
                            .isEqualTo(MergeDiagnosticCode.ATTRIBUTE_AMBIGUOUS);
                        assertThat(diagnostic.isBlocking()).isTrue();
                    });
            });
    }

    @Test
    void diagnosticModeReturnsInspectableResult() {
        ModelMetadata physical = new ModelMetadata("DiagnosticModel");
        ClassMetadata physicalClass = new ClassMetadata("DiagnosticModel.Topic.Child");
        physicalClass.setTableName("child");
        AttributeMetadata column = new AttributeMetadata("OwnerRef");
        column.setColumnName("owner_id");
        column.setSqlName("owner_id");
        column.setQualifiedName("DiagnosticModel.Topic.Child.OwnerRef");
        physicalClass.addAttribute(column);
        physical.addClass(physicalClass);

        ModelMetadata semantic = new ModelMetadata("DiagnosticModel");
        ClassMetadata semanticClass = new ClassMetadata("DiagnosticModel.Topic.Child");
        AttributeMetadata first = new AttributeMetadata("Owner");
        first.setCoreType(CoreType.REFERENCE);
        AttributeMetadata second = new AttributeMetadata("The_Owner");
        second.setCoreType(CoreType.REFERENCE);
        semanticClass.addAttribute(first);
        semanticClass.addAttribute(second);
        semantic.addClass(semanticClass);

        MetadataMergeResult result = merger.merge(physical, semantic);

        assertThat(result).isNotNull();
        assertThat(result.hasBlockingDiagnostics()).isTrue();
        assertThat(result.blockingDiagnostics())
            .allSatisfy(diagnostic -> assertThat(diagnostic.isBlocking()).isTrue());
    }

    private java.sql.Connection newH2Connection(String name) throws Exception {
        return java.sql.DriverManager.getConnection(
            "jdbc:h2:mem:" + name + "_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    }
}

package ch.interlis.generator.reader;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class Ili2cModelReaderTest {

    @Test
    void readsAssociationReferenceAndCompositionSemantics() throws Exception {
        Ili2cModelReader reader = new Ili2cModelReader(new File("test-models/CoreIrTestModel.ili"));

        ModelMetadata metadata = reader.readMetadata("CoreIrTestModel");

        assertThat(metadata.getModelVersion()).isEqualTo("2026-05-30");
        assertThat(metadata.getAllRelationships())
            .allSatisfy(relationship -> {
                assertThat(relationship.getSource()).isEqualTo("ili2c");
                assertThat(relationship.getSemanticName()).isNotBlank();
                assertThat(relationship.getMergeReason())
                    .isEqualTo(RelationshipMetadata.MergeReason.ILI2C_ONLY);
                assertThat(relationship.getMergeConfidence())
                    .isEqualTo(RelationshipMetadata.MergeConfidence.NONE);
            });

        ClassMetadata child = metadata.getClass("CoreIrTestModel.Relations.Child");
        assertThat(child).isNotNull();
        assertThat(child.getTopicName()).isEqualTo("CoreIrTestModel.Relations");

        AttributeMetadata components = child.getAttribute("Components");
        assertThat(components).isNotNull();
        assertThat(components.isOrdered()).isTrue();
        assertThat(components.getCardinalityMin()).isZero();
        assertThat(components.getCardinalityMax()).isEqualTo(-1);

        assertThat(metadata.getAllRelationships())
            .anySatisfy(relationship -> {
                assertThat(relationship.getSemanticKind())
                    .isEqualTo(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE);
                assertThat(relationship.getSourceClass()).isEqualTo("CoreIrTestModel.Relations.Component");
                assertThat(relationship.getTargetClass()).isEqualTo("CoreIrTestModel.Relations.Parent");
                assertThat(relationship.getTargetRoleName()).isEqualTo("ParentRef");
            })
            .anySatisfy(relationship -> {
                assertThat(relationship.getSemanticKind())
                    .isEqualTo(RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE);
                assertThat(relationship.getTargetClass()).isEqualTo("CoreIrTestModel.Relations.Component");
                assertThat(relationship.isComposition()).isTrue();
                assertThat(relationship.isOrdered()).isTrue();
                assertThat(relationship.getCardinality().getMaxTarget()).isEqualTo(-1);
            })
            .anySatisfy(relationship -> {
                assertThat(relationship.getSemanticKind())
                    .isEqualTo(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
                assertThat(relationship.getAssociationName()).isEqualTo("CoreIrTestModel.Relations.ParentChild");
                assertThat(relationship.getTargetRoleName()).isEqualTo("Owner");
                assertThat(relationship.isExternal()).isTrue();
                assertThat(relationship.isComposition()).isTrue();
                assertThat(relationship.getCardinality().getMinTarget()).isEqualTo(1);
            })
            .anySatisfy(relationship -> {
                assertThat(relationship.getSemanticKind())
                    .isEqualTo(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
                assertThat(relationship.getTargetRoleName()).isEqualTo("Children");
                assertThat(relationship.getCardinality().getMaxTarget()).isEqualTo(-1);
            });

        AssociationMetadata association = metadata.getAssociation("CoreIrTestModel.Relations.ParentChild");
        assertThat(association).isNotNull();
        assertThat(association.getAssociationClass()).isEqualTo("CoreIrTestModel.Relations.ParentChild");
        assertThat(association.getRoles())
            .extracting(AssociationRoleMetadata::getName)
            .containsExactlyInAnyOrder("Owner", "Children");
        assertThat(association.getRoles())
            .filteredOn(role -> role.getName().equals("Owner"))
            .singleElement()
            .satisfies(role -> {
                assertThat(role.getTargetClass()).isEqualTo("CoreIrTestModel.Relations.Parent");
                assertThat(role.getOppositeRoleName()).isEqualTo("Children");
                assertThat(role.isExternal()).isTrue();
                assertThat(role.isComposition()).isTrue();
                assertThat(role.getCardinality().getMinTarget()).isEqualTo(1);
            });
        assertThat(association.getAllAttributes())
            .extracting(AttributeMetadata::getName)
            .containsExactly("RoleNote");
    }

    @Test
    void readsCoreTypesFromIli2cModelTypes() throws Exception {
        Ili2cModelReader reader = new Ili2cModelReader(new File("test-models/CoreTypeTestModel.ili"));

        ModelMetadata metadata = reader.readMetadata("CoreTypeTestModel");

        ClassMetadata sample = metadata.getClass("CoreTypeTestModel.Types.Sample");
        assertThat(sample).isNotNull();
        assertThat(sample.getAttribute("PlainText").getCoreType()).isEqualTo(CoreType.TEXT);
        assertThat(sample.getAttribute("MultiLine").getCoreType()).isEqualTo(CoreType.MTEXT);
        assertThat(sample.getAttribute("Amount").getCoreType()).isEqualTo(CoreType.NUMERIC);
        assertThat(sample.getAttribute("Flag").getCoreType()).isEqualTo(CoreType.BOOLEAN);
        assertThat(sample.getAttribute("OnDate").getCoreType()).isEqualTo(CoreType.DATE);
        assertThat(sample.getAttribute("AtDateTime").getCoreType()).isEqualTo(CoreType.DATETIME);
        assertThat(sample.getAttribute("AtTime").getCoreType()).isEqualTo(CoreType.TIME);
        assertThat(sample.getAttribute("Status").getCoreType()).isEqualTo(CoreType.ENUM);
        assertThat(sample.getAttribute("Position").getCoreType()).isEqualTo(CoreType.COORD);
        assertThat(sample.getAttribute("Route").getCoreType()).isEqualTo(CoreType.POLYLINE);
        assertThat(sample.getAttribute("Footprint").getCoreType()).isEqualTo(CoreType.SURFACE);
    }
}

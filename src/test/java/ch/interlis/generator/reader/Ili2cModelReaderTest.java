package ch.interlis.generator.reader;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
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
    }
}

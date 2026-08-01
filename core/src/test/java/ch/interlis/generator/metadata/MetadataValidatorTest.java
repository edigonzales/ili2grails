package ch.interlis.generator.metadata;

import ch.interlis.generator.metadata.merge.MergeDiagnostic;
import ch.interlis.generator.metadata.merge.MergeDiagnosticCode;
import ch.interlis.generator.metadata.merge.MergeSeverity;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.AssociationMetadataBuilder;
import ch.interlis.generator.model.builder.AssociationRoleMetadataBuilder;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.model.builder.RelationshipMetadataBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MetadataValidatorTest {

    private final MetadataValidator validator = new MetadataValidator();

    @Test
    void validMetadataProducesNoDiagnostics() {
        ModelMetadata metadata = minimalValidMetadata();
        assertThat(validator.validate(metadata)).isEmpty();
    }

    @Test
    void classMapKeyMismatchIsDetected() {
        ModelMetadata metadata = rebuildWithClassRenamed(minimalValidMetadata(), "OtherName");
        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("class map key"));
    }

    @Test
    void duplicateColumnNamesAreDetected() {
        ModelMetadata metadata = rebuilt(builder -> {
            ClassMetadataBuilder clazz = builder.requireClassBuilder("TestModel.Topic.Person");
            clazz.requireAttributeBuilder("name").columnName("name_col");
            clazz.attribute(new AttributeMetadataBuilder("name2").columnName("NAME_COL"));
        });
        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("duplicate physical column"));
    }

    @Test
    void unknownRelationshipTargetIsDetected() {
        ModelMetadata metadata = rebuilt(builder ->
            builder.relationshipBuilder("TestModel.Topic.Person_missing")
                .sourceClass("TestModel.Topic.Person")
                .targetClass("TestModel.Topic.Missing")
                .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK)
                .source("ili2db"));
        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("relationship target class not found"));
    }

    @Test
    void externalRelationshipTargetIsAllowed() {
        ModelMetadata metadata = rebuilt(builder ->
            builder.relationshipBuilder("TestModel.Topic.Person_external")
                .sourceClass("TestModel.Topic.Person")
                .targetClass("OtherModel.Topic.External")
                .semanticKind(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE)
                .external(true)
                .source("ili2c"));
        assertThat(validator.validate(metadata))
            .filteredOn(diagnostic -> diagnostic.code()
                .equals(MergeDiagnosticCode.MERGE_INVARIANT_VIOLATION))
            .isEmpty();
    }

    @Test
    void canonicalRelationshipWithoutPhysicalEvidenceIsDetected() {
        ModelMetadata metadata = rebuilt(builder ->
            builder.relationshipBuilder("TestModel.Topic.Person_parent")
                .sourceClass("TestModel.Topic.Person")
                .targetClass("TestModel.Topic.Person")
                .semanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
                .source("ili2db+ili2c"));
        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("without physical evidence"));
    }

    @Test
    void mediumConfidenceWithoutTokenIsDetected() {
        ModelMetadata metadata = rebuilt(builder ->
            builder.relationshipBuilder("TestModel.Topic.Person_parent")
                .sourceClass("TestModel.Topic.Person")
                .targetClass("TestModel.Topic.Person")
                .semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK)
                .source("ili2db+ili2c")
                .sourceAttribute("parent_id")
                .mergeReason(RelationshipMetadata.MergeReason.NORMALIZED_TOKEN)
                .mergeConfidence(RelationshipMetadata.MergeConfidence.MEDIUM));
        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("must carry mergeReason and mergeToken"));
    }

    @Test
    void duplicateAssociationRoleNamesAreDetected() {
        ModelMetadata metadata = rebuilt(builder -> {
            AssociationMetadataBuilder association =
                builder.requireAssociationBuilder("TestModel.Topic.Assoc");
            association.role(new AssociationRoleMetadataBuilder("Person").targetClass("TestModel.Topic.Person"));
            association.role(new AssociationRoleMetadataBuilder("Person").targetClass("TestModel.Topic.Other"));
        });
        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("role"));
    }

    @Test
    void multiplePrimaryKeysAreDetected() {
        ModelMetadata metadata = rebuilt(builder -> {
            ClassMetadataBuilder clazz = builder.requireClassBuilder("TestModel.Topic.Person");
            clazz.attribute(new AttributeMetadataBuilder("pk_one").primaryKey(true));
            clazz.attribute(new AttributeMetadataBuilder("pk_two").primaryKey(true));
        });
        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("more than one primary key"));
    }

    @Test
    void mandatoryRelationshipAgainstNullableColumnIsWarning() {
        ModelMetadata metadata = rebuilt(builder -> {
            ClassMetadataBuilder clazz = builder.requireClassBuilder("TestModel.Topic.Person");
            clazz.attribute(new AttributeMetadataBuilder("parent").columnName("parent_id"));
            builder.relationshipBuilder("TestModel.Topic.Person_parent")
                .sourceClass("TestModel.Topic.Person")
                .targetClass("TestModel.Topic.Person")
                .semanticKind(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE)
                .sourceAttribute("parent_id")
                .source("ili2db+ili2c")
                .mandatory(true);
        });
        assertThat(validator.validate(metadata))
            .anySatisfy(diagnostic -> {
                assertThat(diagnostic.severity()).isEqualTo(MergeSeverity.WARNING);
                assertThat(diagnostic.code())
                    .isEqualTo(MergeDiagnosticCode.MERGE_INVARIANT_VIOLATION);
            });
    }

    private ModelMetadata rebuilt(java.util.function.Consumer<ModelMetadataBuilder> mutator) {
        ModelMetadataBuilder builder = minimalValidBuilder();
        mutator.accept(builder);
        return new ModelMetadataFactory().buildValidated(builder);
    }

    private ModelMetadata rebuildWithClassRenamed(ModelMetadata metadata, String newName) {
        ModelMetadataBuilder builder = metadata.toBuilder();
        ClassMetadataBuilder clazz = builder.requireClassBuilder("TestModel.Topic.Person");
        clazz.name(newName);
        return builder.buildUnchecked();
    }

    private ModelMetadata minimalValidMetadata() {
        return new ModelMetadataFactory().buildValidated(minimalValidBuilder());
    }

    private ModelMetadataBuilder minimalValidBuilder() {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder person = builder.classBuilder("TestModel.Topic.Person");
        person.tableName("person");
        person.attribute(new AttributeMetadataBuilder("name").columnName("name").javaType("String").coreType(ch.interlis.generator.model.CoreType.TEXT));
        person.attribute(new AttributeMetadataBuilder("t_id").columnName("t_id").primaryKey(true).javaType("Long").coreType(ch.interlis.generator.model.CoreType.NUMERIC));
        builder.associationBuilder("TestModel.Topic.Assoc")
            .associationClass("TestModel.Topic.Assoc");
        return builder;
    }

    private java.util.function.Consumer<MergeDiagnostic> invariant(String fragment) {
        return diagnostic -> {
            assertThat(diagnostic.code())
                .isEqualTo(MergeDiagnosticCode.MERGE_INVARIANT_VIOLATION);
            assertThat(diagnostic.message()).contains(fragment);
        };
    }
}

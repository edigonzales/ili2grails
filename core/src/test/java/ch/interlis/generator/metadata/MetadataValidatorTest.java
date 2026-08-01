package ch.interlis.generator.metadata;

import ch.interlis.generator.metadata.merge.MergeDiagnostic;
import ch.interlis.generator.metadata.merge.MergeDiagnosticCode;
import ch.interlis.generator.metadata.merge.MergeSeverity;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        ModelMetadata metadata = minimalValidMetadata();
        ClassMetadata clazz = metadata.getAllClasses().iterator().next();
        metadata.setClasses(new java.util.LinkedHashMap<>());
        metadata.addClass(clazz);
        clazz.setName("OtherName");
        metadata.getClasses().put(clazz.getName(), clazz);

        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("class map key must equal class name"));
    }

    @Test
    void duplicateColumnNamesAreDetected() {
        ModelMetadata metadata = minimalValidMetadata();
        ClassMetadata clazz = metadata.getClass("TestModel.Topic.Person");
        AttributeMetadata first = new AttributeMetadata("name");
        first.setColumnName("name_col");
        AttributeMetadata second = new AttributeMetadata("name2");
        second.setColumnName("NAME_COL");
        clazz.addAttribute(first);
        clazz.addAttribute(second);

        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("duplicate physical column"));
    }

    @Test
    void unknownRelationshipTargetIsDetected() {
        ModelMetadata metadata = minimalValidMetadata();
        RelationshipMetadata relationship = new RelationshipMetadata("TestModel.Topic.Person_missing");
        relationship.setSourceClass("TestModel.Topic.Person");
        relationship.setTargetClass("TestModel.Topic.Missing");
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        relationship.setSource("ili2db");
        metadata.addRelationship(relationship);

        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("relationship target class not found"));
    }

    @Test
    void externalRelationshipTargetIsAllowed() {
        ModelMetadata metadata = minimalValidMetadata();
        RelationshipMetadata relationship = new RelationshipMetadata("TestModel.Topic.Person_external");
        relationship.setSourceClass("TestModel.Topic.Person");
        relationship.setTargetClass("OtherModel.Topic.External");
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE);
        relationship.setExternal(true);
        relationship.setSource("ili2c");
        metadata.addRelationship(relationship);

        assertThat(validator.validate(metadata))
            .filteredOn(diagnostic -> diagnostic.code()
                .equals(MergeDiagnosticCode.MERGE_INVARIANT_VIOLATION))
            .isEmpty();
    }

    @Test
    void canonicalRelationshipWithoutPhysicalEvidenceIsDetected() {
        ModelMetadata metadata = minimalValidMetadata();
        RelationshipMetadata relationship = new RelationshipMetadata("TestModel.Topic.Person_parent");
        relationship.setSourceClass("TestModel.Topic.Person");
        relationship.setTargetClass("TestModel.Topic.Person");
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
        relationship.setSource("ili2db+ili2c");
        metadata.addRelationship(relationship);

        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("without physical evidence"));
    }

    @Test
    void mediumConfidenceWithoutTokenIsDetected() {
        ModelMetadata metadata = minimalValidMetadata();
        RelationshipMetadata relationship = new RelationshipMetadata("TestModel.Topic.Person_parent");
        relationship.setSourceClass("TestModel.Topic.Person");
        relationship.setTargetClass("TestModel.Topic.Person");
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        relationship.setSource("ili2db+ili2c");
        relationship.setSourceAttribute("parent_id");
        relationship.setMergeReason(RelationshipMetadata.MergeReason.NORMALIZED_TOKEN);
        relationship.setMergeConfidence(RelationshipMetadata.MergeConfidence.MEDIUM);
        metadata.addRelationship(relationship);

        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("must carry mergeReason and mergeToken"));
    }

    @Test
    void duplicateAssociationRoleNamesAreDetected() {
        ModelMetadata metadata = minimalValidMetadata();
        AssociationMetadata association = metadata.getAssociation("TestModel.Topic.Assoc");
        AssociationRoleMetadata first = new AssociationRoleMetadata("Person");
        first.setTargetClass("TestModel.Topic.Person");
        AssociationRoleMetadata second = new AssociationRoleMetadata("Person");
        second.setTargetClass("TestModel.Topic.Other");
        association.addRole(first);
        association.addRole(second);

        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("duplicate association role name"));
    }

    @Test
    void multiplePrimaryKeysAreDetected() {
        ModelMetadata metadata = minimalValidMetadata();
        ClassMetadata clazz = metadata.getClass("TestModel.Topic.Person");
        AttributeMetadata first = new AttributeMetadata("pk_one");
        first.setPrimaryKey(true);
        AttributeMetadata second = new AttributeMetadata("pk_two");
        second.setPrimaryKey(true);
        clazz.addAttribute(first);
        clazz.addAttribute(second);

        assertThat(validator.validate(metadata))
            .anySatisfy(invariant("more than one primary key"));
    }

    @Test
    void mandatoryRelationshipAgainstNullableColumnIsWarning() {
        ModelMetadata metadata = minimalValidMetadata();
        ClassMetadata clazz = metadata.getClass("TestModel.Topic.Person");
        AttributeMetadata parent = new AttributeMetadata("parent");
        parent.setColumnName("parent_id");
        clazz.addAttribute(parent);

        RelationshipMetadata relationship = new RelationshipMetadata("TestModel.Topic.Person_parent");
        relationship.setSourceClass("TestModel.Topic.Person");
        relationship.setTargetClass("TestModel.Topic.Person");
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE);
        relationship.setSourceAttribute("parent_id");
        relationship.setSource("ili2db+ili2c");
        relationship.setMandatory(true);
        metadata.addRelationship(relationship);

        assertThat(validator.validate(metadata))
            .anySatisfy(diagnostic -> {
                assertThat(diagnostic.severity()).isEqualTo(MergeSeverity.WARNING);
                assertThat(diagnostic.code())
                    .isEqualTo(MergeDiagnosticCode.MERGE_INVARIANT_VIOLATION);
            });
    }

    private ModelMetadata minimalValidMetadata() {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata person = new ClassMetadata("TestModel.Topic.Person");
        person.setTableName("person");
        AttributeMetadata name = new AttributeMetadata("name");
        name.setColumnName("name");
        AttributeMetadata tId = new AttributeMetadata("t_id");
        tId.setColumnName("t_id");
        tId.setPrimaryKey(true);
        person.addAttribute(name);
        person.addAttribute(tId);
        metadata.addClass(person);

        AssociationMetadata association = new AssociationMetadata("TestModel.Topic.Assoc");
        association.setAssociationClass("TestModel.Topic.Assoc");
        metadata.addAssociation(association);
        return metadata;
    }

    private java.util.function.Consumer<MergeDiagnostic> invariant(String fragment) {
        return diagnostic -> {
            assertThat(diagnostic.code())
                .isEqualTo(MergeDiagnosticCode.MERGE_INVARIANT_VIOLATION);
            assertThat(diagnostic.message()).contains(fragment);
        };
    }
}

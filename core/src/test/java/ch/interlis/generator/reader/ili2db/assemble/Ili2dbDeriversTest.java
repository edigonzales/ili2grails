package ch.interlis.generator.reader.ili2db.assemble;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.AssociationMetadataBuilder;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.model.builder.RelationshipMetadataBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * Deriver-Schicht: reine IR-Transformationen auf dem Builder.
 */
class Ili2dbDeriversTest {

    @Test
    void derivesManyToOneRelationshipFromForeignKeyAttribute() {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("DeriveModel");
        ClassMetadataBuilder sample = builder.classBuilder("DeriveModel.Topic.Sample");
        sample.kind(ClassMetadata.ClassKind.CLASS);
        AttributeMetadataBuilder owner = new AttributeMetadataBuilder("owner");
        owner.columnName("owner_fk");
        owner.sqlName("owner_fk");
        owner.foreignKey(true);
        owner.referencedClass("DeriveModel.Topic.Sample");
        owner.mandatory(true);
        sample.attribute(owner);

        new Ili2dbRelationshipDeriver().derive(builder);

        assertThat(builder.relationshipBuilders()).hasSize(1);
        RelationshipMetadataBuilder rel = builder.relationshipBuilders().get(0);
        assertThat(rel.sourceClass()).isEqualTo("DeriveModel.Topic.Sample");
        assertThat(rel.targetClass()).isEqualTo("DeriveModel.Topic.Sample");
        assertThat(rel.sourceAttribute()).isEqualTo("owner_fk");
        assertThat(rel.targetAttribute()).isEqualTo("T_Id");
        assertThat(rel.type()).isEqualTo(RelationshipMetadata.RelationType.MANY_TO_ONE);
        assertThat(rel.semanticKind()).isEqualTo(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        assertThat(rel.physicalName()).isEqualTo("owner_fk");
        assertThat(rel.targetRoleName()).isEqualTo("owner");
        assertThat(rel.mandatory()).isTrue();
    }

    @Test
    void derivesAssociationWithRolesAndExcludesRoleAttributes() {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("DeriveModel");
        ClassMetadataBuilder link = builder.classBuilder("DeriveModel.Topic.Link");
        link.kind(ClassMetadata.ClassKind.ASSOCIATION);
        AttributeMetadataBuilder fromRole = new AttributeMetadataBuilder("from");
        fromRole.columnName("from_fk");
        fromRole.foreignKey(true);
        fromRole.referencedClass("DeriveModel.Topic.A");
        link.attribute(fromRole);
        AttributeMetadataBuilder toRole = new AttributeMetadataBuilder("to");
        toRole.columnName("to_fk");
        toRole.foreignKey(true);
        toRole.referencedClass("DeriveModel.Topic.B");
        link.attribute(toRole);
        AttributeMetadataBuilder label = new AttributeMetadataBuilder("label");
        label.columnName("label");
        link.attribute(label);

        new Ili2dbRelationshipDeriver().derive(builder);
        new Ili2dbAssociationDeriver().derive(builder, new ArrayList<>());

        assertThat(builder.relationshipBuilders()).hasSize(2);
        assertThat(builder.associationBuilders()).hasSize(1);
        AssociationMetadataBuilder association =
            builder.requireAssociationBuilder("DeriveModel.Topic.Link");
        assertThat(association.associationClass()).isEqualTo("DeriveModel.Topic.Link");
        assertThat(association.roleBuilders()).extracting(role -> role.name())
            .containsExactlyInAnyOrder("from", "to");
        assertThat(association.attributeBuilders().values()).extracting(attr -> attr.name())
            .containsExactly("label");
    }
}

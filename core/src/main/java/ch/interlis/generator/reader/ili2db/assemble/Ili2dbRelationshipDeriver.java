package ch.interlis.generator.reader.ili2db.assemble;

import ch.interlis.generator.model.Cardinality;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.model.builder.RelationshipMetadataBuilder;

/**
 * Leitet {@code MANY_TO_ONE}-Beziehungen aus FK-Attributen ab. Reine
 * IR-Transformation auf dem Builder; keine SQL-Zugriffe.
 */
public final class Ili2dbRelationshipDeriver {

    public static final String ILI2DB_STANDARD_TARGET_ATTRIBUTE = "T_Id";

    public void derive(ModelMetadataBuilder builder) {
        for (ClassMetadataBuilder classMetadata : builder.classBuilders().values()) {
            for (AttributeMetadataBuilder attr : classMetadata.attributeBuilders().values()) {
                if (attr.foreignKey() && attr.referencedClass() != null) {
                    RelationshipMetadataBuilder rel =
                        builder.relationshipBuilder(classMetadata.name() + "_" + attr.name());
                    rel.sourceClass(classMetadata.name());
                    rel.targetClass(attr.referencedClass());
                    String sourceAttribute = attr.sqlName() != null ? attr.sqlName() : attr.name();
                    rel.sourceAttribute(sourceAttribute);
                    rel.targetAttribute(ILI2DB_STANDARD_TARGET_ATTRIBUTE);
                    rel.type(RelationshipMetadata.RelationType.MANY_TO_ONE);
                    rel.semanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
                    rel.source("ili2db");
                    rel.physicalName(sourceAttribute);
                    rel.mergeReason(RelationshipMetadata.MergeReason.ILI2DB_ONLY);
                    rel.mergeConfidence(RelationshipMetadata.MergeConfidence.NONE);
                    rel.targetRoleName(attr.name());
                    rel.cardinality(Cardinality.of(1, 1, attr.mandatory() ? 1 : 0, 1));
                    rel.mandatory(attr.mandatory());
                }
            }
        }
    }
}

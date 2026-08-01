package ch.interlis.generator.metadata;

import ch.interlis.generator.metadata.merge.MergeDiagnostic;
import ch.interlis.generator.metadata.merge.MergeDiagnosticCode;
import ch.interlis.generator.metadata.merge.MergeSeverity;
import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validator für Core-IR-Invarianten. Lauf als Blocking Gate innerhalb des
 * Metadaten-Merges; Verletzungen werden als {@link MergeDiagnostic} mit Code
 * {@link MergeDiagnosticCode#MERGE_INVARIANT_VIOLATION} gemeldet.
 */
public final class MetadataValidator {

    public List<MergeDiagnostic> validate(ModelMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        List<MergeDiagnostic> diagnostics = new ArrayList<>();

        validateClassKeys(metadata, diagnostics);
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            validateClass(classMetadata, diagnostics);
        }
        validateRelationships(metadata, diagnostics);
        validateAssociations(metadata, diagnostics);
        validateCardinalityAgainstNullability(metadata, diagnostics);
        return diagnostics;
    }

    private void validateClassKeys(ModelMetadata metadata, List<MergeDiagnostic> diagnostics) {
        for (Map.Entry<String, ClassMetadata> entry : metadata.getClasses().entrySet()) {
            if (!Objects.equals(entry.getKey(), entry.getValue().getName())) {
                diagnostics.add(invariant(entry.getValue().getName(), null,
                    "class map key must equal class name"));
            }
        }
    }

    private void validateClass(ClassMetadata classMetadata, List<MergeDiagnostic> diagnostics) {
        Set<String> attributeNames = new HashSet<>();
        Set<String> columnNames = new HashSet<>();
        int primaryKeyCount = 0;
        for (AttributeMetadata attribute : classMetadata.getAllAttributes()) {
            if (attribute.getName() != null && !attributeNames.add(attribute.getName())) {
                diagnostics.add(invariant(classMetadata.getName(), attribute.getName(),
                    "duplicate attribute name in class"));
            }
            if (attribute.getColumnName() != null) {
                String lowerColumn = attribute.getColumnName().toLowerCase(Locale.ROOT);
                if (!columnNames.add(lowerColumn)) {
                    diagnostics.add(invariant(classMetadata.getName(), attribute.getName(),
                        "duplicate physical column '" + attribute.getColumnName()
                            + "' within class (case-insensitive)"));
                }
            }
            if (attribute.isPrimaryKey()) {
                primaryKeyCount++;
            }
        }
        if (primaryKeyCount > 1) {
            diagnostics.add(invariant(classMetadata.getName(), null,
                "more than one primary key attribute in class"));
        }
    }

    private void validateRelationships(ModelMetadata metadata, List<MergeDiagnostic> diagnostics) {
        List<RelationshipMetadata> relationships = metadata.getAllRelationships();
        for (int i = 0; i < relationships.size(); i++) {
            RelationshipMetadata relationship = relationships.get(i);
            if (!relationship.isExternal()) {
                if (!isKnownClass(metadata, relationship.getSourceClass())) {
                    diagnostics.add(invariant(relationship.getName(), null,
                        "relationship source class not found: " + relationship.getSourceClass()));
                }
                if (!isKnownClass(metadata, relationship.getTargetClass())) {
                    diagnostics.add(invariant(relationship.getName(), null,
                        "relationship target class not found: " + relationship.getTargetClass()));
                }
            }
            if (isCanonical(relationship)) {
                boolean hasPhysicalEvidence =
                    notBlank(relationship.getSourceAttribute())
                        || notBlank(relationship.getPhysicalName());
                if (!hasPhysicalEvidence) {
                    diagnostics.add(invariant(relationship.getName(), null,
                        "canonical relationship without physical evidence"));
                }
            }
            RelationshipMetadata.MergeConfidence confidence = relationship.getMergeConfidence();
            if (confidence == RelationshipMetadata.MergeConfidence.EXACT
                || confidence == RelationshipMetadata.MergeConfidence.MEDIUM) {
                if (relationship.getMergeReason() == null || relationship.getMergeToken() == null) {
                    diagnostics.add(invariant(relationship.getName(), null,
                        "merged relationship with confidence " + confidence
                            + " must carry mergeReason and mergeToken"));
                }
            }
            for (int j = i + 1; j < relationships.size(); j++) {
                if (sameRelationship(relationships.get(i), relationships.get(j))) {
                    diagnostics.add(invariant(relationship.getName(), null,
                        "duplicate canonical relationship"));
                }
            }
        }
    }

    private void validateAssociations(ModelMetadata metadata, List<MergeDiagnostic> diagnostics) {
        for (AssociationMetadata association : metadata.getAllAssociations()) {
            Set<String> roleNames = new HashSet<>();
            for (AssociationRoleMetadata role : association.getRoles()) {
                if (role.getName() != null && !roleNames.add(role.getName())) {
                    diagnostics.add(invariant(association.getName(), role.getName(),
                        "duplicate association role name"));
                }
                if (!role.isExternal() && !isKnownClass(metadata, role.getTargetClass())) {
                    diagnostics.add(invariant(association.getName(), role.getName(),
                        "association role target class not found: " + role.getTargetClass()));
                }
            }
        }
    }

    private void validateCardinalityAgainstNullability(ModelMetadata metadata,
                                                       List<MergeDiagnostic> diagnostics) {
        for (RelationshipMetadata relationship : metadata.getAllRelationships()) {
            if (!isCanonical(relationship) || !relationship.isMandatory()) {
                continue;
            }
            AttributeMetadata attribute = findPhysicalAttribute(metadata, relationship);
            if (attribute != null && !attribute.isMandatory()) {
                diagnostics.add(new MergeDiagnostic(
                    MergeSeverity.WARNING,
                    MergeDiagnosticCode.MERGE_INVARIANT_VIOLATION,
                    "merged relationship is mandatory but the physical column is nullable",
                    relationship.getName(),
                    attribute.getName(),
                    Map.of("invariant", "cardinality-vs-nullability")
                ));
            }
        }
    }

    private AttributeMetadata findPhysicalAttribute(ModelMetadata metadata,
                                                    RelationshipMetadata relationship) {
        ClassMetadata sourceClass = metadata.getClass(relationship.getSourceClass());
        if (sourceClass == null) {
            return null;
        }
        String attributeName = firstNonNull(relationship.getSourceAttribute(),
            relationship.getPhysicalName());
        if (attributeName == null) {
            return null;
        }
        AttributeMetadata direct = sourceClass.getAttribute(attributeName);
        if (direct != null) {
            return direct;
        }
        for (AttributeMetadata attribute : sourceClass.getAllAttributes()) {
            if (attributeName.equalsIgnoreCase(attribute.getName())
                || attributeName.equalsIgnoreCase(attribute.getColumnName())
                || attributeName.equalsIgnoreCase(attribute.getSqlName())) {
                return attribute;
            }
        }
        return null;
    }

    private boolean isKnownClass(ModelMetadata metadata, String className) {
        return className != null && metadata.getClass(className) != null;
    }

    private boolean isCanonical(RelationshipMetadata relationship) {
        return "ili2db+ili2c".equals(relationship.getSource());
    }

    private boolean sameRelationship(RelationshipMetadata left, RelationshipMetadata right) {
        return Objects.equals(left.getName(), right.getName())
            && Objects.equals(left.getSourceClass(), right.getSourceClass())
            && Objects.equals(left.getTargetClass(), right.getTargetClass())
            && Objects.equals(left.getSourceAttribute(), right.getSourceAttribute())
            && Objects.equals(left.getTargetRoleName(), right.getTargetRoleName())
            && Objects.equals(left.getSemanticKind(), right.getSemanticKind());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private MergeDiagnostic invariant(String semanticElement,
                                      String physicalElement,
                                      String message) {
        return new MergeDiagnostic(
            MergeSeverity.ERROR,
            MergeDiagnosticCode.MERGE_INVARIANT_VIOLATION,
            message,
            semanticElement,
            physicalElement,
            Map.of("invariant", message)
        );
    }
}

package ch.interlis.generator.metadata.merge;

import ch.interlis.generator.metadata.MetadataPostProcessor;
import ch.interlis.generator.metadata.MetadataValidator;
import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministischer Merger für physische (ili2db) und semantische (ili2c) Metadaten.
 *
 * <p>Es gibt kein first-match-wins: Matchphasen sind explizit, Mehrdeutigkeiten
 * erzeugen strukturierte Diagnostics, und bei Ambiguität wird kein Kandidat
 * ausgewählt. Ein physisches Element wird höchstens einmal konsumiert. Der
 * semantische Input wird niemals mutiert; das Resultat basiert auf einer
 * tiefen Kopie des physischen Inputs.</p>
 */
public final class MetadataMerger {

    private final AttributeMatcher attributeMatcher;
    private final RelationshipMatcher relationshipMatcher;
    private final MetadataPostProcessor postProcessor;
    private final MetadataValidator validator;

    public MetadataMerger(AttributeMatcher attributeMatcher,
                          RelationshipMatcher relationshipMatcher,
                          MetadataPostProcessor postProcessor,
                          MetadataValidator validator) {
        this.attributeMatcher = Objects.requireNonNull(attributeMatcher, "attributeMatcher");
        this.relationshipMatcher = Objects.requireNonNull(relationshipMatcher, "relationshipMatcher");
        this.postProcessor = Objects.requireNonNull(postProcessor, "postProcessor");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public static MetadataMerger defaultMerger() {
        MergeTokenNormalizer normalizer = new MergeTokenNormalizer();
        return new MetadataMerger(
            new AttributeMatcher(normalizer),
            new RelationshipMatcher(normalizer),
            new MetadataPostProcessor(),
            new MetadataValidator()
        );
    }

    /**
     * Führt den Merge vollständig aus und liefert das inspizierbare Resultat.
     * Die Policy (STRICT/DIAGNOSTIC) wendet der Caller an.
     */
    public MetadataMergeResult merge(ModelMetadata physical, ModelMetadata semantic) {
        Objects.requireNonNull(physical, "physical");
        Objects.requireNonNull(semantic, "semantic");

        List<MergeDiagnostic> diagnostics = new ArrayList<>();

        if (!Objects.equals(physical.getModelName(), semantic.getModelName())) {
            diagnostics.add(new MergeDiagnostic(
                MergeSeverity.WARNING,
                MergeDiagnosticCode.MODEL_NAME_MISMATCH,
                "physical and semantic model names differ",
                semantic.getModelName(),
                physical.getModelName(),
                Map.of()
            ));
        }

        ModelMetadata target = ModelMetadataCopier.copy(physical);

        if (semantic.getIliVersion() != null) {
            target.setIliVersion(semantic.getIliVersion());
        }
        if (semantic.getModelVersion() != null) {
            target.setModelVersion(semantic.getModelVersion());
        }

        mergeClasses(target, semantic, diagnostics);
        mergeEnums(target, semantic);
        mergeRelationships(target, semantic, diagnostics);
        mergeAssociations(target, semantic);

        postProcessor.process(target);

        List<MergeDiagnostic> validatorDiagnostics = validator.validate(target);
        diagnostics.addAll(validatorDiagnostics);

        List<MergeDiagnostic> sorted = diagnostics.stream()
            .sorted(Comparator
                .comparing(MergeDiagnostic::severity)
                .thenComparing(MergeDiagnostic::code)
                .thenComparing(diagnostic -> nullToEmpty(diagnostic.semanticElement()))
                .thenComparing(diagnostic -> nullToEmpty(diagnostic.physicalElement())))
            .toList();

        return new MetadataMergeResult(target, sorted);
    }

    /**
     * STRICT-Variante: wirft bei blockierenden Diagnostics nach vollständiger
     * Auswertung eine {@link MetadataMergeException}.
     */
    public ModelMetadata mergeStrict(ModelMetadata physical, ModelMetadata semantic) {
        MetadataMergeResult result = merge(physical, semantic);
        result.throwIfBlocking();
        return result.metadata();
    }

    private void mergeClasses(ModelMetadata target,
                              ModelMetadata semantic,
                              List<MergeDiagnostic> diagnostics) {
        for (ClassMetadata semanticClass : sortedSemanticClasses(semantic)) {
            ClassMetadata targetClass = target.getClass(semanticClass.getName());
            if (targetClass == null) {
                ClassMetadata copy = ModelMetadataCopier.copyClass(semanticClass);
                target.addClass(copy);
                for (RelationshipMetadata relationship : semanticClass.getRelationships()) {
                    copy.addRelationship(ModelMetadataCopier.copyRelationship(relationship));
                }
                MergeSeverity severity = semanticClass.isAbstract()
                    ? MergeSeverity.INFO
                    : MergeSeverity.WARNING;
                diagnostics.add(new MergeDiagnostic(
                    severity,
                    MergeDiagnosticCode.CLASS_ONLY_IN_SEMANTIC,
                    "class exists only in the semantic model",
                    semanticClass.getName(),
                    null,
                    Map.of()
                ));
                continue;
            }
            enrichClass(targetClass, semanticClass);
            mergeAttributes(targetClass, semanticClass, diagnostics);
        }

        Set<String> semanticClassNames = new LinkedHashSet<>();
        for (ClassMetadata semanticClass : semantic.getAllClasses()) {
            semanticClassNames.add(semanticClass.getName());
        }
        for (ClassMetadata targetClass : target.getAllClasses()) {
            if (!semanticClassNames.contains(targetClass.getName())) {
                diagnostics.add(new MergeDiagnostic(
                    MergeSeverity.INFO,
                    MergeDiagnosticCode.CLASS_ONLY_IN_PHYSICAL,
                    "class exists only in the physical model",
                    null,
                    targetClass.getName(),
                    Map.of()
                ));
            }
        }
    }

    private List<ClassMetadata> sortedSemanticClasses(ModelMetadata semantic) {
        return semantic.getAllClasses().stream()
            .sorted(Comparator.comparing(ClassMetadata::getName,
                Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    private void enrichClass(ClassMetadata target, ClassMetadata semantic) {
        if (semantic.getDocumentation() != null) {
            target.setDocumentation(semantic.getDocumentation());
        }
        if (semantic.getKind() != null) {
            target.setKind(semantic.getKind());
        }
        if (semantic.getTopicName() != null) {
            target.setTopicName(semantic.getTopicName());
        }
        target.setAbstract(semantic.isAbstract());
        if (semantic.getBaseClass() != null) {
            target.setBaseClass(semantic.getBaseClass());
        }
        target.getLabels().putAll(semantic.getLabels());
    }

    private void mergeAttributes(ClassMetadata targetClass,
                                 ClassMetadata semanticClass,
                                 List<MergeDiagnostic> diagnostics) {
        List<MatchDecision<AttributeMetadata>> decisions =
            attributeMatcher.match(targetClass, semanticClass);

        for (MatchDecision<AttributeMetadata> decision : decisions) {
            switch (decision.status()) {
                case MATCHED -> mergeAttributeIntoClass(targetClass, decision);
                case AMBIGUOUS -> {
                    AttributeMetadata semanticAttribute = decision.semantic();
                    diagnostics.add(new MergeDiagnostic(
                        MergeSeverity.ERROR,
                        MergeDiagnosticCode.ATTRIBUTE_AMBIGUOUS,
                        "attribute match is ambiguous; no candidate selected",
                        semanticAttribute.getName(),
                        null,
                        candidateDetails(decision.candidates())
                    ));
                }
                case PHYSICAL_ALREADY_USED -> {
                    AttributeMetadata semanticAttribute = decision.semantic();
                    diagnostics.add(new MergeDiagnostic(
                        MergeSeverity.WARNING,
                        MergeDiagnosticCode.ATTRIBUTE_PHYSICAL_REUSED,
                        "physical attribute is already consumed by another semantic element",
                        semanticAttribute.getName(),
                        null,
                        candidateDetails(decision.candidates())
                    ));
                }
                case UNMATCHED -> {
                    AttributeMetadata semanticAttribute = decision.semantic();
                    if (isStructuralCollectionAttribute(semanticAttribute)) {
                        continue;
                    }
                    boolean persistent = hasPhysicalMapping(targetClass);
                    if (persistent) {
                        diagnostics.add(new MergeDiagnostic(
                            MergeSeverity.WARNING,
                            MergeDiagnosticCode.ATTRIBUTE_UNMATCHED,
                            "semantic attribute has no physical column in a persistent class",
                            semanticAttribute.getName(),
                            targetClass.getName(),
                            Map.of()
                        ));
                    }
                }
            }
        }
    }

    /**
     * Semantische to-many-Kompositions-Attribute sind strukturell über ihre
     * Relationship repräsentiert und besitzen bewusst keine eigene Spalte.
     */
    private boolean isStructuralCollectionAttribute(AttributeMetadata attribute) {
        return attribute.getCoreType() == CoreType.COMPOSITION;
    }

    private Map<String, String> candidateDetails(List<MatchCandidate<AttributeMetadata>> candidates) {
        Map<String, String> details = new LinkedHashMap<>();
        List<String> physicalNames = new ArrayList<>();
        for (MatchCandidate<AttributeMetadata> candidate : candidates) {
            AttributeMetadata physical = candidate.physical();
            physicalNames.add(physical.getQualifiedName() != null
                ? physical.getQualifiedName()
                : physical.getName());
        }
        details.put("candidateCount", String.valueOf(candidates.size()));
        details.put("physicalCandidates", String.join(", ", physicalNames));
        return details;
    }

    private boolean hasPhysicalMapping(ClassMetadata classMetadata) {
        return (classMetadata.getTableName() != null && !classMetadata.getTableName().isBlank())
            || (classMetadata.getSqlName() != null && !classMetadata.getSqlName().isBlank());
    }

    private void mergeAttributeIntoClass(ClassMetadata targetClass,
                                         MatchDecision<AttributeMetadata> decision) {
        AttributeMetadata merged = mergeAttribute(decision.physical(), decision.semantic());
        AttributeMetadata existing = targetClass.getAttribute(merged.getName());
        if (existing != null) {
            existing.copyFrom(merged);
        } else {
            targetClass.addAttribute(merged);
        }
    }

    private AttributeMetadata mergeAttribute(AttributeMetadata physical,
                                             AttributeMetadata semantic) {
        AttributeMetadata merged = ModelMetadataCopier.copyAttribute(physical);

        if (semantic.getQualifiedName() != null) {
            merged.setQualifiedName(semantic.getQualifiedName());
        }

        if (merged.getReferencedClass() == null && semantic.getReferencedClass() != null) {
            merged.setReferencedClass(semantic.getReferencedClass());
        }

        merged.setMandatory(mergeMandatory(physical, semantic));

        if (semantic.getCoreType() != CoreType.UNKNOWN) {
            merged.setCoreType(semantic.getCoreType());
        }
        if (semantic.getJavaType() != null) {
            merged.setJavaType(semantic.getJavaType());
        }
        if (semantic.getIliType() != null) {
            merged.setIliType(semantic.getIliType());
        }
        if (semantic.getDomainName() != null) {
            merged.setDomainName(semantic.getDomainName());
        }
        if (semantic.getEnumType() != null) {
            merged.setEnumType(semantic.getEnumType());
        } else if (physical.getEnumType() != null) {
            merged.setEnumType(physical.getEnumType());
        }
        Integer narrowerMaxLength = narrowerMaxLength(physical.getMaxLength(), semantic.getMaxLength());
        if (narrowerMaxLength != null) {
            merged.setMaxLength(narrowerMaxLength);
        }
        if (semantic.getMinValue() != null) {
            merged.setMinValue(semantic.getMinValue());
        }
        if (semantic.getMaxValue() != null) {
            merged.setMaxValue(semantic.getMaxValue());
        }
        if (semantic.getPrecision() != null) {
            merged.setPrecision(semantic.getPrecision());
        } else if (physical.getPrecision() != null) {
            merged.setPrecision(physical.getPrecision());
        }
        if (semantic.getScale() != null) {
            merged.setScale(semantic.getScale());
        } else if (physical.getScale() != null) {
            merged.setScale(physical.getScale());
        }
        if (semantic.getCardinalityMin() != null) {
            merged.setCardinalityMin(semantic.getCardinalityMin());
        }
        if (semantic.getCardinalityMax() != null) {
            merged.setCardinalityMax(semantic.getCardinalityMax());
        }
        if (semantic.isOrdered() || physical.isOrdered()) {
            merged.setOrdered(true);
        }
        if (semantic.getUnit() != null) {
            merged.setUnit(semantic.getUnit());
        }
        if (semantic.getDocumentation() != null) {
            merged.setDocumentation(semantic.getDocumentation());
        }
        if (semantic.isGeometry() || physical.isGeometry()) {
            merged.setGeometry(true);
        }
        if (physical.getGeometryKind() != null && !physical.getGeometryKind().isBlank()) {
            merged.setGeometryKind(physical.getGeometryKind());
        } else if (semantic.getGeometryKind() != null) {
            merged.setGeometryKind(semantic.getGeometryKind());
        }
        if (physical.getGeometrySrid() != null) {
            merged.setGeometrySrid(physical.getGeometrySrid());
        }
        if (physical.getGeometryHasZ() != null) {
            merged.setGeometryHasZ(physical.getGeometryHasZ());
        } else if (semantic.getGeometryHasZ() != null) {
            merged.setGeometryHasZ(semantic.getGeometryHasZ());
        }
        if (physical.getGeometryHasM() != null) {
            merged.setGeometryHasM(physical.getGeometryHasM());
        } else if (semantic.getGeometryHasM() != null) {
            merged.setGeometryHasM(semantic.getGeometryHasM());
        }
        if (semantic.getAllowEmptyGeometry() != null) {
            merged.setAllowEmptyGeometry(semantic.getAllowEmptyGeometry());
        } else if (physical.getAllowEmptyGeometry() != null) {
            merged.setAllowEmptyGeometry(physical.getAllowEmptyGeometry());
        }
        mergeLabels(merged.getLabels(), semantic.getLabels());
        return merged;
    }

    private boolean mergeMandatory(AttributeMetadata physical, AttributeMetadata semantic) {
        return physical.isMandatory() || semantic.isMandatory();
    }

    private Integer narrowerMaxLength(Integer physical, Integer semantic) {
        if (physical == null) {
            return semantic;
        }
        if (semantic == null) {
            return physical;
        }
        return Math.min(physical, semantic);
    }

    private void mergeLabels(Map<String, String> target, Map<String, String> semantic) {
        target.putAll(semantic);
    }

    private void mergeEnums(ModelMetadata target, ModelMetadata semantic) {
        for (EnumMetadata enumMetadata : semantic.getAllEnums()) {
            EnumMetadata existing = target.getEnums().get(enumMetadata.getName());
            if (existing == null) {
                target.addEnum(ModelMetadataCopier.copyEnum(enumMetadata));
            }
        }
    }

    private void mergeRelationships(ModelMetadata target,
                                    ModelMetadata semantic,
                                    List<MergeDiagnostic> diagnostics) {
        List<MatchDecision<RelationshipMetadata>> decisions =
            relationshipMatcher.match(target, semantic);

        for (MatchDecision<RelationshipMetadata> decision : decisions) {
            RelationshipMetadata semanticRelationship = decision.semantic();
            switch (decision.status()) {
                case MATCHED -> {
                    RelationshipMetadata physicalRelationship = decision.physical();
                    mergeRelationship(physicalRelationship, semanticRelationship, decision);
                }
                case AMBIGUOUS -> {
                    diagnostics.add(new MergeDiagnostic(
                        MergeSeverity.ERROR,
                        MergeDiagnosticCode.RELATIONSHIP_AMBIGUOUS,
                        "relationship match is ambiguous; no candidate selected",
                        semanticRelationship.getName(),
                        null,
                        relationshipCandidateDetails(decision.candidates())
                    ));
                    target.addRelationship(ModelMetadataCopier.copyRelationship(semanticRelationship));
                }
                case PHYSICAL_ALREADY_USED -> {
                    diagnostics.add(new MergeDiagnostic(
                        MergeSeverity.WARNING,
                        MergeDiagnosticCode.RELATIONSHIP_PHYSICAL_REUSED,
                        "physical relationship is already consumed by another semantic element",
                        semanticRelationship.getName(),
                        null,
                        relationshipCandidateDetails(decision.candidates())
                    ));
                    target.addRelationship(ModelMetadataCopier.copyRelationship(semanticRelationship));
                }
                case UNMATCHED -> {
                    diagnostics.add(new MergeDiagnostic(
                        MergeSeverity.WARNING,
                        MergeDiagnosticCode.RELATIONSHIP_UNMATCHED,
                        "semantic relationship has no physical counterpart",
                        semanticRelationship.getName(),
                        null,
                        Map.of()
                    ));
                    target.addRelationship(ModelMetadataCopier.copyRelationship(semanticRelationship));
                }
            }
        }

        validateAssociationRoles(target, semantic, diagnostics);
    }

    private void validateAssociationRoles(ModelMetadata target,
                                          ModelMetadata semantic,
                                          List<MergeDiagnostic> diagnostics) {
        Map<String, List<RelationshipMetadata>> rolesByAssociation = new LinkedHashMap<>();
        for (RelationshipMetadata relationship : target.getAllRelationships()) {
            if (relationship.getSemanticKind() != RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
                || !"ili2db+ili2c".equals(relationship.getSource())) {
                continue;
            }
            String associationName = postProcessor
                .resolveAssociationName(target, relationship);
            rolesByAssociation.computeIfAbsent(associationName, key -> new ArrayList<>())
                .add(relationship);
        }
        for (Map.Entry<String, List<RelationshipMetadata>> entry : rolesByAssociation.entrySet()) {
            String associationName = entry.getKey();
            if (target.getAssociation(associationName) == null) {
                diagnostics.add(new MergeDiagnostic(
                    MergeSeverity.WARNING,
                    MergeDiagnosticCode.ASSOCIATION_ROLE_UNRESOLVED,
                    "association role cannot be resolved to an association",
                    associationName,
                    null,
                    Map.of()
                ));
            }
            Set<String> roleNames = new LinkedHashSet<>();
            for (RelationshipMetadata relationship : entry.getValue()) {
                String roleName = relationship.getTargetRoleName();
                if (roleName != null && !roleNames.add(roleName)) {
                    diagnostics.add(new MergeDiagnostic(
                        MergeSeverity.ERROR,
                        MergeDiagnosticCode.ASSOCIATION_ROLE_DUPLICATE,
                        "duplicate association role name within association",
                        associationName + "." + roleName,
                        null,
                        Map.of()
                    ));
                }
            }
        }
    }

    private void mergeRelationship(RelationshipMetadata physical,
                                   RelationshipMetadata semantic,
                                   MatchDecision<RelationshipMetadata> decision) {
        if (semantic.getType() != null) {
            physical.setType(semantic.getType());
        }
        if (semantic.getSemanticKind() != null) {
            physical.setSemanticKind(semantic.getSemanticKind());
        }
        if (semantic.getAssociationName() != null) {
            physical.setAssociationName(semantic.getAssociationName());
        }
        if (semantic.getSourceRoleName() != null) {
            physical.setSourceRoleName(semantic.getSourceRoleName());
        }
        if (semantic.getTargetRoleName() != null) {
            physical.setTargetRoleName(semantic.getTargetRoleName());
        }
        if (semantic.getOppositeRoleName() != null) {
            physical.setOppositeRoleName(semantic.getOppositeRoleName());
        }
        if (semantic.getCardinality() != null) {
            physical.setCardinality(semantic.getCardinality());
        }
        physical.setMandatory(semantic.isMandatory());
        physical.setOrdered(semantic.isOrdered());
        physical.setExternal(semantic.isExternal());
        physical.setComposition(semantic.isComposition());
        physical.setSource("ili2db+ili2c");
        if (semantic.getSemanticName() != null) {
            physical.setSemanticName(semantic.getSemanticName());
        } else if (semantic.getName() != null) {
            physical.setSemanticName(semantic.getName());
        }
        physical.setMergeReason(toMergeReason(decision.reason()));
        physical.setMergeConfidence(toMergeConfidence(decision.reason()));
        physical.setMergeToken(decision.token());
    }

    private RelationshipMetadata.MergeReason toMergeReason(MatchReason reason) {
        return switch (reason) {
            case EXACT_QUALIFIED_NAME, EXACT_NAME, EXACT_COLUMN_NAME -> RelationshipMetadata.MergeReason.EXACT_NAME;
            case EXACT_SOURCE_ATTRIBUTE, EXACT_PHYSICAL_NAME -> RelationshipMetadata.MergeReason.EXACT_SOURCE_ATTRIBUTE;
            case EXACT_TARGET_ROLE -> RelationshipMetadata.MergeReason.EXACT_TARGET_ROLE;
            case NORMALIZED_FULL_TOKEN, NORMALIZED_ID_SUFFIX -> RelationshipMetadata.MergeReason.NORMALIZED_TOKEN;
            case NO_MATCH -> RelationshipMetadata.MergeReason.ILI2DB_ONLY;
        };
    }

    private RelationshipMetadata.MergeConfidence toMergeConfidence(MatchReason reason) {
        return switch (reason) {
            case EXACT_QUALIFIED_NAME, EXACT_NAME, EXACT_SOURCE_ATTRIBUTE, EXACT_TARGET_ROLE,
                EXACT_PHYSICAL_NAME, EXACT_COLUMN_NAME -> RelationshipMetadata.MergeConfidence.EXACT;
            case NORMALIZED_FULL_TOKEN, NORMALIZED_ID_SUFFIX -> RelationshipMetadata.MergeConfidence.MEDIUM;
            case NO_MATCH -> RelationshipMetadata.MergeConfidence.NONE;
        };
    }

    private Map<String, String> relationshipCandidateDetails(
        List<MatchCandidate<RelationshipMetadata>> candidates) {
        Map<String, String> details = new LinkedHashMap<>();
        List<String> physicalNames = new ArrayList<>();
        for (MatchCandidate<RelationshipMetadata> candidate : candidates) {
            physicalNames.add(candidate.physical().getName());
        }
        details.put("candidateCount", String.valueOf(candidates.size()));
        details.put("physicalCandidates", String.join(", ", physicalNames));
        return details;
    }

    private void mergeAssociations(ModelMetadata target, ModelMetadata semantic) {
        for (AssociationMetadata association : semantic.getAllAssociations()) {
            AssociationMetadata existing = target.getAssociation(association.getName());
            if (existing == null) {
                target.addAssociation(ModelMetadataCopier.copyAssociation(association));
            }
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

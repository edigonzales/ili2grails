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
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.EnumMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.model.builder.RelationshipMetadataBuilder;

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
 * ausgewählt. Ein physisches Element wird höchstens einmal konsumiert. Die
 * Inputs werden niemals mutiert; das Resultat wird über Builder erzeugt und
 * über die {@link ModelMetadataFactory} als immutable Snapshot gefroren.</p>
 */
public final class MetadataMerger {

    private final AttributeMatcher attributeMatcher;
    private final RelationshipMatcher relationshipMatcher;
    private final MetadataPostProcessor postProcessor;
    private final MetadataValidator validator;
    private final ModelMetadataFactory factory;

    public MetadataMerger(AttributeMatcher attributeMatcher,
                          RelationshipMatcher relationshipMatcher,
                          MetadataPostProcessor postProcessor,
                          MetadataValidator validator) {
        this(attributeMatcher, relationshipMatcher, postProcessor, validator,
            new ModelMetadataFactory());
    }

    public MetadataMerger(AttributeMatcher attributeMatcher,
                          RelationshipMatcher relationshipMatcher,
                          MetadataPostProcessor postProcessor,
                          MetadataValidator validator,
                          ModelMetadataFactory factory) {
        this.attributeMatcher = Objects.requireNonNull(attributeMatcher, "attributeMatcher");
        this.relationshipMatcher = Objects.requireNonNull(relationshipMatcher, "relationshipMatcher");
        this.postProcessor = Objects.requireNonNull(postProcessor, "postProcessor");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.factory = Objects.requireNonNull(factory, "factory");
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

        ModelMetadataBuilder target = physical.toBuilder();

        if (semantic.getIliVersion() != null) {
            target.iliVersion(semantic.getIliVersion());
        }
        if (semantic.getModelVersion() != null) {
            target.modelVersion(semantic.getModelVersion());
        }

        mergeClasses(target, physical, semantic, diagnostics);
        mergeEnums(target, semantic);
        mergeRelationships(target, physical, semantic, diagnostics);
        mergeAssociations(target, semantic);

        postProcessor.process(target);

        ModelMetadata built = factory.buildValidated(target);

        List<MergeDiagnostic> validatorDiagnostics = validator.validate(built);
        diagnostics.addAll(validatorDiagnostics);

        List<MergeDiagnostic> sorted = diagnostics.stream()
            .sorted(Comparator
                .comparing(MergeDiagnostic::severity)
                .thenComparing(MergeDiagnostic::code)
                .thenComparing(diagnostic -> nullToEmpty(diagnostic.semanticElement()))
                .thenComparing(diagnostic -> nullToEmpty(diagnostic.physicalElement())))
            .toList();

        return new MetadataMergeResult(built, sorted);
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

    private void mergeClasses(ModelMetadataBuilder target,
                              ModelMetadata physical,
                              ModelMetadata semantic,
                              List<MergeDiagnostic> diagnostics) {
        for (ClassMetadata semanticClass : sortedSemanticClasses(semantic)) {
            ClassMetadata targetClass = physical.getClass(semanticClass.getName());
            if (targetClass == null) {
                ClassMetadataBuilder copy = target.addClassFrom(semanticClass);
                for (RelationshipMetadata relationship : semantic.relationshipsFrom(semanticClass.getName())) {
                    target.appendRelationshipBuilder(RelationshipMetadataBuilder.from(relationship));
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
            enrichClass(target.requireClassBuilder(targetClass.getName()), semanticClass);
            mergeAttributes(target, targetClass, semanticClass, diagnostics);
        }

        Set<String> semanticClassNames = new LinkedHashSet<>();
        for (ClassMetadata semanticClass : semantic.getAllClasses()) {
            semanticClassNames.add(semanticClass.getName());
        }
        for (ClassMetadata targetClass : physical.getAllClasses()) {
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

    private void enrichClass(ClassMetadataBuilder target, ClassMetadata semantic) {
        if (semantic.getDocumentation() != null) {
            target.documentation(semantic.getDocumentation());
        }
        if (semantic.getKind() != null) {
            target.kind(semantic.getKind());
        }
        if (semantic.getTopicName() != null) {
            target.topicName(semantic.getTopicName());
        }
        target.abstractClass(semantic.isAbstract());
        if (semantic.getBaseClass() != null) {
            target.baseClass(semantic.getBaseClass());
        }
        semantic.getLabels().forEach(target::label);
    }

    private void mergeAttributes(ModelMetadataBuilder targetBuilder,
                                 ClassMetadata targetClass,
                                 ClassMetadata semanticClass,
                                 List<MergeDiagnostic> diagnostics) {
        List<MatchDecision<AttributeMetadata>> decisions =
            attributeMatcher.match(targetClass, semanticClass);

        for (MatchDecision<AttributeMetadata> decision : decisions) {
            switch (decision.status()) {
                case MATCHED -> mergeAttributeIntoClass(targetBuilder, targetClass, decision);
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

    private void mergeAttributeIntoClass(ModelMetadataBuilder targetBuilder,
                                         ClassMetadata targetClass,
                                         MatchDecision<AttributeMetadata> decision) {
        AttributeMetadataBuilder merged = mergeAttribute(decision.physical(), decision.semantic());
        ClassMetadataBuilder classBuilder = targetBuilder.requireClassBuilder(targetClass.getName());
        classBuilder.replaceAttribute(merged);
    }

    private AttributeMetadataBuilder mergeAttribute(AttributeMetadata physical,
                                                    AttributeMetadata semantic) {
        AttributeMetadataBuilder merged = AttributeMetadataBuilder.from(physical);

        if (semantic.getQualifiedName() != null) {
            merged.qualifiedName(semantic.getQualifiedName());
        }

        if (merged.referencedClass() == null && semantic.getReferencedClass() != null) {
            merged.referencedClass(semantic.getReferencedClass());
        }

        merged.mandatory(mergeMandatory(physical, semantic));

        if (semantic.getCoreType() != CoreType.UNKNOWN) {
            merged.coreType(semantic.getCoreType());
        }
        if (semantic.getJavaType() != null) {
            merged.javaType(semantic.getJavaType());
        }
        if (semantic.getIliType() != null) {
            merged.iliType(semantic.getIliType());
        }
        if (semantic.getDomainName() != null) {
            merged.domainName(semantic.getDomainName());
        }
        if (semantic.getEnumType() != null) {
            merged.enumType(semantic.getEnumType());
        } else if (physical.getEnumType() != null) {
            merged.enumType(physical.getEnumType());
        }
        Integer narrowerMaxLength = narrowerMaxLength(physical.getMaxLength(), semantic.getMaxLength());
        if (narrowerMaxLength != null) {
            merged.maxLength(narrowerMaxLength);
        }
        if (semantic.getMinValue() != null) {
            merged.minValue(semantic.getMinValue());
        }
        if (semantic.getMaxValue() != null) {
            merged.maxValue(semantic.getMaxValue());
        }
        if (semantic.getPrecision() != null) {
            merged.precision(semantic.getPrecision());
        } else if (physical.getPrecision() != null) {
            merged.precision(physical.getPrecision());
        }
        if (semantic.getScale() != null) {
            merged.scale(semantic.getScale());
        } else if (physical.getScale() != null) {
            merged.scale(physical.getScale());
        }
        if (semantic.getCardinalityMin() != null) {
            merged.cardinalityMin(semantic.getCardinalityMin());
        }
        if (semantic.getCardinalityMax() != null) {
            merged.cardinalityMax(semantic.getCardinalityMax());
        }
        if (semantic.isOrdered() || physical.isOrdered()) {
            merged.ordered(true);
        }
        if (semantic.getUnit() != null) {
            merged.unit(semantic.getUnit());
        }
        if (semantic.getDocumentation() != null) {
            merged.documentation(semantic.getDocumentation());
        }
        if (semantic.isGeometry() || physical.isGeometry()) {
            merged.geometry(true);
        }
        if (physical.getGeometryKind() != null && !physical.getGeometryKind().isBlank()) {
            merged.geometryKind(physical.getGeometryKind());
        } else if (semantic.getGeometryKind() != null) {
            merged.geometryKind(semantic.getGeometryKind());
        }
        if (physical.getGeometrySrid() != null) {
            merged.geometrySrid(physical.getGeometrySrid());
        }
        if (physical.getGeometryHasZ() != null) {
            merged.geometryHasZ(physical.getGeometryHasZ());
        } else if (semantic.getGeometryHasZ() != null) {
            merged.geometryHasZ(semantic.getGeometryHasZ());
        }
        if (physical.getGeometryHasM() != null) {
            merged.geometryHasM(physical.getGeometryHasM());
        } else if (semantic.getGeometryHasM() != null) {
            merged.geometryHasM(semantic.getGeometryHasM());
        }
        if (semantic.getAllowEmptyGeometry() != null) {
            merged.allowEmptyGeometry(semantic.getAllowEmptyGeometry());
        } else if (physical.getAllowEmptyGeometry() != null) {
            merged.allowEmptyGeometry(physical.getAllowEmptyGeometry());
        }
        semantic.getLabels().forEach(merged::label);
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

    private void mergeEnums(ModelMetadataBuilder target, ModelMetadata semantic) {
        for (EnumMetadata enumMetadata : semantic.getAllEnums()) {
            if (target.findEnumBuilder(enumMetadata.getName()).isEmpty()) {
                target.addEnumFrom(enumMetadata);
            }
        }
    }

    private boolean hasRelationshipIdentity(ModelMetadataBuilder target,
                                             RelationshipMetadata relationship) {
        ch.interlis.generator.model.RelationshipIdentity identity =
            ch.interlis.generator.model.RelationshipIdentity.of(relationship);
        return target.relationshipBuilders().stream()
            .anyMatch(existing -> ch.interlis.generator.model.RelationshipIdentity.of(
                existing.buildUnchecked()).equals(identity));
    }

    private void appendIfAbsent(ModelMetadataBuilder target,
                                RelationshipMetadata relationship,
                                boolean duplicate, List<MergeDiagnostic> diagnostics) {
        if (!hasRelationshipIdentity(target, relationship)) {
            target.appendRelationshipBuilder(RelationshipMetadataBuilder.from(relationship));
            return;
        }
        if (duplicate) {
            diagnostics.add(new MergeDiagnostic(
                MergeSeverity.WARNING,
                MergeDiagnosticCode.DUPLICATE_CANONICAL_RELATIONSHIP,
                "relationship identity already present; semantic copy skipped",
                relationship.getName(),
                null,
                Map.of()));
        }
    }

    private void mergeRelationships(ModelMetadataBuilder target,
                                    ModelMetadata physical,
                                    ModelMetadata semantic,
                                    List<MergeDiagnostic> diagnostics) {
        List<MatchDecision<RelationshipMetadata>> decisions =
            relationshipMatcher.match(physical, semantic);

        List<RelationshipMetadata> physicalRelationships = physical.getAllRelationships();

        for (MatchDecision<RelationshipMetadata> decision : decisions) {
            RelationshipMetadata semanticRelationship = decision.semantic();
            switch (decision.status()) {
                case MATCHED -> {
                    RelationshipMetadata physicalRelationship = decision.physical();
                    int index = physicalRelationships.indexOf(physicalRelationship);
                    RelationshipMetadataBuilder merged = mergeRelationship(
                        physicalRelationship, semanticRelationship, decision);
                    if (index >= 0) {
                        target.replaceRelationshipBuilder(index, merged);
                    } else {
                        target.appendRelationshipBuilder(merged);
                    }
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
                    appendIfAbsent(target, semanticRelationship, true, diagnostics);
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
                    appendIfAbsent(target, semanticRelationship, false, diagnostics);
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
                    appendIfAbsent(target, semanticRelationship, false, diagnostics);
                }
            }
        }

        validateAssociationRoles(target, semantic, diagnostics);
    }

    private void validateAssociationRoles(ModelMetadataBuilder target,
                                          ModelMetadata semantic,
                                          List<MergeDiagnostic> diagnostics) {
        Map<String, List<RelationshipMetadataBuilder>> rolesByAssociation = new LinkedHashMap<>();
        for (RelationshipMetadataBuilder relationship : target.relationshipBuilders()) {
            if (relationship.semanticKind() != RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE
                || !"ili2db+ili2c".equals(relationship.source())) {
                continue;
            }
            String associationName = postProcessor
                .resolveAssociationName(builderSnapshot(target), snapshotRelationship(relationship));
            rolesByAssociation.computeIfAbsent(associationName, key -> new ArrayList<>())
                .add(relationship);
        }
        for (Map.Entry<String, List<RelationshipMetadataBuilder>> entry : rolesByAssociation.entrySet()) {
            String associationName = entry.getKey();
            if (target.findAssociationBuilder(associationName).isEmpty()) {
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
            for (RelationshipMetadataBuilder relationship : entry.getValue()) {
                String roleName = relationship.targetRoleName();
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

    private ModelMetadata builderSnapshot(ModelMetadataBuilder builder) {
        return builder.buildUnchecked();
    }

    private RelationshipMetadata snapshotRelationship(RelationshipMetadataBuilder builder) {
        return builder.buildUnchecked();
    }

    private RelationshipMetadataBuilder mergeRelationship(RelationshipMetadata physical,
                                                          RelationshipMetadata semantic,
                                                          MatchDecision<RelationshipMetadata> decision) {
        RelationshipMetadataBuilder merged = RelationshipMetadataBuilder.from(physical);
        if (semantic.getType() != null) {
            merged.type(semantic.getType());
        }
        if (semantic.getSemanticKind() != null) {
            merged.semanticKind(semantic.getSemanticKind());
        }
        if (semantic.getAssociationName() != null) {
            merged.associationName(semantic.getAssociationName());
        }
        if (semantic.getSourceRoleName() != null) {
            merged.sourceRoleName(semantic.getSourceRoleName());
        }
        if (semantic.getTargetRoleName() != null) {
            merged.targetRoleName(semantic.getTargetRoleName());
        }
        if (semantic.getOppositeRoleName() != null) {
            merged.oppositeRoleName(semantic.getOppositeRoleName());
        }
        if (semantic.getCardinality() != null) {
            merged.cardinality(semantic.getCardinality());
        }
        merged.mandatory(semantic.isMandatory());
        merged.ordered(semantic.isOrdered());
        merged.external(semantic.isExternal());
        merged.composition(semantic.isComposition());
        merged.source("ili2db+ili2c");
        if (semantic.getSemanticName() != null) {
            merged.semanticName(semantic.getSemanticName());
        } else if (semantic.getName() != null) {
            merged.semanticName(semantic.getName());
        }
        merged.mergeReason(toMergeReason(decision.reason()));
        merged.mergeConfidence(toMergeConfidence(decision.reason()));
        merged.mergeToken(decision.token());
        return merged;
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

    private void mergeAssociations(ModelMetadataBuilder target, ModelMetadata semantic) {
        for (AssociationMetadata association : semantic.getAllAssociations()) {
            if (target.findAssociationBuilder(association.getName()).isEmpty()) {
                target.addAssociationFrom(association);
            }
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

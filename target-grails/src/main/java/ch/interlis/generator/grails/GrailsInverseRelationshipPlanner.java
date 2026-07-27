package ch.interlis.generator.grails;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves safe inverse editors for physical many-to-one relationships.
 *
 * <p>The mapper remains the source of truth for the generated collection and
 * property names. This is important when ili2db names need normalization or
 * collide with other generated properties.
 */
public final class GrailsInverseRelationshipPlanner {

    private static final List<String> PREFERRED_LABEL_LANGUAGES = List.of("de-CH", "de", "en");

    private final ModelMetadata metadata;
    private final GenerationConfig config;
    private final TargetNameRegistry registry;
    private final GrailsRelationshipMapper relationshipMapper;
    private final List<GrailsInverseRelationshipPlan> plans;

    private GrailsInverseRelationshipPlanner(ModelMetadata metadata,
                                             GenerationConfig config,
                                             TargetNameRegistry registry,
                                             GrailsRelationshipMapper relationshipMapper) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.config = Objects.requireNonNull(config, "config");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.relationshipMapper = Objects.requireNonNull(relationshipMapper, "relationshipMapper");
        this.plans = buildPlans();
    }

    public static GrailsInverseRelationshipPlanner forMetadata(ModelMetadata metadata,
                                                                GenerationConfig config,
                                                                TargetNameRegistry registry,
                                                                GrailsRelationshipMapper relationshipMapper) {
        return new GrailsInverseRelationshipPlanner(metadata, config, registry, relationshipMapper);
    }

    public List<GrailsInverseRelationshipPlan> plans() {
        return plans;
    }

    public List<GrailsInverseRelationshipPlan> plansForOwner(String ownerIliClassName) {
        if (ownerIliClassName == null) {
            return List.of();
        }
        return plans.stream()
            .filter(plan -> ownerIliClassName.equals(plan.ownerIliClassName()))
            .sorted(Comparator.comparing(GrailsInverseRelationshipPlan::collectionPropertyName))
            .toList();
    }

    private List<GrailsInverseRelationshipPlan> buildPlans() {
        List<GrailsInverseRelationshipPlan> result = new ArrayList<>();
        for (ClassMetadata ownerClass : relationshipMapper.generatedClasses()) {
            if (!isRegularPersistentClass(ownerClass)) {
                continue;
            }
            GrailsRelationshipMapper.DomainMapping ownerMapping = relationshipMapper.map(ownerClass);
            for (GrailsRelationshipMapper.DomainCollection collection : ownerMapping.collections()) {
                RelationshipMetadata relationship = collection.relationship();
                if (!isSafeInverseRelationship(ownerClass, relationship)) {
                    continue;
                }
                ClassMetadata relatedClass = metadata.getClass(relationship.getSourceClass());
                if (!isRegularPersistentClass(relatedClass) || !relationshipMapper.shouldGenerate(relatedClass)) {
                    continue;
                }
                List<GrailsRelationshipMapper.DomainProperty> relatedProperties =
                    matchingRelatedProperties(relatedClass, relationship);
                if (relatedProperties.size() != 1) {
                    continue;
                }
                GrailsRelationshipMapper.DomainProperty relatedProperty = relatedProperties.get(0);
                if (!registry.className(ownerClass).equals(relatedProperty.type())
                    || relatedProperty.columnName() == null
                    || relatedProperty.columnName().isBlank()) {
                    continue;
                }

                result.add(new GrailsInverseRelationshipPlan(
                    ownerClass.getName(),
                    collection.name(),
                    relatedClass.getName(),
                    registry.domainPackage() + "." + registry.className(relatedClass),
                    relatedProperty.name(),
                    relationship.getName(),
                    collectionLabel(relatedClass),
                    classLabel(relatedClass),
                    !relatedProperty.nullable(),
                    config.isAssociationUiEnabled(),
                    config.isAssociationUiEditable()
                ));
            }
        }
        return List.copyOf(result);
    }

    private List<GrailsRelationshipMapper.DomainProperty> matchingRelatedProperties(
        ClassMetadata relatedClass,
        RelationshipMetadata relationship
    ) {
        return relationshipMapper.map(relatedClass).properties().stream()
            .filter(property -> sameRelationship(property.relationship(), relationship))
            .toList();
    }

    private boolean isSafeInverseRelationship(ClassMetadata ownerClass, RelationshipMetadata relationship) {
        if (relationship == null
            || relationship.getType() != RelationshipMetadata.RelationType.MANY_TO_ONE
            || relationship.isComposition()
            || relationship.isExternal()
            || relationship.isOrdered()) {
            return false;
        }
        if (relationship.getSemanticKind() != RelationshipMetadata.SemanticKind.ILI2DB_FK
            && relationship.getSemanticKind() != RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE) {
            return false;
        }
        return ownerClass.getName().equals(relationship.getTargetClass())
            && relationship.getSourceClass() != null;
    }

    private boolean isRegularPersistentClass(ClassMetadata classMetadata) {
        if (classMetadata == null) {
            return false;
        }
        if (classMetadata.getKind() == ClassMetadata.ClassKind.ASSOCIATION
            || classMetadata.getKind() == ClassMetadata.ClassKind.STRUCTURE) {
            return false;
        }
        return notBlank(classMetadata.getTableName()) || notBlank(classMetadata.getSqlName());
    }

    private boolean sameRelationship(RelationshipMetadata left, RelationshipMetadata right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getName(), right.getName())
            && Objects.equals(left.getSourceClass(), right.getSourceClass())
            && Objects.equals(left.getTargetClass(), right.getTargetClass())
            && Objects.equals(left.getSourceAttribute(), right.getSourceAttribute())
            && Objects.equals(left.getTargetRoleName(), right.getTargetRoleName())
            && left.getSemanticKind() == right.getSemanticKind();
    }

    private String collectionLabel(ClassMetadata relatedClass) {
        return NameUtils.pluralize(classLabel(relatedClass));
    }

    private String classLabel(ClassMetadata classMetadata) {
        Map<String, String> labels = classMetadata.getLabels();
        for (String language : PREFERRED_LABEL_LANGUAGES) {
            String label = labels.get(language);
            if (notBlank(label)) {
                return label;
            }
        }
        String simpleName = registry.className(classMetadata);
        return notBlank(simpleName) ? simpleName : classMetadata.getSimpleName();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

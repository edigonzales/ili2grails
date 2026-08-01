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
 * Plant inverse/navigationale Related-Sections direkt aus den Core-Relationships
 * und den tatsächlichen Child-Properties.
 *
 * <p>Eine inverse Related-Section ist keine GORM-Collection. Der Planner erzeugt
 * bewusst kein {@code static hasMany}; die Navigation läuft über den
 * Query-Service. Mehrere FKs derselben Zielklasse erzeugen getrennte Pläne mit
 * unterschiedlichen Property-Namen.</p>
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

        for (RelationshipMetadata relationship : eligibleRelationships()) {
            ClassMetadata ownerClass = metadata.getClass(relationship.getTargetClass());
            ClassMetadata relatedClass = metadata.getClass(relationship.getSourceClass());
            if (ownerClass == null || relatedClass == null) {
                continue;
            }

            GrailsRelationshipMapper.PropertyResolution resolution =
                relationshipMapper.resolvePropertyForRelationship(relatedClass, relationship);
            if (resolution.status() != GrailsRelationshipMapper.PropertyResolution.Status.RESOLVED) {
                continue;
            }
            GrailsRelationshipMapper.DomainProperty relatedProperty = resolution.property();
            if (relatedProperty.columnName() == null || relatedProperty.columnName().isBlank()) {
                continue;
            }
            if (!registry.className(ownerClass).equals(relatedProperty.type())) {
                continue;
            }

            result.add(toPlan(ownerClass, relatedClass, relatedProperty, relationship));
        }

        return sortedImmutable(result);
    }

    private List<GrailsInverseRelationshipPlan> sortedImmutable(List<GrailsInverseRelationshipPlan> result) {
        return result.stream()
            .sorted(Comparator
                .comparing(GrailsInverseRelationshipPlan::ownerIliClassName,
                    Comparator.nullsLast(String::compareTo))
                .thenComparing(GrailsInverseRelationshipPlan::collectionPropertyName,
                    Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    private GrailsInverseRelationshipPlan toPlan(ClassMetadata ownerClass,
                                                 ClassMetadata relatedClass,
                                                 GrailsRelationshipMapper.DomainProperty relatedProperty,
                                                 RelationshipMetadata relationship) {
        return new GrailsInverseRelationshipPlan(
            ownerClass.getName(),
            registry.collectionPropertyName(relationship),
            relatedClass.getName(),
            registry.domainPackage() + "." + registry.className(relatedClass),
            relatedProperty.name(),
            relationship.getName(),
            collectionLabel(relatedClass),
            classLabel(relatedClass),
            !relatedProperty.nullable(),
            config.isAssociationUiEnabled(),
            config.isAssociationUiEditable(),
            false
        );
    }

    /**
     * Nur einfache inverse MANY_TO_ONE-Relationships: ILI2DB_FK oder
     * REFERENCE_ATTRIBUTE, keine Association-Rolle, keine Komposition, nicht
     * external, nicht ordered, physisch belegt, auf generierten regulären
     * persistenten Klassen.
     */
    private List<RelationshipMetadata> eligibleRelationships() {
        return metadata.getAllRelationships().stream()
            .filter(relationship -> relationship.getType() == RelationshipMetadata.RelationType.MANY_TO_ONE)
            .filter(relationship -> relationship.getSemanticKind()
                == RelationshipMetadata.SemanticKind.ILI2DB_FK
                || relationship.getSemanticKind()
                == RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE)
            .filter(relationship -> !relationship.isComposition())
            .filter(relationship -> !relationship.isExternal())
            .filter(relationship -> !relationship.isOrdered())
            .filter(relationship -> relationship.getTargetClass() != null
                && relationship.getSourceClass() != null)
            .filter(relationship -> isRegularGeneratedPersistentClass(
                metadata.getClass(relationship.getTargetClass())))
            .filter(relationship -> isRegularGeneratedPersistentClass(
                metadata.getClass(relationship.getSourceClass())))
            .sorted(Comparator
                .comparing(RelationshipMetadata::getSourceClass,
                    Comparator.nullsLast(String::compareTo))
                .thenComparing(RelationshipMetadata::getTargetClass,
                    Comparator.nullsLast(String::compareTo))
                .thenComparing(RelationshipMetadata::getTargetRoleName,
                    Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    private boolean isRegularGeneratedPersistentClass(ClassMetadata classMetadata) {
        if (classMetadata == null || !relationshipMapper.shouldGenerate(classMetadata)) {
            return false;
        }
        if (classMetadata.getKind() == ClassMetadata.ClassKind.ASSOCIATION
            || classMetadata.getKind() == ClassMetadata.ClassKind.STRUCTURE) {
            return false;
        }
        return notBlank(classMetadata.getTableName()) || notBlank(classMetadata.getSqlName());
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

package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationAttributeDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationCreateMode;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind;
import ch.interlis.generator.grails.runtime.api.descriptor.DisplayDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind;
import ch.interlis.generator.grails.runtime.api.descriptor.FieldDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.FieldKind;
import ch.interlis.generator.grails.runtime.api.descriptor.GeometryDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipMode;
import ch.interlis.generator.grails.runtime.api.descriptor.RelationshipDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeCoreType;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Single place deriving the typed runtime descriptors from the immutable core
 * IR and the Grails plans.
 *
 * <p>Generators must not re-derive these business decisions from core
 * metadata; they consume the plan produced here.</p>
 */
public final class RuntimeDescriptorPlanner {

    private static final List<String> DISPLAY_FIELD_PREFERENCES = List.of(
        "name", "bezeichnung", "label", "title", "code", "ident"
    );

    private final TargetNameRegistry names;
    private final GrailsRelationshipMapper relationships;
    private final GrailsAssociationPlanner associations;
    private final GrailsInverseRelationshipPlanner inverses;
    private final Map<String, ClassMetadata> classesByName;

    public RuntimeDescriptorPlanner(TargetNameRegistry names,
                                    GrailsRelationshipMapper relationships,
                                    GrailsAssociationPlanner associations,
                                    GrailsInverseRelationshipPlanner inverses) {
        this.names = Objects.requireNonNull(names, "names");
        this.relationships = Objects.requireNonNull(relationships, "relationships");
        this.associations = Objects.requireNonNull(associations, "associations");
        this.inverses = Objects.requireNonNull(inverses, "inverses");
        this.classesByName = new LinkedHashMap<>();
        for (ClassMetadata classMetadata : relationships.generatedClasses()) {
            classesByName.put(classMetadata.getName(), classMetadata);
        }
    }

    public RuntimeDescriptorPlan plan(ModelMetadata metadata, GenerationConfig config) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(config, "config");

        List<DomainDescriptor> domains = new ArrayList<>();
        List<AssociationDescriptor> associationDescriptors = new ArrayList<>();
        List<AssociationContextDescriptor> contextDescriptors = new ArrayList<>();
        List<RuntimeDescriptorDiagnostic> diagnostics = new ArrayList<>();

        List<ClassMetadata> classes = new ArrayList<>(relationships.generatedClasses());
        classes.sort(Comparator.comparing(ClassMetadata::getName, Comparator.nullsLast(String::compareTo)));
        for (ClassMetadata classMetadata : classes) {
            domains.add(planDomain(classMetadata, metadata, config));
        }

        boolean writeEnabled = config.isAssociationUiEditable();
        List<GrailsAssociationPlan> plans = new ArrayList<>(associations.plans());
        plans.sort(Comparator.comparing(
            GrailsAssociationPlan::associationName, Comparator.nullsLast(String::compareTo)));
        for (GrailsAssociationPlan plan : plans) {
            associationDescriptors.add(planAssociation(plan, config, writeEnabled));
            for (GrailsAssociationContextPlan context : plan.contexts()) {
                contextDescriptors.add(planContext(plan, context, writeEnabled));
            }
        }

        return new RuntimeDescriptorPlan(
            domains,
            associationDescriptors,
            contextDescriptors,
            diagnostics
        );
    }

    private DomainDescriptor planDomain(ClassMetadata classMetadata,
                                        ModelMetadata metadata,
                                        GenerationConfig config) {
        String iliName = classMetadata.getName();
        String modelName = metadata.getModelName();
        boolean associationDomain = associations.isAssociationDomain(iliName);
        String topicName = topicPath(classMetadata.getTopicName(), modelName);
        String domainClassName = names.domainPackage() + "." + names.className(classMetadata);

        Map<String, FieldDescriptor> fields = new LinkedHashMap<>();
        Map<String, RelationshipDescriptor> relationshipDescriptors = new LinkedHashMap<>();
        Map<String, GeometryDescriptor> geometries = new LinkedHashMap<>();
        List<String> displayFields = new ArrayList<>();
        List<String> searchFields = new ArrayList<>();

        List<GrailsRelationshipMapper.DomainProperty> properties = new ArrayList<>(
            relationships.map(classMetadata).properties());
        properties.sort(Comparator.comparing(
            GrailsRelationshipMapper.DomainProperty::name, Comparator.nullsLast(String::compareTo)));

        for (GrailsRelationshipMapper.DomainProperty property : properties) {
            AttributeMetadata attribute = property.attribute();
            if (property.geometry()) {
                geometries.put(property.name(), new GeometryDescriptor(
                    property.name(),
                    property.geometrySrid(),
                    property.geometryKind() == null || property.geometryKind().isBlank()
                        ? "GEOMETRY" : property.geometryKind().toUpperCase(Locale.ROOT),
                    property.geometryHasZ(),
                    property.geometryHasM(),
                    property.allowEmptyGeometry()));
            }
            if (attribute != null) {
                fields.put(property.name(), planField(property, attribute));
            }
            if (property.relationship() != null) {
                relationshipDescriptors.put(property.name(), planRelationship(property));
            }
        }

        displayFields.addAll(displayFields(properties));
        searchFields.addAll(searchFields(properties));

        Map<String, InverseRelationshipDescriptor> inverseDescriptors = new LinkedHashMap<>();
        List<GrailsInverseRelationshipPlan> inversePlans = new ArrayList<>(
            inverses.plansForOwner(iliName));
        inversePlans.sort(Comparator.comparing(
            GrailsInverseRelationshipPlan::collectionPropertyName,
            Comparator.nullsLast(String::compareTo)));
        for (GrailsInverseRelationshipPlan plan : inversePlans) {
            if (plan.visible()) {
                inverseDescriptors.put(plan.collectionPropertyName(), planInverse(plan));
            }
        }

        return new DomainDescriptor(
            iliName,
            modelName,
            topicName,
            domainClassName,
            names.viewPath(classMetadata),
            names.className(classMetadata),
            label(classMetadata, config),
            associationDomain ? DomainKind.ASSOCIATION : DomainKind.CLASS,
            associations.showDomainInNavigation(iliName),
            new DisplayDescriptor(null, displayFields, searchFields),
            fields,
            relationshipDescriptors,
            inverseDescriptors,
            geometries
        );
    }

    private FieldDescriptor planField(GrailsRelationshipMapper.DomainProperty property,
                                      AttributeMetadata attribute) {
        FieldKind kind;
        if (property.geometry()) {
            kind = FieldKind.GEOMETRY;
        } else if (property.relationship() != null) {
            kind = FieldKind.RELATIONSHIP;
        } else if (attribute.getEnumType() != null) {
            kind = FieldKind.ENUM;
        } else {
            kind = FieldKind.SCALAR;
        }
        String maxLength = property.maxLength() != null
            ? property.maxLength().toString()
            : (property.constraints() != null && property.constraints().maxLength() != null
            ? property.constraints().maxLength().toString() : null);
        return new FieldDescriptor(
            property.name(),
            attribute.getQualifiedName() != null && !attribute.getQualifiedName().isBlank()
                ? attribute.getQualifiedName() : attribute.getName(),
            property.type(),
            mapCoreType(attribute.getCoreType()),
            kind,
            resolveDefaultLabel(attribute),
            !property.nullable(),
            property.maxLength(),
            property.minValue(),
            property.maxValue(),
            property.constraints() != null ? property.constraints().precision() : null,
            property.constraints() != null ? property.constraints().scale() : null,
            attribute.getUnit(),
            attribute.getEnumType()
        );
    }

    private RelationshipDescriptor planRelationship(GrailsRelationshipMapper.DomainProperty property) {
        RelationshipMetadata relationship = property.relationship();
        String targetClass = relationshipTargetQualifiedName(relationship);
        String label = relationshipLabel(property, relationship);
        return new RelationshipDescriptor(
            property.name(),
            property.name(),
            targetClass,
            relationship.getSemanticKind() != null ? relationship.getSemanticKind().name() : null,
            label,
            relationship.getSourceAttribute(),
            relationship.getTargetRoleName(),
            !property.nullable()
        );
    }

    private String relationshipTargetQualifiedName(RelationshipMetadata relationship) {
        if (relationship.getTargetClass() != null) {
            ClassMetadata target = classesByName.get(relationship.getTargetClass());
            if (target != null) {
                return names.domainPackage() + "." + names.className(target);
            }
        }
        return null;
    }

    private String relationshipLabel(GrailsRelationshipMapper.DomainProperty property,
                                     RelationshipMetadata relationship) {
        if (relationship.getTargetRoleName() != null && !relationship.getTargetRoleName().isBlank()) {
            return relationship.getTargetRoleName();
        }
        if (relationship.getSourceAttribute() != null && !relationship.getSourceAttribute().isBlank()) {
            return relationship.getSourceAttribute();
        }
        if (relationship.getName() != null && !relationship.getName().isBlank()) {
            return relationship.getName();
        }
        return property.name();
    }

    private InverseRelationshipDescriptor planInverse(GrailsInverseRelationshipPlan plan) {
        String relatedController = null;
        ClassMetadata related = classesByName.get(plan.relatedIliClassName());
        if (related != null) {
            relatedController = names.viewPath(related);
        }
        return new InverseRelationshipDescriptor(
            plan.collectionPropertyName(),
            plan.label(),
            plan.ownerIliClassName(),
            plan.relatedIliClassName(),
            plan.relatedDomainQualifiedName(),
            relatedController,
            plan.relatedPropertyName(),
            plan.relatedLabel(),
            plan.writable(),
            plan.visible(),
            InverseRelationshipMode.AUTO
        );
    }

    private AssociationDescriptor planAssociation(GrailsAssociationPlan plan,
                                                  GenerationConfig config,
                                                  boolean writeEnabled) {
        List<AssociationRoleDescriptor> roles = new ArrayList<>();
        for (GrailsAssociationRolePlan role : plan.roles()) {
            roles.add(new AssociationRoleDescriptor(
                role.roleName(),
                role.roleLabel(),
                role.domainPropertyName(),
                role.targetIliClassName(),
                role.targetDomainQualifiedName(),
                role.minCardinality() != null ? role.minCardinality() : 0,
                role.maxCardinality() != null ? role.maxCardinality() : -1,
                role.mandatory(),
                role.ordered(),
                role.external(),
                role.composition()
            ));
        }
        List<AssociationAttributeDescriptor> attributes = new ArrayList<>();
        for (GrailsAssociationAttributePlan attribute : plan.attributes()) {
            attributes.add(new AssociationAttributeDescriptor(
                attribute.iliName(),
                attribute.domainPropertyName(),
                attribute.javaType(),
                mapCoreTypeName(attribute.coreType()),
                attribute.label(),
                attribute.mandatory(),
                attribute.maxLength(),
                attribute.unit(),
                attribute.enumType(),
                attribute.geometry()
            ));
        }
        return new AssociationDescriptor(
            plan.associationName(),
            plan.associationIliClassName(),
            plan.associationDomainQualifiedName(),
            plan.associationControllerName(),
            plan.associationViewPath(),
            plan.physicalTable(),
            plan.physicalSqlName(),
            mapStorageKind(plan.storageKind()),
            plan.writable() && writeEnabled,
            resolveShowInNavigation(plan, config),
            roles,
            attributes,
            plan.diagnostics()
        );
    }

    private AssociationContextDescriptor planContext(GrailsAssociationPlan plan,
                                                     GrailsAssociationContextPlan context,
                                                     boolean writeEnabled) {
        boolean writable = context.writable() && writeEnabled;
        AssociationCreateMode createMode = writable && context.createMode() != null
            ? mapCreateMode(context.createMode())
            : AssociationCreateMode.NONE;
        return new AssociationContextDescriptor(
            context.contextId(),
            plan.associationName(),
            context.participantDomainQualifiedName(),
            context.fixedRoleName(),
            context.fixedRolePropertyName(),
            context.editableRoleNames(),
            context.editableRolePropertyNames(),
            context.defaultLabel(),
            context.messageCode(),
            context.presentationKind() != null ? context.presentationKind().name() : null,
            createMode,
            writable,
            context.removable() && writable,
            context.showAssociationObjectLink(),
            context.perspectiveMinCardinality() != null ? context.perspectiveMinCardinality() : 0,
            context.perspectiveMaxCardinality() != null ? context.perspectiveMaxCardinality() : -1,
            context.diagnostics()
        );
    }

    private boolean resolveShowInNavigation(GrailsAssociationPlan plan, GenerationConfig config) {
        String navigation = config.getAssociationNavigation();
        if (GenerationConfig.ASSOCIATION_NAVIGATION_SHOW.equals(navigation)) {
            return true;
        }
        if (GenerationConfig.ASSOCIATION_NAVIGATION_HIDE.equals(navigation)) {
            return false;
        }
        if (!config.isHideContextualAssociationControllers()) {
            return true;
        }
        return plan.showInNavigation();
    }

    // ------------------------------------------------------------------
    // Display field derivation (same preference rules as the domain generator)
    // ------------------------------------------------------------------

    private List<String> displayFields(List<GrailsRelationshipMapper.DomainProperty> properties) {
        List<String> preferred = DISPLAY_FIELD_PREFERENCES.stream()
            .flatMap(preference -> properties.stream()
                .filter(this::isDisplayCandidate)
                .filter(property -> preference.equals(normalizedName(property.name())))
                .map(GrailsRelationshipMapper.DomainProperty::name))
            .distinct()
            .limit(2)
            .toList();
        if (!preferred.isEmpty()) {
            return preferred;
        }
        return properties.stream()
            .filter(this::isTextDisplayCandidate)
            .map(GrailsRelationshipMapper.DomainProperty::name)
            .distinct()
            .limit(2)
            .toList();
    }

    private List<String> searchFields(List<GrailsRelationshipMapper.DomainProperty> properties) {
        List<String> preferred = DISPLAY_FIELD_PREFERENCES.stream()
            .flatMap(preference -> properties.stream()
                .filter(this::isTextDisplayCandidate)
                .filter(property -> preference.equals(normalizedName(property.name())))
                .map(GrailsRelationshipMapper.DomainProperty::name))
            .distinct()
            .toList();
        List<String> textFields = properties.stream()
            .filter(this::isTextDisplayCandidate)
            .map(GrailsRelationshipMapper.DomainProperty::name)
            .distinct()
            .toList();
        List<String> result = new ArrayList<>(preferred);
        for (String field : textFields) {
            if (!result.contains(field)) {
                result.add(field);
            }
        }
        return result;
    }

    private boolean isDisplayCandidate(GrailsRelationshipMapper.DomainProperty property) {
        if (property == null || property.geometry() || property.relationship() != null
            || property.attribute() == null) {
            return false;
        }
        String name = normalizedName(property.name());
        return !"id".equals(name) && !"version".equals(name) && !"tid".equals(name);
    }

    private boolean isTextDisplayCandidate(GrailsRelationshipMapper.DomainProperty property) {
        if (!isDisplayCandidate(property)) {
            return false;
        }
        AttributeMetadata attribute = property.attribute();
        return "String".equals(property.type())
            || attribute.getCoreType() == CoreType.TEXT
            || attribute.getCoreType() == CoreType.MTEXT;
    }

    private String normalizedName(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String topicPath(String topicName, String modelName) {
        if (topicName == null || topicName.isBlank()) {
            return "";
        }
        if (modelName != null && !modelName.isBlank()
            && topicName.startsWith(modelName + ".")) {
            return topicName.substring(modelName.length() + 1);
        }
        return topicName;
    }

    private String label(ClassMetadata classMetadata, GenerationConfig config) {
        Map<String, String> labels = classMetadata.getLabels();
        if (labels != null && !labels.isEmpty()) {
            List<String> preferredLanguages = GenerationConfig.LANGUAGE_EN.equals(config.getLanguage())
                ? List.of("en", "de-CH", "de")
                : List.of("de-CH", "de", "en");
            for (String preferredLanguage : preferredLanguages) {
                String preferred = labels.get(preferredLanguage);
                if (preferred != null && !preferred.isBlank()) {
                    return preferred;
                }
            }
        }
        return classMetadata.getSimpleName();
    }

    private String resolveDefaultLabel(AttributeMetadata attribute) {
        if (attribute.getLabels().containsKey("de-CH")) {
            return attribute.getLabels().get("de-CH");
        }
        if (attribute.getLabels().containsKey("de")) {
            return attribute.getLabels().get("de");
        }
        if (attribute.getLabels().containsKey("en")) {
            return attribute.getLabels().get("en");
        }
        return attribute.getName();
    }

    private static RuntimeCoreType mapCoreType(CoreType coreType) {
        if (coreType == null) {
            return RuntimeCoreType.UNKNOWN;
        }
        return switch (coreType) {
            case TEXT -> RuntimeCoreType.TEXT;
            case MTEXT -> RuntimeCoreType.MTEXT;
            case NUMERIC -> RuntimeCoreType.NUMERIC;
            case BOOLEAN -> RuntimeCoreType.BOOLEAN;
            case DATE -> RuntimeCoreType.DATE;
            case DATETIME -> RuntimeCoreType.DATETIME;
            case TIME -> RuntimeCoreType.TIME;
            case ENUM -> RuntimeCoreType.ENUM;
            case COORD -> RuntimeCoreType.COORD;
            case POLYLINE -> RuntimeCoreType.POLYLINE;
            case SURFACE -> RuntimeCoreType.SURFACE;
            case REFERENCE -> RuntimeCoreType.REFERENCE;
            case COMPOSITION -> RuntimeCoreType.COMPOSITION;
            case OBJECT -> RuntimeCoreType.OBJECT;
            case UNKNOWN -> RuntimeCoreType.UNKNOWN;
        };
    }

    private static RuntimeCoreType mapCoreTypeName(String coreType) {
        if (coreType == null || coreType.isBlank()) {
            return RuntimeCoreType.UNKNOWN;
        }
        try {
            return RuntimeCoreType.valueOf(coreType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notRuntimeType) {
            return RuntimeCoreType.UNKNOWN;
        }
    }

    private static AssociationCreateMode mapCreateMode(ch.interlis.generator.grails.AssociationCreateMode mode) {
        return switch (mode) {
            case NONE -> AssociationCreateMode.NONE;
            case QUICK -> AssociationCreateMode.QUICK;
            case CONTEXTUAL_FORM -> AssociationCreateMode.CONTEXTUAL_FORM;
        };
    }

    private static ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind mapStorageKind(
        ch.interlis.generator.grails.AssociationStorageKind kind) {
        return switch (kind) {
            case LINK_ENTITY -> ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind.LINK_ENTITY;
            case EMBEDDED_FOREIGN_KEY -> ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind.EMBEDDED_FOREIGN_KEY;
            case UNMAPPED -> ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind.UNMAPPED;
        };
    }
}

package ch.interlis.generator.grails;

import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Grails-specific, deterministic classification of INTERLIS associations.
 *
 * <p>The planner reads the framework-agnostic core IR ({@link AssociationMetadata},
 * {@link AssociationRoleMetadata}) and combines it with the actual Grails domain
 * properties produced by {@link GrailsRelationshipMapper}. It never rebuilds ili2c
 * or ili2db reader logic and never creates inverse GORM collections.
 */
public final class GrailsAssociationPlanner {

    static final String DIAGNOSTIC_UNMAPPED_ASSOCIATION = "UNMAPPED_ASSOCIATION";
    static final String DIAGNOSTIC_EMBEDDED_FK_ASSOCIATION = "EMBEDDED_FK_ASSOCIATION";
    static final String DIAGNOSTIC_AMBIGUOUS_ROLE_PROPERTY = "AMBIGUOUS_ROLE_PROPERTY";
    static final String DIAGNOSTIC_ROLE_PROPERTY_NOT_FOUND = "ROLE_PROPERTY_NOT_FOUND";
    static final String DIAGNOSTIC_TARGET_DOMAIN_NOT_GENERATED = "TARGET_DOMAIN_NOT_GENERATED";
    static final String DIAGNOSTIC_MERGE_CONFIDENCE_NONE = "MERGE_CONFIDENCE_NONE";

    private final ModelMetadata metadata;
    private final GenerationConfig config;
    private final TargetNameRegistry registry;
    private final GrailsRelationshipMapper relationshipMapper;
    private final Set<String> associationClassNames;
    private final List<GrailsAssociationPlan> plans;

    private GrailsAssociationPlanner(ModelMetadata metadata,
                                     GenerationConfig config,
                                     TargetNameRegistry registry,
                                     GrailsRelationshipMapper relationshipMapper) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.config = Objects.requireNonNull(config, "config");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.relationshipMapper = Objects.requireNonNull(relationshipMapper, "relationshipMapper");
        this.associationClassNames = new LinkedHashSet<>();
        this.plans = buildPlans();
    }

    public static GrailsAssociationPlanner forMetadata(ModelMetadata metadata,
                                                       GenerationConfig config,
                                                       TargetNameRegistry registry,
                                                       GrailsRelationshipMapper relationshipMapper) {
        return new GrailsAssociationPlanner(metadata, config, registry, relationshipMapper);
    }

    public List<GrailsAssociationPlan> plans() {
        return plans;
    }

    public List<GrailsAssociationContextPlan> contextsForParticipant(String participantIliClassName) {
        if (participantIliClassName == null) {
            return List.of();
        }
        return plans.stream()
            .flatMap(plan -> plan.contexts().stream())
            .filter(context -> participantIliClassName.equals(context.participantIliClassName()))
            .sorted(Comparator.comparing(GrailsAssociationContextPlan::contextId,
                Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    public Optional<GrailsAssociationPlan> findPlan(String associationName) {
        if (associationName == null) {
            return Optional.empty();
        }
        return plans.stream()
            .filter(plan -> associationName.equals(plan.associationName()))
            .findFirst();
    }

    public boolean showDomainInNavigation(String iliClassName) {
        if (iliClassName == null) {
            return true;
        }
        return plans.stream()
            .filter(plan -> iliClassName.equals(plan.associationIliClassName()))
            .findFirst()
            .map(GrailsAssociationPlan::showInNavigation)
            .orElse(true);
    }

    public boolean isAssociationDomain(String iliClassName) {
        return iliClassName != null && associationClassNames.contains(iliClassName);
    }

    private List<GrailsAssociationPlan> buildPlans() {
        List<GrailsAssociationPlan> result = new ArrayList<>();
        metadata.getAllAssociations().stream()
            .sorted(Comparator.comparing(AssociationMetadata::getName, Comparator.nullsLast(String::compareTo)))
            .forEach(association -> {
                ClassMetadata associationClass = resolveAssociationClass(association);
                if (associationClass != null && associationClass.getKind() == ClassMetadata.ClassKind.ASSOCIATION) {
                    associationClassNames.add(associationClass.getName());
                }
                result.add(buildPlan(association));
            });
        return List.copyOf(result);
    }

    private GrailsAssociationPlan buildPlan(AssociationMetadata association) {
        List<String> diagnostics = new ArrayList<>();
        ClassMetadata associationClass = resolveAssociationClass(association);
        GrailsRelationshipMapper.DomainMapping domainMapping =
            associationClass != null && relationshipMapper.shouldGenerate(associationClass)
                ? relationshipMapper.map(associationClass)
                : null;

        AssociationStorageKind storageKind = resolveStorageKind(association, associationClass, domainMapping);
        if (storageKind == AssociationStorageKind.UNMAPPED) {
            diagnostics.add(DIAGNOSTIC_UNMAPPED_ASSOCIATION);
        }
        if (storageKind == AssociationStorageKind.EMBEDDED_FOREIGN_KEY) {
            diagnostics.add(DIAGNOSTIC_EMBEDDED_FK_ASSOCIATION);
        }

        List<GrailsAssociationRolePlan> roles = buildRolePlans(association, associationClass, domainMapping, diagnostics);
        List<GrailsAssociationAttributePlan> attributes =
            buildAttributePlans(association, associationClass, domainMapping, roles);
        List<GrailsAssociationContextPlan> contexts =
            buildContextPlans(association, roles, attributes, storageKind);

        boolean physicalMappingPresent = storageKind == AssociationStorageKind.LINK_ENTITY
            || storageKind == AssociationStorageKind.EMBEDDED_FOREIGN_KEY;
        boolean writable = contexts.stream().anyMatch(GrailsAssociationContextPlan::writable);
        boolean showInNavigation = !(physicalMappingPresent && !contexts.isEmpty());

        String domainClassName = associationClass != null ? registry.className(associationClass) : null;
        String domainQualifiedName = domainClassName != null
            ? registry.domainPackage() + "." + domainClassName
            : null;
        String controllerName = associationClass != null ? registry.viewPath(associationClass) : null;
        String viewPath = controllerName;

        return new GrailsAssociationPlan(
            association.getName(),
            association.getAssociationClass(),
            domainClassName,
            domainQualifiedName,
            controllerName,
            viewPath,
            association.getPhysicalTable(),
            association.getPhysicalSqlName(),
            storageKind,
            physicalMappingPresent,
            writable,
            showInNavigation,
            roles,
            attributes,
            contexts,
            diagnostics
        );
    }

    private ClassMetadata resolveAssociationClass(AssociationMetadata association) {
        ClassMetadata associationClass = association.getAssociationClass() != null
            ? metadata.getClass(association.getAssociationClass())
            : null;
        if (associationClass == null) {
            associationClass = metadata.getClass(association.getName());
        }
        return associationClass;
    }

    private AssociationStorageKind resolveStorageKind(AssociationMetadata association,
                                                       ClassMetadata associationClass,
                                                       GrailsRelationshipMapper.DomainMapping domainMapping) {
        if (associationClass == null) {
            return AssociationStorageKind.UNMAPPED;
        }
        if (domainMapping != null && relationshipMapper.shouldGenerate(associationClass)) {
            boolean physicallyMapped = notBlank(association.getPhysicalTable())
                || notBlank(association.getPhysicalSqlName())
                || notBlank(associationClass.getTableName())
                || notBlank(associationClass.getSqlName());
            return physicallyMapped ? AssociationStorageKind.LINK_ENTITY : AssociationStorageKind.UNMAPPED;
        }
        if (associationClass.getKind() == ClassMetadata.ClassKind.ASSOCIATION) {
            return AssociationStorageKind.EMBEDDED_FOREIGN_KEY;
        }
        return AssociationStorageKind.UNMAPPED;
    }

    private List<GrailsAssociationRolePlan> buildRolePlans(AssociationMetadata association,
                                                           ClassMetadata associationClass,
                                                           GrailsRelationshipMapper.DomainMapping domainMapping,
                                                           List<String> diagnostics) {
        List<GrailsAssociationRolePlan> roles = new ArrayList<>();
        for (AssociationRoleMetadata role : association.getRoles()) {
            String propertyName = resolveRoleProperty(association, role, domainMapping, diagnostics);

            ClassMetadata targetClass = role.getTargetClass() != null
                ? metadata.getClass(role.getTargetClass())
                : null;
            boolean targetGenerated = targetClass != null && relationshipMapper.shouldGenerate(targetClass);
            if (!targetGenerated && role.getTargetClass() != null) {
                diagnostics.add(DIAGNOSTIC_TARGET_DOMAIN_NOT_GENERATED + ":" + role.getName());
            }
            String targetDomainClassName = role.getTargetClass() != null
                ? registry.className(role.getTargetClass())
                : null;
            String targetDomainQualifiedName = targetGenerated
                ? registry.domainPackage() + "." + targetDomainClassName
                : null;

            if (role.getMergeConfidence() == RelationshipMetadata.MergeConfidence.NONE) {
                diagnostics.add(DIAGNOSTIC_MERGE_CONFIDENCE_NONE + ":" + role.getName());
                propertyName = null;
            }

            ch.interlis.generator.model.Cardinality cardinality = role.getCardinality();
            Integer min = cardinality != null ? cardinality.minTarget() : null;
            Integer max = cardinality != null ? cardinality.maxTarget() : null;

            roles.add(new GrailsAssociationRolePlan(
                role.getName(),
                roleLabel(role),
                propertyName,
                role.getTargetClass(),
                targetDomainClassName,
                targetDomainQualifiedName,
                min,
                max,
                role.isMandatory(),
                role.isOrdered(),
                role.isExternal(),
                role.isComposition(),
                role.getPhysicalName(),
                role.getSemanticName()
            ));
        }
        return roles;
    }

    private String resolveRoleProperty(AssociationMetadata association,
                                       AssociationRoleMetadata role,
                                       GrailsRelationshipMapper.DomainMapping domainMapping,
                                       List<String> diagnostics) {
        if (domainMapping == null) {
            return null;
        }
        List<GrailsRelationshipMapper.DomainProperty> roleProperties = domainMapping.properties().stream()
            .filter(property -> property.relationship() != null)
            .filter(property -> property.relationship().getSemanticKind()
                == RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE)
            .filter(property -> matchesAssociation(association, property.relationship()))
            .toList();

        // 1. exact match by target role name
        List<GrailsRelationshipMapper.DomainProperty> byRoleName = roleProperties.stream()
            .filter(property -> equalsIgnoreCase(property.relationship().getTargetRoleName(), role.getName()))
            .toList();
        Optional<String> resolved = single(byRoleName, association, role, diagnostics);
        if (resolved != null) {
            return resolved.orElse(null);
        }

        // 2. fallback by physical name
        if (notBlank(role.getPhysicalName())) {
            List<GrailsRelationshipMapper.DomainProperty> byPhysical = roleProperties.stream()
                .filter(property -> equalsIgnoreCase(property.relationship().getPhysicalName(), role.getPhysicalName()))
                .toList();
            resolved = single(byPhysical, association, role, diagnostics);
            if (resolved != null) {
                return resolved.orElse(null);
            }
        }

        // 3. fallback by source attribute
        if (notBlank(role.getSourceAttribute())) {
            List<GrailsRelationshipMapper.DomainProperty> bySource = roleProperties.stream()
                .filter(property -> equalsIgnoreCase(property.relationship().getSourceAttribute(),
                    role.getSourceAttribute()))
                .toList();
            resolved = single(bySource, association, role, diagnostics);
            if (resolved != null) {
                return resolved.orElse(null);
            }
        }

        // 4. fallback by semantic name
        if (notBlank(role.getSemanticName())) {
            List<GrailsRelationshipMapper.DomainProperty> bySemantic = roleProperties.stream()
                .filter(property -> equalsIgnoreCase(property.relationship().getSemanticName(), role.getSemanticName()))
                .toList();
            resolved = single(bySemantic, association, role, diagnostics);
            if (resolved != null) {
                return resolved.orElse(null);
            }
        }

        // 5. fallback by target class when the role name is unique for that target
        if (notBlank(role.getTargetClass())) {
            List<GrailsRelationshipMapper.DomainProperty> byTarget = roleProperties.stream()
                .filter(property -> equalsIgnoreCase(property.relationship().getTargetClass(), role.getTargetClass()))
                .toList();
            resolved = single(byTarget, association, role, diagnostics);
            if (resolved != null) {
                return resolved.orElse(null);
            }
        }

        diagnostics.add(DIAGNOSTIC_ROLE_PROPERTY_NOT_FOUND + ":" + role.getName());
        return null;
    }

    /**
     * @return {@code null} when the candidate list is empty (caller should try the next fallback),
     *         an empty optional when the candidates are ambiguous (diagnostic recorded),
     *         or the resolved property name.
     */
    private Optional<String> single(List<GrailsRelationshipMapper.DomainProperty> candidates,
                                    AssociationMetadata association,
                                    AssociationRoleMetadata role,
                                    List<String> diagnostics) {
        if (candidates.isEmpty()) {
            return null;
        }
        List<String> distinctNames = candidates.stream()
            .map(GrailsRelationshipMapper.DomainProperty::name)
            .distinct()
            .toList();
        if (distinctNames.size() == 1) {
            return Optional.of(distinctNames.get(0));
        }
        diagnostics.add(DIAGNOSTIC_AMBIGUOUS_ROLE_PROPERTY + ":" + role.getName());
        return Optional.empty();
    }

    private boolean matchesAssociation(AssociationMetadata association, RelationshipMetadata relationship) {
        if (notBlank(relationship.getAssociationName())) {
            return equalsIgnoreCase(relationship.getAssociationName(), association.getName());
        }
        return true;
    }

    private List<GrailsAssociationAttributePlan> buildAttributePlans(AssociationMetadata association,
                                                                     ClassMetadata associationClass,
                                                                     GrailsRelationshipMapper.DomainMapping domainMapping,
                                                                     List<GrailsAssociationRolePlan> roles) {
        Set<String> rolePropertyNames = roles.stream()
            .map(GrailsAssociationRolePlan::domainPropertyName)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<GrailsAssociationAttributePlan> attributes = new ArrayList<>();
        for (AttributeMetadata attribute : association.getAllAttributes()) {
            if (attribute.isPrimaryKey() || attribute.isForeignKey()) {
                continue;
            }
            String propertyName = resolveAttributeProperty(associationClass, attribute, domainMapping);
            if (propertyName != null && rolePropertyNames.contains(propertyName)) {
                continue;
            }
            String javaType = resolveAttributeType(attribute, domainMapping, propertyName);
            attributes.add(new GrailsAssociationAttributePlan(
                attribute.getName(),
                propertyName,
                javaType,
                attribute.getCoreType() != null ? attribute.getCoreType().name() : null,
                resolveAttributeLabel(attribute),
                attribute.getDocumentation(),
                attribute.getUnit(),
                attribute.isMandatory(),
                attribute.getMaxLength(),
                attribute.getMinValue(),
                attribute.getMaxValue(),
                attribute.getPrecision(),
                attribute.getScale(),
                attribute.isGeometry(),
                attribute.getGeometryKind(),
                attribute.getGeometrySrid(),
                resolveEnumType(attribute)
            ));
        }
        return attributes;
    }

    private String resolveAttributeProperty(ClassMetadata associationClass,
                                            AttributeMetadata attribute,
                                            GrailsRelationshipMapper.DomainMapping domainMapping) {
        if (domainMapping != null) {
            Optional<GrailsRelationshipMapper.DomainProperty> match = domainMapping.properties().stream()
                .filter(property -> property.attribute() != null)
                .filter(property -> equalsIgnoreCase(property.attribute().getName(), attribute.getName())
                    || equalsIgnoreCase(property.columnName(), attribute.getColumnName()))
                .findFirst();
            if (match.isPresent()) {
                return match.get().name();
            }
        }
        if (associationClass != null) {
            return registry.propertyName(associationClass, attribute);
        }
        return null;
    }

    private String resolveAttributeType(AttributeMetadata attribute,
                                        GrailsRelationshipMapper.DomainMapping domainMapping,
                                        String propertyName) {
        if (domainMapping != null && propertyName != null) {
            Optional<String> type = domainMapping.properties().stream()
                .filter(property -> propertyName.equals(property.name()))
                .map(GrailsRelationshipMapper.DomainProperty::type)
                .findFirst();
            if (type.isPresent()) {
                return type.get();
            }
        }
        String enumType = resolveEnumType(attribute);
        if (enumType != null) {
            return enumType;
        }
        return NameUtils.simpleType(attribute.getJavaType());
    }

    private String resolveEnumType(AttributeMetadata attribute) {
        if (attribute.getEnumType() == null) {
            return null;
        }
        EnumMetadata enumMetadata = metadata.getEnums().get(attribute.getEnumType());
        return enumMetadata != null ? registry.enumName(enumMetadata) : null;
    }

    private List<GrailsAssociationContextPlan> buildContextPlans(AssociationMetadata association,
                                                                 List<GrailsAssociationRolePlan> roles,
                                                                 List<GrailsAssociationAttributePlan> attributes,
                                                                 AssociationStorageKind storageKind) {
        List<GrailsAssociationContextPlan> contexts = new ArrayList<>();
        boolean nary = roles.size() >= 3;
        AssociationCreateMode createMode = resolveCreateMode(roles, attributes, storageKind);

        for (GrailsAssociationRolePlan fixedRole : roles) {
            List<GrailsAssociationRolePlan> otherRoles = roles.stream()
                .filter(role -> role != fixedRole)
                .toList();

            List<String> diagnostics = new ArrayList<>();
            boolean rolesResolved = roles.stream().allMatch(GrailsAssociationRolePlan::hasResolvedProperty);
            if (!rolesResolved) {
                diagnostics.add(DIAGNOSTIC_ROLE_PROPERTY_NOT_FOUND);
            }

            boolean writable = storageKind == AssociationStorageKind.LINK_ENTITY
                && rolesResolved
                && fixedRole.hasResolvedProperty();

            AssociationCreateMode contextCreateMode = writable ? createMode : AssociationCreateMode.NONE;
            AssociationPresentationKind presentationKind = resolvePresentationKind(
                fixedRole, otherRoles, attributes, storageKind);
            if (!writable && presentationKind != AssociationPresentationKind.READ_ONLY) {
                presentationKind = AssociationPresentationKind.READ_ONLY;
            }

            GrailsAssociationRolePlan perspectiveRole = otherRoles.size() == 1 ? otherRoles.get(0) : null;
            Integer perspectiveMin = !nary && perspectiveRole != null ? perspectiveRole.minCardinality() : null;
            Integer perspectiveMax = !nary && perspectiveRole != null ? perspectiveRole.maxCardinality() : null;

            contexts.add(new GrailsAssociationContextPlan(
                contextId(association, fixedRole.roleName()),
                contextMessageCode(association, fixedRole.roleName()),
                defaultContextLabel(fixedRole, otherRoles),
                fixedRole.targetIliClassName(),
                fixedRole.targetDomainClassName(),
                fixedRole.targetDomainQualifiedName(),
                fixedRole.roleName(),
                fixedRole.domainPropertyName(),
                otherRoles.stream().map(GrailsAssociationRolePlan::roleName).toList(),
                otherRoles.stream()
                    .map(GrailsAssociationRolePlan::domainPropertyName)
                    .filter(Objects::nonNull)
                    .toList(),
                perspectiveMin,
                perspectiveMax,
                presentationKind,
                contextCreateMode,
                writable,
                writable,
                storageKind != AssociationStorageKind.UNMAPPED,
                diagnostics
            ));
        }
        return contexts;
    }

    private AssociationPresentationKind resolvePresentationKind(GrailsAssociationRolePlan fixedRole,
                                                                List<GrailsAssociationRolePlan> otherRoles,
                                                                List<GrailsAssociationAttributePlan> attributes,
                                                                AssociationStorageKind storageKind) {
        if (storageKind != AssociationStorageKind.LINK_ENTITY) {
            return AssociationPresentationKind.READ_ONLY;
        }
        if (otherRoles.size() >= 2) {
            return AssociationPresentationKind.NARY_CONTEXTUAL_FORM;
        }
        if (!attributes.isEmpty()) {
            return AssociationPresentationKind.CONTEXTUAL_FORM;
        }
        GrailsAssociationRolePlan perspectiveRole = otherRoles.size() == 1 ? otherRoles.get(0) : null;
        if (perspectiveRole != null && perspectiveRole.isToOne()) {
            return AssociationPresentationKind.RELATED_TO_ONE;
        }
        return AssociationPresentationKind.RELATED_LIST;
    }

    private AssociationCreateMode resolveCreateMode(List<GrailsAssociationRolePlan> roles,
                                                    List<GrailsAssociationAttributePlan> attributes,
                                                    AssociationStorageKind storageKind) {
        if (isQuickLinkEligible(roles, attributes, storageKind)) {
            return AssociationCreateMode.QUICK;
        }
        boolean rolesResolved = roles.stream().allMatch(GrailsAssociationRolePlan::hasResolvedProperty);
        if (storageKind == AssociationStorageKind.LINK_ENTITY && rolesResolved && !roles.isEmpty()) {
            return AssociationCreateMode.CONTEXTUAL_FORM;
        }
        return AssociationCreateMode.NONE;
    }

    private boolean isQuickLinkEligible(List<GrailsAssociationRolePlan> roles,
                                        List<GrailsAssociationAttributePlan> attributes,
                                        AssociationStorageKind storageKind) {
        if (storageKind != AssociationStorageKind.LINK_ENTITY) {
            return false;
        }
        if (roles.size() != 2) {
            return false;
        }
        if (!attributes.isEmpty()) {
            return false;
        }
        for (GrailsAssociationRolePlan role : roles) {
            if (!role.hasResolvedProperty()) {
                return false;
            }
            if (role.targetDomainQualifiedName() == null) {
                return false;
            }
            if (role.ordered() || role.composition() || role.external()) {
                return false;
            }
        }
        return true;
    }

    private String defaultContextLabel(GrailsAssociationRolePlan fixedRole,
                                       List<GrailsAssociationRolePlan> otherRoles) {
        if (otherRoles.size() == 1) {
            GrailsAssociationRolePlan other = otherRoles.get(0);
            String base = other.targetDomainClassName() != null
                ? other.targetDomainClassName()
                : other.roleLabel();
            if (base == null) {
                return fixedRole.roleLabel();
            }
            return other.isToMany() ? NameUtils.pluralize(base) : base;
        }
        return fixedRole.roleLabel();
    }

    private String contextId(AssociationMetadata association, String fixedRoleName) {
        String raw = association.getName() + "::" + fixedRoleName;
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("%3A%3A", "::");
    }

    private String contextMessageCode(AssociationMetadata association, String fixedRoleName) {
        return "interlis.association."
            + normalizeToken(association.getName())
            + "."
            + lowerCamel(fixedRoleName)
            + ".label";
    }

    private String roleLabel(AssociationRoleMetadata role) {
        if (notBlank(role.getSemanticName())) {
            return role.getSemanticName();
        }
        return role.getName();
    }

    private String resolveAttributeLabel(AttributeMetadata attribute) {
        Map<String, String> labels = attribute.getLabels();
        if (labels != null) {
            if (labels.containsKey("de-CH")) {
                return labels.get("de-CH");
            }
            if (labels.containsKey("de")) {
                return labels.get("de");
            }
            if (labels.containsKey("en")) {
                return labels.get("en");
            }
        }
        return attribute.getName();
    }

    private String normalizeToken(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return "association";
        }
        String[] segments = qualifiedName.split("[^A-Za-z0-9]+");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            if (builder.length() == 0) {
                builder.append(lowerCamel(segment));
            } else {
                builder.append(Character.toUpperCase(segment.charAt(0)))
                    .append(segment.substring(1));
            }
        }
        return builder.length() == 0 ? "association" : builder.toString();
    }

    private String lowerCamel(String value) {
        if (value == null || value.isBlank()) {
            return "role";
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

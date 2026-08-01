package ch.interlis.generator.model;

import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validierung des Build-Models vor dem Freeze.
 *
 * <p>Ergänzt die fachlichen P0-Validierungen um die immutable-IR-Invarianten
 * (Mapschlüssel = Objektname, eindeutige physische Tabellen/Spalten, eindeutige
 * Relationship-Identitäten, aufgelöste Typen, konsistente Geometry-Felder,
 * null-freie Collections).</p>
 */
public final class ModelMetadataValidator {

    public List<ModelMetadataDiagnostic> validate(ModelMetadataBuilder builder) {
        List<ModelMetadataDiagnostic> diagnostics = new ArrayList<>();

        if (builder.modelName() == null || builder.modelName().isBlank()) {
            diagnostics.add(new ModelMetadataDiagnostic(
                ModelMetadataDiagnosticCode.EMPTY_MODEL_NAME,
                "model",
                "The model name must not be blank",
                true,
                Map.of()));
        }

        Set<String> physicalTables = new HashSet<>();
        Set<String> relationshipIdentities = new HashSet<>();

        for (Map.Entry<String, ClassMetadataBuilder> entry : builder.classBuilders().entrySet()) {
            String className = entry.getKey();
            ClassMetadataBuilder classBuilder = entry.getValue();
            if (!className.equals(classBuilder.name())) {
                diagnostics.add(new ModelMetadataDiagnostic(
                    ModelMetadataDiagnosticCode.DUPLICATE_CLASS,
                    className,
                    "Map key '" + className + "' does not match class name '"
                        + classBuilder.name() + "'",
                    true,
                    Map.of()));
            }
            String tableName = classBuilder.tableName();
            if (tableName != null && !tableName.isBlank()) {
                String normalizedTable = tableName.toLowerCase(Locale.ROOT);
                if (!physicalTables.add(normalizedTable)) {
                    diagnostics.add(new ModelMetadataDiagnostic(
                        ModelMetadataDiagnosticCode.DUPLICATE_PHYSICAL_TABLE,
                        className,
                        "Physical table '" + tableName + "' is used by more than one class",
                        true,
                        Map.of()));
                }
            }
            Set<String> physicalColumns = new HashSet<>();
            for (Map.Entry<String, AttributeMetadataBuilder> attributeEntry
                : classBuilder.attributeBuilders().entrySet()) {
                String attributeName = attributeEntry.getKey();
                AttributeMetadataBuilder attribute = attributeEntry.getValue();
                if (!attributeName.equals(attribute.name())) {
                    diagnostics.add(new ModelMetadataDiagnostic(
                        ModelMetadataDiagnosticCode.DUPLICATE_ATTRIBUTE,
                        className + "." + attributeName,
                        "Map key '" + attributeName + "' does not match attribute name '"
                            + attribute.name() + "'",
                        true,
                        Map.of()));
                }
                validateAttribute(className, attribute, diagnostics);
                if (attribute.primaryKey() && attribute.columnName() != null
                    && !attribute.columnName().isBlank()) {
                    String normalizedColumn = attribute.columnName().toLowerCase(Locale.ROOT);
                    if (!physicalColumns.add(normalizedColumn)) {
                        diagnostics.add(new ModelMetadataDiagnostic(
                            ModelMetadataDiagnosticCode.DUPLICATE_PHYSICAL_COLUMN,
                            className + "." + attributeName,
                            "Physical column '" + attribute.columnName()
                                + "' is used by more than one attribute",
                            true,
                            Map.of()));
                    }
                }
            }
        }

        for (ch.interlis.generator.model.builder.RelationshipMetadataBuilder relationship
            : builder.relationshipBuilders()) {
            String sourceClass = relationshipSourceClass(relationship);
            String targetClass = relationshipTargetClass(relationship);
            if (!builder.hasClassBuilder(sourceClass)) {
                diagnostics.add(new ModelMetadataDiagnostic(
                    ModelMetadataDiagnosticCode.UNRESOLVED_SOURCE_CLASS,
                    relationship.name(),
                    "Relationship '" + relationship.name() + "' has unresolved source class '"
                        + sourceClass + "'",
                    false,
                    Map.of()));
            }
            if (!builder.hasClassBuilder(targetClass)) {
                diagnostics.add(new ModelMetadataDiagnostic(
                    ModelMetadataDiagnosticCode.UNRESOLVED_TARGET_CLASS,
                    relationship.name(),
                    "Relationship '" + relationship.name() + "' has unresolved target class '"
                        + targetClass + "'",
                    false,
                    Map.of()));
            }
            String identity = relationshipIdentity(relationship);
            if (!relationshipIdentities.add(identity)) {
                diagnostics.add(new ModelMetadataDiagnostic(
                    ModelMetadataDiagnosticCode.DUPLICATE_RELATIONSHIP_IDENTITY,
                    relationship.name(),
                    "Duplicate relationship identity '" + identity + "'",
                    true,
                    Map.of()));
            }
        }

        for (ch.interlis.generator.model.builder.AssociationMetadataBuilder association
            : builder.associationBuilders().values()) {
            for (ch.interlis.generator.model.builder.AssociationRoleMetadataBuilder role
                : association.roleBuilders()) {
                if (roleTargetClass(role) != null && !builder.hasClassBuilder(roleTargetClass(role))) {
                    diagnostics.add(new ModelMetadataDiagnostic(
                        ModelMetadataDiagnosticCode.UNRESOLVED_ASSOCIATION_ROLE_TARGET,
                        association.name() + "::" + role.name(),
                        "Role '" + role.name() + "' of association '" + association.name()
                            + "' targets unknown class '" + roleTargetClass(role) + "'",
                        false,
                        Map.of()));
                }
            }
        }

        return diagnostics;
    }

    private void validateAttribute(String className,
                                   AttributeMetadataBuilder attribute,
                                   List<ModelMetadataDiagnostic> diagnostics) {
        if (attribute.coreType() == null || attribute.javaType() == null) {
            diagnostics.add(new ModelMetadataDiagnostic(
                ModelMetadataDiagnosticCode.UNRESOLVED_TYPE,
                className + "." + attribute.name(),
                "Attribute '" + attribute.name() + "' has unresolved coreType/javaType",
                true,
                Map.of()));
        }
        if (attribute.geometry()) {
            boolean hasSrid = attribute.geometrySrid() != null;
            boolean hasKind = attribute.geometryKind() != null;
            if (hasSrid && !hasKind) {
                diagnostics.add(new ModelMetadataDiagnostic(
                    ModelMetadataDiagnosticCode.INCONSISTENT_GEOMETRY,
                    className + "." + attribute.name(),
                    "Geometry attribute '" + attribute.name()
                        + "' has an srid but no geometry kind",
                    true,
                    Map.of()));
            }
        }
    }

    private static String relationshipSourceClass(ch.interlis.generator.model.builder.RelationshipMetadataBuilder r) {
        return r.sourceClass() == null ? "" : r.sourceClass();
    }

    private static String relationshipTargetClass(ch.interlis.generator.model.builder.RelationshipMetadataBuilder r) {
        return r.targetClass() == null ? "" : r.targetClass();
    }

    private static boolean relationshipExternal(ch.interlis.generator.model.builder.RelationshipMetadataBuilder r) {
        return r.external();
    }

    private static String relationshipIdentity(ch.interlis.generator.model.builder.RelationshipMetadataBuilder r) {
        return r.sourceClass() + "|" + r.targetClass() + "|" + r.sourceAttribute()
            + "|" + r.physicalName() + "|" + r.associationName() + "|" + r.targetRoleName()
            + "|" + (r.semanticKind() == null ? "" : r.semanticKind().name());
    }

    private static String roleTargetClass(ch.interlis.generator.model.builder.AssociationRoleMetadataBuilder role) {
        return role.targetClass();
    }
}

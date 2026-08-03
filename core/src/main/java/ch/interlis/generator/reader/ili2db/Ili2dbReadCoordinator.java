package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.reader.ili2db.assemble.Ili2dbAssociationDeriver;
import ch.interlis.generator.reader.ili2db.assemble.Ili2dbMetadataAssembler;
import ch.interlis.generator.reader.ili2db.assemble.Ili2dbRelationshipDeriver;
import ch.interlis.generator.reader.ili2db.catalog.ClassMappingRow;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogCapabilities;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogReader;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogSnapshot;
import ch.interlis.generator.reader.ili2db.catalog.ModelRow;
import ch.interlis.generator.reader.ili2db.schema.DatabaseDialect;
import ch.interlis.generator.reader.ili2db.schema.DefaultJdbcSchemaIntrospector;
import ch.interlis.generator.reader.ili2db.schema.GeometryIntrospectorFactory;
import ch.interlis.generator.reader.ili2db.schema.GeometrySchemaSnapshot;
import ch.interlis.generator.reader.ili2db.schema.JdbcSchemaIntrospector;
import ch.interlis.generator.reader.ili2db.schema.JdbcSchemaSnapshot;
import ch.interlis.generator.reader.ili2db.schema.SqliteSchemaIntrospector;
import ch.interlis.generator.reader.sql.QualifiedSqlName;
import ch.interlis.generator.reader.sql.SqlIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Orchestriert einen ili2db-Lesedurchgang: Katalog lesen (typed Rows),
 * Schema/Geometry introspektieren (immutable Snapshots, tabellen- und
 * batchweise), Enum-Werte lesen und die IR über den Assembler bauen.
 * Die Phasen bleiben strikt getrennt; der Assembler ist die einzige Stelle,
 * die IR-Builder erzeugt.
 *
 * <p>Die fachliche Auswahl und das tatsächlich gewünschte Geometry-Flag
 * werden direkt übergeben; die technische Umgebung bleibt im Context.</p>
 */
public final class Ili2dbReadCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(Ili2dbReadCoordinator.class);

    private final Ili2dbCatalogReader catalogReader = new Ili2dbCatalogReader();
    private final Ili2dbMetadataAssembler assembler = new Ili2dbMetadataAssembler();
    private final Ili2dbRelationshipDeriver relationshipDeriver = new Ili2dbRelationshipDeriver();
    private final Ili2dbAssociationDeriver associationDeriver = new Ili2dbAssociationDeriver();
    private final ModelMetadataFactory metadataFactory = new ModelMetadataFactory();

    public Ili2dbReadResult read(Ili2dbReadContext context,
                                 ModelSelection selection,
                                 boolean includeGeometryMetadata) throws SQLException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(selection, "selection");
        List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();

        Ili2dbCatalogCapabilities capabilities =
            catalogReader.detectCapabilities(context, diagnostics);
        Map<String, String> settings = catalogReader.readSettings(context, capabilities, diagnostics);
        List<ModelRow> models = catalogReader.readModels(context, capabilities, diagnostics);

        Set<String> effectiveModels = effectiveModelNames(selection, models, diagnostics);
        if (effectiveModels.isEmpty()) {
            // Das Root-Modell fehlt; die FATAL-Diagnostic ist bereits erfasst.
            return new Ili2dbReadResult(null, null, JdbcSchemaSnapshot.empty(),
                GeometrySchemaSnapshot.empty(), diagnostics);
        }

        List<ClassMappingRow> classes = catalogReader.readClasses(
            context, capabilities, diagnostics, effectiveModels);
        List<String> tableNames = classes.stream()
            .map(ClassMappingRow::tableName)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .toList();

        Ili2dbCatalogSnapshot catalog = new Ili2dbCatalogSnapshot(
            settings,
            models,
            classes,
            catalogReader.readAttributes(context, capabilities, diagnostics,
                effectiveModels, tableNames),
            catalogReader.readInheritance(context, capabilities, diagnostics, effectiveModels),
            catalogReader.readColumnProperties(context, capabilities, diagnostics),
            catalogReader.readEnumDomains(context, capabilities, diagnostics),
            capabilities
        );

        JdbcSchemaSnapshot schema = inspectSchema(context, tableNames, diagnostics);
        GeometrySchemaSnapshot geometry = inspectGeometry(
            context, includeGeometryMetadata, tableNames, diagnostics);

        ModelMetadataBuilder builder = assembler.assemble(context, catalog, schema, geometry,
            selection, diagnostics);
        relationshipDeriver.derive(builder);
        associationDeriver.derive(builder, diagnostics);

        ModelMetadata metadata = metadataFactory.buildValidated(builder);
        logger.info("Metadata reading complete: {} classes, {} enums",
            metadata.getClasses().size(), metadata.getEnums().size());

        return new Ili2dbReadResult(metadata, catalog, schema, geometry, diagnostics);
    }

    /**
     * Effektive Modellnamen = Auswahl ∩ verfügbare DB-Modelle.
     *
     * <ul>
     *   <li>Root-Modell fehlt in der Datenbank: {@code REQUESTED_MODEL_MISSING}
     *       als FATAL-Diagnostic (keine {@code IllegalArgumentException}).</li>
     *   <li>Benötigte Dependency fehlt: {@code SELECTED_DEPENDENCY_MISSING}
     *       als WARNING-Diagnostic (nicht nur loggen).</li>
     *   <li>Unabhängige DB-Modelle werden nie hinzugefügt.</li>
     *   <li>Wenn die Metatabelle nicht lesbar ist (leere Verfügbarkeitsmenge),
     *       wird Root-only gelesen (WARNING).</li>
     * </ul>
     */
    private Set<String> effectiveModelNames(ModelSelection selection,
                                            List<ModelRow> databaseModels,
                                            List<Ili2dbDiagnostic> diagnostics) {
        Set<String> available = new LinkedHashSet<>();
        for (ModelRow row : databaseModels) {
            if (row.modelName() != null && !row.modelName().isBlank()) {
                available.add(row.modelName());
            }
        }
        if (available.isEmpty()) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.OPTIONAL_META_TABLE_MISSING,
                "t_ili2db_model is not readable; dependency graph not verifiable, "
                    + "reading root model only",
                null, "t_ili2db_model", Map.of()));
            return new LinkedHashSet<>(Set.of(selection.rootModelName()));
        }
        if (!available.contains(selection.rootModelName())) {
            List<String> sortedAvailable = new TreeSet<>(available).stream().limit(50).toList();
            Map<String, String> details = new LinkedHashMap<>();
            details.put("requestedModel", selection.rootModelName());
            details.put("availableModelCount", String.valueOf(available.size()));
            details.put("availableModels", String.join(", ", sortedAvailable));
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.FATAL,
                Ili2dbDiagnosticCode.REQUESTED_MODEL_MISSING,
                "Root model not found in t_ili2db_model: " + selection.rootModelName(),
                selection.rootModelName(), "t_ili2db_model", details));
            return Set.of();
        }
        Set<String> effective = new LinkedHashSet<>();
        for (String name : selection.includedModelNames()) {
            if (available.contains(name)) {
                effective.add(name);
            } else {
                Map<String, String> details = new LinkedHashMap<>();
                details.put("rootModel", selection.rootModelName());
                details.put("missingDependency", name);
                details.put("selectedModelCount", String.valueOf(selection.includedModelNames().size()));
                diagnostics.add(new Ili2dbDiagnostic(
                    Ili2dbSeverity.WARNING,
                    Ili2dbDiagnosticCode.SELECTED_DEPENDENCY_MISSING,
                    "Selected model " + name + " is not present in t_ili2db_model; skipping.",
                    name, "t_ili2db_model", details));
            }
        }
        return effective;
    }

    // ------------------------------------------------------------------
    // Schema / Geometry
    // ------------------------------------------------------------------

    private JdbcSchemaSnapshot inspectSchema(Ili2dbReadContext context,
                                             List<String> tableNames,
                                             List<Ili2dbDiagnostic> diagnostics) throws SQLException {
        if (context.dialect() == DatabaseDialect.OTHER) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.DATABASE_DIALECT_UNSUPPORTED,
                "Database dialect is not recognized; generic JDBC introspection is used",
                null, null, Map.of()));
        }
        List<QualifiedSqlName> tables = new ArrayList<>();
        for (String tableName : tableNames) {
            tables.add(new QualifiedSqlName(context.schema(), SqlIdentifier.discovered(tableName)));
        }
        JdbcSchemaIntrospector introspector = context.dialect() == DatabaseDialect.SQLITE
            ? new SqliteSchemaIntrospector()
            : new DefaultJdbcSchemaIntrospector();
        return introspector.inspect(context, tables);
    }

    private GeometrySchemaSnapshot inspectGeometry(Ili2dbReadContext context,
                                                   boolean includeGeometryMetadata,
                                                   List<String> tableNames,
                                                   List<Ili2dbDiagnostic> diagnostics)
        throws SQLException {
        if (!includeGeometryMetadata) {
            return GeometrySchemaSnapshot.empty();
        }
        List<QualifiedSqlName> tables = new ArrayList<>();
        for (String tableName : tableNames) {
            tables.add(new QualifiedSqlName(context.schema(), SqlIdentifier.discovered(tableName)));
        }
        GeometrySchemaSnapshot snapshot = new GeometryIntrospectorFactory()
            .forDialect(context.dialect())
            .inspect(context, tables);
        if (!snapshot.metadataAvailable()) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.GEOMETRY_METADATA_UNAVAILABLE,
                "Geometry metadata could not be read; geometry columns fall back to generic kind",
                null, null, Map.of()));
        }
        return snapshot;
    }
}

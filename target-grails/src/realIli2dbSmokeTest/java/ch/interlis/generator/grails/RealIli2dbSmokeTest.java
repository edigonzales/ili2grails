package ch.interlis.generator.grails;

import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RealIli2dbSmokeTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret";
    private static final List<String> MODEL_REPOSITORIES = List.of(
        "test-models",
        "https://models.interlis.ch/",
        "https://models.geo.admin.ch/"
    );
    private static final Path CORE_IR_MODEL_FILE = Path.of("test-models/CoreIrTestModel.ili");
    private static final Path STRUCTURE_COMPOSITION_MODEL_FILE = Path.of(
        "test-models/StructureCompositionCases.ili"
    );
    private static final Path VSADSSMINI_MODEL_FILE = Path.of(
        "test-models/VSADSSMINI_2020_2_d_LV95-20251129.ili"
    );
    private static final Path REPORT_DIR = Path.of("build/reports/real-ili2db-smoke");
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(8);
    private static final Duration DB_TIMEOUT = Duration.ofSeconds(90);

    @TempDir
    Path tempDir;

    @Test
    void validatesCoreIrStructureCompositionAgainstRealIli2pgSchema() throws Exception {
        Path jsonReport = REPORT_DIR.resolve("structure-composition-summary.json");
        Path markdownReport = REPORT_DIR.resolve("structure-composition-summary.md");
        deleteReports(jsonReport, markdownReport);

        RealSchemaMetadata realSchema = importAndReadMetadata(
            "CoreIrTestModel",
            CORE_IR_MODEL_FILE,
            "rt_coreir_"
        );

        GenerationConfig config = GenerationConfig.builder(tempDir.resolve("coreir-generated"), "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(realSchema.metadata(), config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(realSchema.metadata(), config, registry);
        StructureSummary summary = summarize(
            realSchema.modelName(),
            realSchema.schemaName(),
            realSchema.metadata(),
            registry,
            mapper
        );
        writeSummary(
            jsonReport,
            markdownReport,
            summary
        );

        assertThat(summary.structureCount())
            .as("CoreIrTestModel should exercise STRUCTURE handling in a real ili2pg schema")
            .isGreaterThan(0);
        assertThat(summary.compositionTargetCount())
            .as("CoreIrTestModel should exercise Composition targets")
            .isGreaterThan(0);
        assertThat(summary.structures())
            .as("CoreIrTestModel should expose a physical generated composition structure")
            .anySatisfy(structure -> {
                assertThat(structure.physical()).isTrue();
                assertThat(structure.compositionTarget()).isTrue();
                assertThat(structure.generated()).isTrue();
            });

        structures(realSchema.metadata())
            .filter(this::hasPhysicalMapping)
            .forEach(structure -> assertThat(mapper.shouldGenerateClass(structure))
                .as("physical structure should be generated: %s", structure.getName())
                .isTrue());

        structures(realSchema.metadata())
            .filter(structure -> !hasPhysicalMapping(structure))
            .filter(structure -> !summary.compositionTargetNames().contains(structure.getName()))
            .forEach(structure -> assertThat(mapper.shouldGenerateClass(structure))
                .as("unused non-persistent structure should not be generated: %s", structure.getName())
                .isFalse());

        structures(realSchema.metadata())
            .filter(structure -> summary.compositionTargetNames().contains(structure.getName()))
            .filter(structure -> !structure.isAbstract())
            .forEach(structure -> assertThat(mapper.shouldGenerateClass(structure))
                .as("composition target should be generated: %s", structure.getName())
                .isTrue());

        assertNoNamingCollisions(realSchema.metadata(), registry, mapper);
        new GrailsCrudGenerator().generate(realSchema.metadata(), config);
        GeneratedGroovyCompiler.compileGeneratedSources(config.getOutputDir());
    }

    @Test
    void validatesLocalStructureCompositionCasesAgainstRealIli2pgSchema() throws Exception {
        Path jsonReport = REPORT_DIR.resolve("structure-composition-cases-summary.json");
        Path markdownReport = REPORT_DIR.resolve("structure-composition-cases-summary.md");
        deleteReports(jsonReport, markdownReport);

        RealSchemaMetadata realSchema = importAndReadMetadata(
            "StructureCompositionCases",
            STRUCTURE_COMPOSITION_MODEL_FILE,
            "rt_structcomp_"
        );

        GenerationConfig config = GenerationConfig.builder(tempDir.resolve("structure-composition-generated"), "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(realSchema.metadata(), config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(realSchema.metadata(), config, registry);
        StructureSummary summary = summarize(
            realSchema.modelName(),
            realSchema.schemaName(),
            realSchema.metadata(),
            registry,
            mapper
        );
        writeSummary(
            jsonReport,
            markdownReport,
            summary
        );

        assertThat(summary.structures())
            .extracting(StructureEntry::name)
            .contains(
                "StructureCompositionCases.Cases.Attachment",
                "StructureCompositionCases.Cases.Inspection",
                "StructureCompositionCases.Cases.Part"
            );
        assertThat(summary.compositionRelationships())
            .extracting(CompositionRelationshipEntry::sourceAttribute)
            .contains("MainInspection", "OptionalAttachment", "Parts");
        assertThat(summary.compositionRelationships())
            .filteredOn(relationship -> "Parts".equals(relationship.sourceAttribute()))
            .singleElement()
            .satisfies(relationship -> {
                assertThat(relationship.cardinality()).isEqualTo("1..1 -> 0..*");
                assertThat(relationship.ordered()).isTrue();
                assertThat(relationship.generatedTarget()).isTrue();
            });
        assertThat(summary.compositionRelationships())
            .filteredOn(relationship -> !"Parts".equals(relationship.sourceAttribute()))
            .allSatisfy(relationship -> assertThat(relationship.generatedTarget()).isTrue());
        assertThat(summary.relationshipCounts())
            .containsKeys(
                RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE.name(),
                RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE.name(),
                RelationshipMetadata.SemanticKind.ILI2DB_FK.name(),
                RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE.name()
            );

        structures(realSchema.metadata())
            .filter(structure -> summary.compositionTargetNames().contains(structure.getName()))
            .forEach(structure -> assertThat(mapper.shouldGenerateClass(structure))
                .as("composition target should be generated: %s", structure.getName())
                .isTrue());

        assertNoNamingCollisions(realSchema.metadata(), registry, mapper);
        new GrailsCrudGenerator().generate(realSchema.metadata(), config);
        GeneratedGroovyCompiler.compileGeneratedSources(config.getOutputDir());
    }

    @Test
    void validatesVsadssminiLargeModelAgainstRealIli2pgSchema() throws Exception {
        Path jsonReport = REPORT_DIR.resolve("vsadssmini-structure-composition-summary.json");
        Path markdownReport = REPORT_DIR.resolve("vsadssmini-structure-composition-summary.md");
        deleteReports(jsonReport, markdownReport);

        RealSchemaMetadata realSchema = importAndReadMetadata(
            "VSADSSMINI_2020_LV95",
            VSADSSMINI_MODEL_FILE,
            "rt_vsadssmini_"
        );

        GenerationConfig config = GenerationConfig.builder(tempDir.resolve("vsadssmini-generated"), "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(realSchema.metadata(), config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(realSchema.metadata(), config, registry);
        StructureSummary summary = summarize(
            realSchema.modelName(),
            realSchema.schemaName(),
            realSchema.metadata(),
            registry,
            mapper
        );
        writeSummary(
            jsonReport,
            markdownReport,
            summary
        );

        assertThat(realSchema.metadata().getAllClasses()).isNotEmpty();
        assertThat(realSchema.metadata().getAllRelationships()).isNotEmpty();
        assertThat(summary.structureCount()).isZero();
        assertThat(summary.compositionTargetCount()).isZero();
        assertThat(summary.relationshipCounts())
            .containsKeys(
                RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE.name(),
                RelationshipMetadata.SemanticKind.ILI2DB_FK.name()
            );
        assertThat(summary.notes())
            .contains("No STRUCTURE or COMPOSITION_ATTRIBUTE entries found in real ili2pg metadata.");
        assertNoNamingCollisions(realSchema.metadata(), registry, mapper);
        new GrailsCrudGenerator().generate(realSchema.metadata(), config);
        GeneratedGroovyCompiler.compileGeneratedSources(config.getOutputDir());
    }

    @Test
    void validatesAssociationCasesAgainstRealIli2pgSchema() throws Exception {
        Path associationCasesModelFile = Path.of("test-models/AssociationCases.ili");
        if (!Files.exists(associationCasesModelFile)) {
            throw new TestAbortedException("AssociationCases.ili not available");
        }

        RealSchemaMetadata realSchema = importAndReadMetadata(
            "AssociationCases",
            associationCasesModelFile,
            "rt_assoc_"
        );

        System.out.println("=== AssociationCases Real ili2db Association Plan ===");
        System.out.println("Classes: " + realSchema.metadata().getAllClasses().size());
        System.out.println("Relationships: " + realSchema.metadata().getAllRelationships().size());
        System.out.println("Associations: " + realSchema.metadata().getAssociations().size());

        GenerationConfig config = GenerationConfig.builder(tempDir.resolve("assoc-generated"), "ch.example.association")
            .domainPackage("ch.example.association.domain")
            .enumPackage("ch.example.association.enums")
            .build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(realSchema.metadata(), config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(realSchema.metadata(), config, registry);
        GrailsAssociationPlanner planner = GrailsAssociationPlanner.forMetadata(
            realSchema.metadata(), config, registry, mapper);

        List<GrailsAssociationPlan> plans = planner.plans();
        System.out.println("Association plans: " + plans.size());

        for (GrailsAssociationPlan plan : plans) {
            System.out.println("  " + plan.associationName()
                + ": storageKind=" + plan.storageKind()
                + " roles=" + plan.roles().size()
                + " attributes=" + plan.attributes().size()
                + " contexts=" + plan.contexts().size()
                + " writable=" + plan.writable()
                + " physicalTable=" + plan.physicalTable());
            for (GrailsAssociationRolePlan role : plan.roles()) {
                System.out.println("    role=" + role.roleName()
                    + " property=" + role.domainPropertyName()
                    + " target=" + role.targetIliClassName());
            }
            for (GrailsAssociationContextPlan ctx : plan.contexts()) {
                System.out.println("    context=" + ctx.contextId()
                    + " presentation=" + ctx.presentationKind()
                    + " createMode=" + ctx.createMode()
                    + " fixedRole=" + ctx.fixedRoleName()
                    + " fixedProperty=" + ctx.fixedRolePropertyName());
            }
        }

        assertThat(plans).hasSize(6);

        // Real ili2pg (--nameByTopic --smart2Inheritance) embeds the attribute-less binary
        // associations as FK columns on the participant classes; the planner must therefore
        // classify them as UNMAPPED and fall back to read-only (ADR-006), never quick-link.
        GrailsAssociationPlan emptyAssoc = planner.findPlan("AssociationCases.Base.EmptyAssociation").orElseThrow();
        assertThat(emptyAssoc.isBinary()).isTrue();
        assertThat(emptyAssoc.hasOwnAttributes()).isFalse();
        assertThat(emptyAssoc.storageKind()).isEqualTo(AssociationStorageKind.UNMAPPED);
        assertThat(emptyAssoc.writable()).isFalse();
        assertThat(emptyAssoc.contexts())
            .allSatisfy(ctx -> {
                assertThat(ctx.createMode()).isEqualTo(AssociationCreateMode.NONE);
                assertThat(ctx.presentationKind()).isEqualTo(AssociationPresentationKind.READ_ONLY);
            });

        for (String embedded : List.of(
            "AssociationCases.Base.SameTargetAssociation",
            "AssociationCases.Base.PhysicalMismatchAssociation",
            "AssociationCases.Base.ExternalCompositeAssociation")) {
            GrailsAssociationPlan plan = planner.findPlan(embedded).orElseThrow();
            assertThat(plan.storageKind())
                .as("embedded association %s must be UNMAPPED in real ili2pg", embedded)
                .isEqualTo(AssociationStorageKind.UNMAPPED);
            assertThat(plan.writable())
                .as("embedded association %s must not be writable", embedded)
                .isFalse();
        }

        // AssociationWithAttribute cannot be embedded (has an own attribute) => real link table.
        GrailsAssociationPlan attAssoc = planner.findPlan("AssociationCases.Base.AssociationWithAttribute").orElseThrow();
        assertThat(attAssoc.hasOwnAttributes()).isTrue();
        assertThat(attAssoc.storageKind()).isEqualTo(AssociationStorageKind.LINK_ENTITY);
        assertThat(attAssoc.contexts())
            .allSatisfy(ctx -> assertThat(ctx.createMode()).isEqualTo(AssociationCreateMode.CONTEXTUAL_FORM));

        // ExtendedTopicAssociation is a genuine binary link table without attributes:
        // this is the real quick-link candidate in the imported schema.
        GrailsAssociationPlan extended = planner.findPlan("AssociationCases.Extended.ExtendedTopicAssociation")
            .orElseThrow();
        assertThat(extended.storageKind()).isEqualTo(AssociationStorageKind.LINK_ENTITY);
        assertThat(extended.isBinary()).isTrue();
        assertThat(extended.hasOwnAttributes()).isFalse();
        assertThat(extended.writable()).isTrue();
        assertThat(extended.contexts())
            .as("real quick-link candidate ExtendedTopicAssociation")
            .allSatisfy(ctx -> assertThat(ctx.createMode()).isEqualTo(AssociationCreateMode.QUICK));

        new GrailsCrudGenerator().generate(realSchema.metadata(), config);
        GeneratedGroovyCompiler.compileGeneratedSources(config.getOutputDir());

        Path registryFile = config.getOutputDir().resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisAssociationRegistry.groovy");
        assertThat(registryFile).exists();
        String registryContent = Files.readString(registryFile, StandardCharsets.UTF_8);
        assertThat(registryContent).contains("ASSOCIATIONS = [");
        assertThat(registryContent).contains("CONTEXTS = [");
        assertThat(registryContent).contains("ENTITIES = [");
        assertThat(registryContent).contains("createMode: 'QUICK'");
    }

    @Test
    void exercisesRealIli2pgQuickLinkInsertQueryDelete() throws Exception {
        Path modelFile = Path.of("test-models/AssociationCases.ili");
        if (!Files.exists(modelFile)) {
            throw new TestAbortedException("AssociationCases.ili not available");
        }
        Path ili2pgHome = requireIli2pgHome();
        requireDockerCompose();
        startComposeDb();
        waitForDatabase();

        String schemaName = uniqueSchemaName("rt_ql_");
        try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
            dropSchema(connection, schemaName);
        }

        try {
            runIli2pgImport(ili2pgHome, "AssociationCases", schemaName);

            ModelMetadata metadata;
            try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
                MetadataReader reader = new MetadataReader(conn, modelFile.toFile(), schemaName, MODEL_REPOSITORIES);
                metadata = reader.readMetadata("AssociationCases");
            }

            GenerationConfig config = GenerationConfig.builder(tempDir.resolve("ql-gen"), "ch.example.association")
                .domainPackage("ch.example.association.domain")
                .enumPackage("ch.example.association.enums")
                .build();
            TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
            GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
            GrailsAssociationPlanner planner = GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);

            GrailsAssociationPlan ext = planner.findPlan("AssociationCases.Extended.ExtendedTopicAssociation")
                .orElseThrow();
            assertThat(ext.storageKind()).isEqualTo(AssociationStorageKind.LINK_ENTITY);

            String assocTable = ext.physicalTable();
            String personCol = roleColumn(ext, "ExtendedPersonRole");
            String parcelCol = roleColumn(ext, "ExtendedParcelRole");
            String personTable = tableFor(metadata, "AssociationCases.Base.Person");
            String extParcelTable = tableFor(metadata, "AssociationCases.Extended.ExtendedParcel");

            assertThat(assocTable).isNotBlank();
            assertThat(personCol).isNotBlank();
            assertThat(parcelCol).isNotBlank();
            assertThat(personTable).isNotBlank();
            assertThat(extParcelTable).isNotBlank();

            try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
                try (Statement st = conn.createStatement()) {
                    st.execute("SET search_path TO " + schemaName);
                }

                long datasetId = insertReturningSeqId(conn,
                    "INSERT INTO t_ili2db_dataset (t_id, datasetname) VALUES (nextval('t_ili2db_seq'), 'p4-quicklink')");
                long basketId = insertReturningSeqId(conn,
                    "INSERT INTO t_ili2db_basket (t_id, dataset, topic, attachmentkey) VALUES "
                        + "(nextval('t_ili2db_seq'), " + datasetId + ", 'AssociationCases.Extended', 'p4-quicklink')");

                long personId = insertReturningSeqId(conn,
                    "INSERT INTO " + personTable + " (t_id, t_basket, aname) VALUES "
                        + "(nextval('t_ili2db_seq'), " + basketId + ", 'Anna Muster')");
                long parcelId = insertReturningSeqId(conn,
                    "INSERT INTO " + extParcelTable + " (t_id, t_basket, ident) VALUES "
                        + "(nextval('t_ili2db_seq'), " + basketId + ", 'PARCEL-QL-1')");

                // Quick-link create: insert the association link row (both role FKs set).
                long linkId = insertReturningSeqId(conn,
                    "INSERT INTO " + assocTable + " (t_id, t_basket, " + personCol + ", " + parcelCol + ") VALUES "
                        + "(nextval('t_ili2db_seq'), " + basketId + ", " + personId + ", " + parcelId + ")");
                assertThat(linkId).isGreaterThan(0);

                // Query from the participant (person) perspective.
                assertThat(countWhere(conn, assocTable, personCol, personId)).isEqualTo(1);
                // And from the counterpart (parcel) perspective.
                assertThat(countWhere(conn, assocTable, parcelCol, parcelId)).isEqualTo(1);

                // Delete only removes the link row.
                try (PreparedStatement del = conn.prepareStatement(
                         "DELETE FROM " + assocTable + " WHERE t_id = ?")) {
                    del.setLong(1, linkId);
                    assertThat(del.executeUpdate()).isEqualTo(1);
                }
                assertThat(countWhere(conn, assocTable, personCol, personId)).isZero();

                // Target objects survive.
                assertThat(countWhere(conn, personTable, "t_id", personId)).isEqualTo(1);
                assertThat(countWhere(conn, extParcelTable, "t_id", parcelId)).isEqualTo(1);
            }
        } finally {
            try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
                dropSchema(connection, schemaName);
            } catch (SQLException ignored) {
                // Temporary schema; a cleanup failure must not mask the original result.
            }
        }
    }

    private String roleColumn(GrailsAssociationPlan plan, String roleName) {
        return plan.roles().stream()
            .filter(role -> roleName.equals(role.roleName()))
            .map(role -> role.physicalName() != null ? role.physicalName() : role.domainPropertyName())
            .findFirst()
            .orElseThrow();
    }

    private String tableFor(ModelMetadata metadata, String iliClassName) {
        ClassMetadata classMetadata = metadata.getClass(iliClassName);
        if (classMetadata == null) {
            return null;
        }
        if (classMetadata.getTableName() != null && !classMetadata.getTableName().isBlank()) {
            return classMetadata.getTableName();
        }
        return classMetadata.getSqlName();
    }

    private long insertReturningSeqId(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            try (ResultSet rs = st.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("No generated id for: " + sql);
    }

    private long countWhere(Connection conn, String table, String column, long value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")) {
            ps.setLong(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void exercisesQuickLinkAssociationCreateQueryAndDeleteWithH2Fixture() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        GenerationConfig config = GenerationConfig.builder(tempDir.resolve("fixture-gen"), "ch.example.association")
            .domainPackage("ch.example.association.domain")
            .enumPackage("ch.example.association.enums")
            .build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner = GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);

        GrailsAssociationPlan emptyAssoc = planner.findPlan("AssociationCases.Base.EmptyAssociation")
            .orElseThrow();
        assertThat(emptyAssoc.storageKind()).isEqualTo(AssociationStorageKind.LINK_ENTITY);
        assertThat(emptyAssoc.isBinary()).isTrue();
        assertThat(emptyAssoc.hasOwnAttributes()).isFalse();

        GrailsAssociationContextPlan personCtx = emptyAssoc.contexts().stream()
            .filter(ctx -> "PersonRole".equals(ctx.fixedRoleName()))
            .findFirst().orElseThrow();
        GrailsAssociationContextPlan parcelCtx = emptyAssoc.contexts().stream()
            .filter(ctx -> "ParcelRole".equals(ctx.fixedRoleName()))
            .findFirst().orElseThrow();

        assertThat(personCtx.createMode()).isEqualTo(AssociationCreateMode.QUICK);
        assertThat(parcelCtx.createMode()).isEqualTo(AssociationCreateMode.QUICK);

        String personFixedProp = personCtx.fixedRolePropertyName();
        String parcelFixedProp = parcelCtx.fixedRolePropertyName();
        assertThat(personFixedProp).isEqualTo("personRoleId");
        assertThat(parcelFixedProp).isEqualTo("parcelRoleId");

        List<String> personEditableProps = new ArrayList<>(personCtx.editableRolePropertyNames());
        List<String> parcelEditableProps = new ArrayList<>(parcelCtx.editableRolePropertyNames());
        assertThat(personEditableProps).contains("parcelRoleId");
        assertThat(parcelEditableProps).contains("personRoleId");

        // Physical SQL columns (what the FK columns are actually called in the ili2db schema).
        String personSqlColumn = emptyAssoc.roles().stream()
            .filter(role -> "PersonRole".equals(role.roleName()))
            .map(GrailsAssociationRolePlan::physicalName)
            .findFirst().orElseThrow();
        String parcelSqlColumn = emptyAssoc.roles().stream()
            .filter(role -> "ParcelRole".equals(role.roleName()))
            .map(GrailsAssociationRolePlan::physicalName)
            .findFirst().orElseThrow();
        assertThat(personSqlColumn).isEqualTo("person_role_id");
        assertThat(parcelSqlColumn).isEqualTo("parcel_role_id");

        String physicalTable = emptyAssoc.physicalTable();
        assertThat(physicalTable).isEqualTo("emptyassociation");

        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:quicklink;DB_CLOSE_DELAY=-1")) {
            MetadataTestFixtures.createAssociationCasesIli2dbFixture(conn);

            try (PreparedStatement insertPerson = conn.prepareStatement(
                     "INSERT INTO person (name) VALUES (?)",
                     Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement insertParcel = conn.prepareStatement(
                     "INSERT INTO parcel (ident) VALUES (?)",
                     Statement.RETURN_GENERATED_KEYS)) {

                insertPerson.setString(1, "Anna Muster");
                insertPerson.executeUpdate();
                long personId;
                try (ResultSet rs = insertPerson.getGeneratedKeys()) {
                    rs.next();
                    personId = rs.getLong(1);
                }

                insertParcel.setString(1, "PARCEL-001");
                insertParcel.executeUpdate();
                long parcelId;
                try (ResultSet rs = insertParcel.getGeneratedKeys()) {
                    rs.next();
                    parcelId = rs.getLong(1);
                }

                try (PreparedStatement insertLink = conn.prepareStatement(
                         "INSERT INTO " + physicalTable + " (" + personSqlColumn + ", "
                         + parcelSqlColumn + ") VALUES (?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {
                    insertLink.setLong(1, personId);
                    insertLink.setLong(2, parcelId);
                    insertLink.executeUpdate();
                    long linkId;
                    try (ResultSet rs = insertLink.getGeneratedKeys()) {
                        rs.next();
                        linkId = rs.getLong(1);
                    }
                    assertThat(linkId).isGreaterThan(0);
                }

                long personViewCount;
                try (PreparedStatement countQ = conn.prepareStatement(
                         "SELECT COUNT(*) FROM " + physicalTable + " WHERE " + personSqlColumn + " = ?")) {
                    countQ.setLong(1, personId);
                    try (ResultSet rs = countQ.executeQuery()) {
                        rs.next();
                        personViewCount = rs.getLong(1);
                    }
                }
                assertThat(personViewCount).isEqualTo(1);

                long parcelViewCount;
                try (PreparedStatement countQ = conn.prepareStatement(
                         "SELECT COUNT(*) FROM " + physicalTable + " WHERE " + parcelSqlColumn + " = ?")) {
                    countQ.setLong(1, parcelId);
                    try (ResultSet rs = countQ.executeQuery()) {
                        rs.next();
                        parcelViewCount = rs.getLong(1);
                    }
                }
                assertThat(parcelViewCount).isEqualTo(1);

                // Delete the link
                try (PreparedStatement deleteLink = conn.prepareStatement(
                         "DELETE FROM " + physicalTable + " WHERE " + personSqlColumn + " = ?"
                         + " AND " + parcelSqlColumn + " = ?")) {
                    deleteLink.setLong(1, personId);
                    deleteLink.setLong(2, parcelId);
                    int deleted = deleteLink.executeUpdate();
                    assertThat(deleted).isEqualTo(1);
                }

                // After delete, link is gone
                try (PreparedStatement countQ = conn.prepareStatement(
                         "SELECT COUNT(*) FROM " + physicalTable + " WHERE " + personSqlColumn + " = ?")) {
                    countQ.setLong(1, personId);
                    try (ResultSet rs = countQ.executeQuery()) {
                        rs.next();
                        assertThat(rs.getLong(1)).isZero();
                    }
                }

                // Person and Parcel still exist
                try (PreparedStatement checkPerson = conn.prepareStatement(
                         "SELECT COUNT(*) FROM person WHERE t_id = ?")) {
                    checkPerson.setLong(1, personId);
                    try (ResultSet rs = checkPerson.executeQuery()) {
                        rs.next();
                        assertThat(rs.getLong(1)).isEqualTo(1);
                    }
                }
                try (PreparedStatement checkParcel = conn.prepareStatement(
                         "SELECT COUNT(*) FROM parcel WHERE t_id = ?")) {
                    checkParcel.setLong(1, parcelId);
                    try (ResultSet rs = checkParcel.executeQuery()) {
                        rs.next();
                        assertThat(rs.getLong(1)).isEqualTo(1);
                    }
                }
            }
        }
    }

    private RealSchemaMetadata importAndReadMetadata(String modelName, Path modelFile, String schemaPrefix)
        throws Exception {
        if (!Files.exists(modelFile)) {
            throw new TestAbortedException("Model file not available: " + modelFile);
        }
        Path ili2pgHome = requireIli2pgHome();
        requireDockerCompose();
        startComposeDb();
        waitForDatabase();

        String schemaName = uniqueSchemaName(schemaPrefix);
        try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
            dropSchema(connection, schemaName);
        }

        try {
            runIli2pgImport(ili2pgHome, modelName, schemaName);
            try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
                MetadataReader reader = new MetadataReader(connection, modelFile.toFile(), schemaName, MODEL_REPOSITORIES);
                try {
                    return new RealSchemaMetadata(modelName, schemaName, reader.readMetadata(modelName));
                } catch (Ili2cFailure e) {
                    if (!"VSADSSMINI_2020_LV95".equals(modelName)) {
                        throw e;
                    }
                    throw new TestAbortedException(
                        "ili2c enrichment skipped because external model repositories are unavailable: "
                            + e.getMessage(),
                        e
                    );
                }
            }
        } finally {
            try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
                dropSchema(connection, schemaName);
            } catch (SQLException ignored) {
                // The schema is temporary. A cleanup failure must not hide the original test failure.
            }
        }
    }

    private Path requireIli2pgHome() {
        String configuredHome = System.getProperty("ili2pgHome", "/Users/stefan/apps/ili2pg-5.5.1");
        Path ili2pgHome = Path.of(configuredHome);
        if (!Files.exists(ili2pgHome.resolve("ili2pg-5.5.1.jar"))
            || !Files.isDirectory(ili2pgHome.resolve("libs"))) {
            throw new TestAbortedException("ili2pg home not available: " + ili2pgHome);
        }
        return ili2pgHome;
    }

    private void requireDockerCompose() throws IOException, InterruptedException {
        CommandResult result = runCommand(List.of("docker", "compose", "version"), Path.of("."), Duration.ofSeconds(30));
        if (result.exitCode() != 0) {
            throw new TestAbortedException("docker compose not available: " + result.output());
        }
    }

    private void startComposeDb() throws IOException, InterruptedException {
        CommandResult result = runCommand(
            List.of("docker", "compose", "up", "-d", "edit-db"),
            Path.of("."),
            Duration.ofMinutes(3)
        );
        if (result.exitCode() != 0) {
            throw new TestAbortedException("Could not start docker compose edit-db: " + result.output());
        }
    }

    private void waitForDatabase() throws InterruptedException {
        long deadline = System.nanoTime() + DB_TIMEOUT.toNanos();
        SQLException lastError = null;
        while (System.nanoTime() < deadline) {
            try (Connection ignored = DriverManager.getConnection(JDBC_URL)) {
                return;
            } catch (SQLException e) {
                lastError = e;
                Thread.sleep(1000);
            }
        }
        throw new TestAbortedException("PostGIS database not reachable at " + JDBC_URL, lastError);
    }

    private void runIli2pgImport(Path ili2pgHome, String modelName, String schemaName)
        throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = ili2pgHome.resolve("ili2pg-5.5.1.jar")
            + File.pathSeparator
            + ili2pgHome.resolve("libs/*");

        List<String> command = new ArrayList<>(List.of(
            javaExecutable.toString(),
            "-cp", classpath,
            "ch.ehi.ili2pg.PgMain",
            "--dbhost", "localhost",
            "--dbport", "54321",
            "--dbdatabase", "edit",
            "--dbusr", "postgres",
            "--dbpwd", "secret",
            "--defaultSrsCode", "2056",
            "--createFk",
            "--nameByTopic",
            "--strokeArcs",
            "--smart2Inheritance",
            "--createEnumTabs",
            "--createBasketCol",
            "--modeldir", String.join(";", MODEL_REPOSITORIES),
            "--models", modelName,
            "--dbschema", schemaName,
            "--schemaimport"
        ));

        CommandResult result = runCommand(command, Path.of("."), PROCESS_TIMEOUT);
        if (result.exitCode() == 0) {
            return;
        }
        if (looksLikeRepositoryProblem(result.output())) {
            throw new TestAbortedException("ili2pg model repository lookup unavailable: " + result.output());
        }
        throw new IOException("ili2pg import failed for " + modelName + " in schema " + schemaName
            + " (exit " + result.exitCode() + "):\n" + result.output());
    }

    private CommandResult runCommand(List<String> command, Path workingDir, Duration timeout)
        throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(124, "Command timed out after " + timeout + ": "
                + String.join(" ", command) + "\n" + output);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new CommandResult(process.exitValue(), output);
    }

    private boolean looksLikeRepositoryProblem(String output) {
        String lower = output == null ? "" : output.toLowerCase(Locale.ROOT);
        return lower.contains("unknownhost")
            || lower.contains("timed out")
            || lower.contains("could not find model")
            || lower.contains("failed to get model");
    }

    private void dropSchema(Connection connection, String schemaName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    private String uniqueSchemaName(String prefix) {
        return prefix + Long.toUnsignedString(System.nanoTime(), 36).toLowerCase(Locale.ROOT);
    }

    private StructureSummary summarize(String modelName,
                                       String schemaName,
                                       ModelMetadata metadata,
                                       TargetNameRegistry registry,
                                       GrailsRelationshipMapper mapper) {
        Set<String> compositionTargets = metadata.getAllRelationships().stream()
            .filter(relationship -> relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE)
            .map(RelationshipMetadata::getTargetClass)
            .filter(name -> name != null && !name.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ClassMetadata> structures = structures(metadata).toList();
        long physicalStructures = structures.stream().filter(this::hasPhysicalMapping).count();
        long generatedStructures = structures.stream().filter(mapper::shouldGenerateClass).count();
        long nonGeneratedStructures = structures.size() - generatedStructures;
        List<StructureEntry> structureEntries = structures.stream()
            .sorted(Comparator.comparing(ClassMetadata::getName, Comparator.nullsLast(String::compareTo)))
            .map(structure -> new StructureEntry(
                structure.getName(),
                structure.getKind() == null ? null : structure.getKind().name(),
                structure.getTableName(),
                structure.getSqlName(),
                hasPhysicalMapping(structure),
                compositionTargets.contains(structure.getName()),
                mapper.shouldGenerateClass(structure),
                structure.isAbstract()
            ))
            .toList();
        List<CompositionRelationshipEntry> compositionRelationships = metadata.getAllRelationships().stream()
            .filter(relationship -> relationship.getSemanticKind() == RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE)
            .sorted(Comparator
                .comparing(RelationshipMetadata::getSourceClass, Comparator.nullsLast(String::compareTo))
                .thenComparing(RelationshipMetadata::getTargetClass, Comparator.nullsLast(String::compareTo))
                .thenComparing(RelationshipMetadata::getSourceAttribute, Comparator.nullsLast(String::compareTo))
                .thenComparing(RelationshipMetadata::getName, Comparator.nullsLast(String::compareTo)))
            .map(relationship -> new CompositionRelationshipEntry(
                relationship.getSourceClass(),
                relationship.getTargetClass(),
                relationship.getSourceAttribute(),
                relationship.getTargetRoleName(),
                formatCardinality(relationship.getCardinality()),
                relationship.isOrdered(),
                relationship.isExternal(),
                isGeneratedClass(metadata, mapper, relationship.getTargetClass())
            ))
            .toList();
        List<GeneratedClassEntry> generatedClasses = mapper.generatedClasses().stream()
            .sorted(Comparator.comparing(ClassMetadata::getName, Comparator.nullsLast(String::compareTo)))
            .map(classMetadata -> new GeneratedClassEntry(
                classMetadata.getName(),
                registry.className(classMetadata)
            ))
            .toList();
        List<SkippedStructureEntry> skippedStructures = structureEntries.stream()
            .filter(structure -> !structure.generated())
            .map(structure -> new SkippedStructureEntry(structure.name(), skippedReason(structure)))
            .toList();
        List<String> notes = new ArrayList<>();
        if (structures.isEmpty() && compositionTargets.isEmpty()) {
            notes.add("No STRUCTURE or COMPOSITION_ATTRIBUTE entries found in real ili2pg metadata.");
        }
        Map<String, Long> relationshipCounts = metadata.getAllRelationships().stream()
            .collect(Collectors.groupingBy(
                relationship -> relationship.getSemanticKind() == null
                    ? "UNSPECIFIED"
                    : relationship.getSemanticKind().name(),
                LinkedHashMap::new,
                Collectors.counting()
            ));
        return new StructureSummary(
            modelName,
            schemaName,
            metadata.getAllClasses().size(),
            structures.size(),
            physicalStructures,
            compositionTargets.size(),
            generatedStructures,
            nonGeneratedStructures,
            compositionTargets,
            relationshipCounts,
            structureEntries,
            compositionRelationships,
            generatedClasses,
            skippedStructures,
            notes
        );
    }

    private boolean isGeneratedClass(ModelMetadata metadata, GrailsRelationshipMapper mapper, String className) {
        ClassMetadata classMetadata = metadata.getClass(className);
        return classMetadata != null && mapper.shouldGenerateClass(classMetadata);
    }

    private String skippedReason(StructureEntry structure) {
        if (structure.abstractClass()) {
            return "abstract";
        }
        if (!structure.physical() && !structure.compositionTarget()) {
            return "nonPersistentUnused";
        }
        return "notTargeted";
    }

    private String formatCardinality(RelationshipMetadata.Cardinality cardinality) {
        if (cardinality == null) {
            return "";
        }
        return cardinality.getMinSource() + ".." + bound(cardinality.getMaxSource())
            + " -> "
            + cardinality.getMinTarget() + ".." + bound(cardinality.getMaxTarget());
    }

    private String bound(int value) {
        return value == -1 ? "*" : Integer.toString(value);
    }

    private Stream<ClassMetadata> structures(ModelMetadata metadata) {
        return metadata.getAllClasses().stream()
            .filter(classMetadata -> classMetadata.getKind() == ClassMetadata.ClassKind.STRUCTURE);
    }

    private boolean hasPhysicalMapping(ClassMetadata classMetadata) {
        return (classMetadata.getTableName() != null && !classMetadata.getTableName().isBlank())
            || (classMetadata.getSqlName() != null && !classMetadata.getSqlName().isBlank());
    }

    private void assertNoNamingCollisions(ModelMetadata metadata,
                                          TargetNameRegistry registry,
                                          GrailsRelationshipMapper mapper) {
        List<String> classNames = mapper.generatedClasses().stream()
            .map(registry::className)
            .toList();
        assertThat(classNames).doesNotHaveDuplicates();

        List<String> enumNames = metadata.getAllEnums().stream()
            .map(registry::enumName)
            .toList();
        assertThat(enumNames).doesNotHaveDuplicates();

        for (ClassMetadata classMetadata : mapper.generatedClasses()) {
            GrailsRelationshipMapper.DomainMapping mapping = mapper.map(classMetadata);
            List<String> propertyNames = new ArrayList<>();
            mapping.properties().forEach(property -> propertyNames.add(property.name()));
            mapping.collections().forEach(collection -> propertyNames.add(collection.name()));
            assertThat(propertyNames)
                .as("generated properties for %s", classMetadata.getName())
                .doesNotHaveDuplicates();
        }
    }

    private void writeSummary(Path jsonTarget, Path markdownTarget, StructureSummary summary) throws IOException {
        Files.createDirectories(jsonTarget.getParent());
        Files.writeString(jsonTarget, summary.toJson(), StandardCharsets.UTF_8);
        Files.writeString(markdownTarget, summary.toMarkdown(), StandardCharsets.UTF_8);
    }

    private void deleteReports(Path jsonTarget, Path markdownTarget) throws IOException {
        Files.deleteIfExists(jsonTarget);
        Files.deleteIfExists(markdownTarget);
    }

    private record RealSchemaMetadata(String modelName, String schemaName, ModelMetadata metadata) {
    }

    private record CommandResult(int exitCode, String output) {
    }

    private record StructureSummary(
        String modelName,
        String schemaName,
        int classCount,
        int structureCount,
        long physicalStructureCount,
        int compositionTargetCount,
        long generatedStructureCount,
        long nonGeneratedStructureCount,
        Set<String> compositionTargetNames,
        Map<String, Long> relationshipCounts,
        List<StructureEntry> structures,
        List<CompositionRelationshipEntry> compositionRelationships,
        List<GeneratedClassEntry> generatedClasses,
        List<SkippedStructureEntry> skippedStructures,
        List<String> notes
    ) {
        String toJson() {
            return "{\n"
                + "  \"modelName\": \"" + escape(modelName) + "\",\n"
                + "  \"schemaName\": \"" + escape(schemaName) + "\",\n"
                + "  \"classCount\": " + classCount + ",\n"
                + "  \"structureCount\": " + structureCount + ",\n"
                + "  \"physicalStructureCount\": " + physicalStructureCount + ",\n"
                + "  \"compositionTargetCount\": " + compositionTargetCount + ",\n"
                + "  \"generatedStructureCount\": " + generatedStructureCount + ",\n"
                + "  \"nonGeneratedStructureCount\": " + nonGeneratedStructureCount + ",\n"
                + "  \"compositionTargetNames\": " + stringArray(compositionTargetNames) + ",\n"
                + "  \"relationshipCounts\": " + countObject(relationshipCounts) + ",\n"
                + "  \"structures\": " + structureArray(structures) + ",\n"
                + "  \"compositionRelationships\": " + compositionRelationshipArray(compositionRelationships) + ",\n"
                + "  \"generatedClasses\": " + generatedClassArray(generatedClasses) + ",\n"
                + "  \"skippedStructures\": " + skippedStructureArray(skippedStructures) + ",\n"
                + "  \"notes\": " + stringList(notes) + "\n"
                + "}\n";
        }

        String toMarkdown() {
            StringBuilder builder = new StringBuilder();
            builder.append("# Real ili2db Structure/Composition Inventory\n\n");
            builder.append("- Model: `").append(modelName).append("`\n");
            builder.append("- Schema: `").append(schemaName).append("`\n");
            builder.append("- Classes: ").append(classCount).append("\n");
            builder.append("- Structures: ").append(structureCount).append("\n");
            builder.append("- Composition targets: ").append(compositionTargetCount).append("\n");
            builder.append("- Generated structures: ").append(generatedStructureCount).append("\n\n");

            if (!notes.isEmpty()) {
                builder.append("## Notes\n\n");
                notes.forEach(note -> builder.append("- ").append(note).append("\n"));
                builder.append("\n");
            }

            builder.append("## Structures\n\n");
            if (structures.isEmpty()) {
                builder.append("No STRUCTURE entries found in real ili2pg metadata.\n\n");
            } else {
                builder.append("| Name | Table | Physical | Composition Target | Generated | Abstract |\n");
                builder.append("|---|---|---:|---:|---:|---:|\n");
                structures.forEach(structure -> builder
                    .append("| `").append(structure.name()).append("` | `")
                    .append(blank(structure.tableName())).append("` | ")
                    .append(structure.physical()).append(" | ")
                    .append(structure.compositionTarget()).append(" | ")
                    .append(structure.generated()).append(" | ")
                    .append(structure.abstractClass()).append(" |\n"));
                builder.append("\n");
            }

            builder.append("## Composition Relationships\n\n");
            if (compositionRelationships.isEmpty()) {
                builder.append("No COMPOSITION_ATTRIBUTE relationships found in real ili2pg metadata.\n\n");
            } else {
                builder.append("| Source | Target | Attribute | Cardinality | Ordered | External | Generated Target |\n");
                builder.append("|---|---|---|---|---:|---:|---:|\n");
                compositionRelationships.forEach(relationship -> builder
                    .append("| `").append(relationship.sourceClass()).append("` | `")
                    .append(relationship.targetClass()).append("` | `")
                    .append(blank(relationship.sourceAttribute())).append("` | `")
                    .append(blank(relationship.cardinality())).append("` | ")
                    .append(relationship.ordered()).append(" | ")
                    .append(relationship.external()).append(" | ")
                    .append(relationship.generatedTarget()).append(" |\n"));
                builder.append("\n");
            }

            builder.append("## Generated Classes\n\n");
            if (generatedClasses.isEmpty()) {
                builder.append("No Grails target classes are generated.\n\n");
            } else {
                builder.append("| IR Name | Grails Target |\n");
                builder.append("|---|---|\n");
                generatedClasses.forEach(generatedClass -> builder
                    .append("| `").append(generatedClass.name()).append("` | `")
                    .append(generatedClass.targetName()).append("` |\n"));
                builder.append("\n");
            }

            builder.append("## Skipped Structures\n\n");
            if (skippedStructures.isEmpty()) {
                builder.append("No structures were skipped.\n\n");
            } else {
                builder.append("| Name | Reason |\n");
                builder.append("|---|---|\n");
                skippedStructures.forEach(skipped -> builder
                    .append("| `").append(skipped.name()).append("` | `")
                    .append(skipped.reason()).append("` |\n"));
                builder.append("\n");
            }

            builder.append("## Relationship Counts\n\n");
            if (relationshipCounts.isEmpty()) {
                builder.append("No relationships found.\n");
            } else {
                builder.append("| Kind | Count |\n");
                builder.append("|---|---:|\n");
                relationshipCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append("| `")
                        .append(entry.getKey())
                        .append("` | ")
                        .append(entry.getValue())
                        .append(" |\n"));
            }
            return builder.toString();
        }

        private static String stringArray(Set<String> values) {
            return values.stream()
                .sorted()
                .map(value -> "\"" + escape(value) + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
        }

        private static String stringList(List<String> values) {
            return values.stream()
                .map(value -> "\"" + escape(value) + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
        }

        private static String countObject(Map<String, Long> counts) {
            return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "\"" + escape(entry.getKey()) + "\": " + entry.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
        }

        private static String structureArray(List<StructureEntry> structures) {
            return structures.stream()
                .map(structure -> "{"
                    + "\"name\": \"" + escape(structure.name()) + "\", "
                    + "\"kind\": \"" + escape(structure.kind()) + "\", "
                    + "\"tableName\": \"" + escape(structure.tableName()) + "\", "
                    + "\"sqlName\": \"" + escape(structure.sqlName()) + "\", "
                    + "\"physical\": " + structure.physical() + ", "
                    + "\"compositionTarget\": " + structure.compositionTarget() + ", "
                    + "\"generated\": " + structure.generated() + ", "
                    + "\"abstract\": " + structure.abstractClass()
                    + "}")
                .collect(Collectors.joining(", ", "[", "]"));
        }

        private static String compositionRelationshipArray(List<CompositionRelationshipEntry> relationships) {
            return relationships.stream()
                .map(relationship -> "{"
                    + "\"sourceClass\": \"" + escape(relationship.sourceClass()) + "\", "
                    + "\"targetClass\": \"" + escape(relationship.targetClass()) + "\", "
                    + "\"sourceAttribute\": \"" + escape(relationship.sourceAttribute()) + "\", "
                    + "\"targetRoleName\": \"" + escape(relationship.targetRoleName()) + "\", "
                    + "\"cardinality\": \"" + escape(relationship.cardinality()) + "\", "
                    + "\"ordered\": " + relationship.ordered() + ", "
                    + "\"external\": " + relationship.external() + ", "
                    + "\"generatedTarget\": " + relationship.generatedTarget()
                    + "}")
                .collect(Collectors.joining(", ", "[", "]"));
        }

        private static String generatedClassArray(List<GeneratedClassEntry> generatedClasses) {
            return generatedClasses.stream()
                .map(generatedClass -> "{"
                    + "\"name\": \"" + escape(generatedClass.name()) + "\", "
                    + "\"targetName\": \"" + escape(generatedClass.targetName()) + "\""
                    + "}")
                .collect(Collectors.joining(", ", "[", "]"));
        }

        private static String skippedStructureArray(List<SkippedStructureEntry> skippedStructures) {
            return skippedStructures.stream()
                .map(skippedStructure -> "{"
                    + "\"name\": \"" + escape(skippedStructure.name()) + "\", "
                    + "\"reason\": \"" + escape(skippedStructure.reason()) + "\""
                    + "}")
                .collect(Collectors.joining(", ", "[", "]"));
        }

        private static String blank(String value) {
            return value == null ? "" : value;
        }

        private static String escape(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    private record StructureEntry(
        String name,
        String kind,
        String tableName,
        String sqlName,
        boolean physical,
        boolean compositionTarget,
        boolean generated,
        boolean abstractClass
    ) {
    }

    private record CompositionRelationshipEntry(
        String sourceClass,
        String targetClass,
        String sourceAttribute,
        String targetRoleName,
        String cardinality,
        boolean ordered,
        boolean external,
        boolean generatedTarget
    ) {
    }

    private record GeneratedClassEntry(String name, String targetName) {
    }

    private record SkippedStructureEntry(String name, String reason) {
    }
}

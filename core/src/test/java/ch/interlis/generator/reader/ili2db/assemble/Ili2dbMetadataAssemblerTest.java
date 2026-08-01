package ch.interlis.generator.reader.ili2db.assemble;

import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.GeometryKind;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.reader.ili2db.Ili2dbDiagnostic;
import ch.interlis.generator.reader.ili2db.Ili2dbFailurePolicy;
import ch.interlis.generator.reader.ili2db.Ili2dbReadContext;
import ch.interlis.generator.reader.ili2db.catalog.AttributeMappingRow;
import ch.interlis.generator.reader.ili2db.catalog.ClassMappingRow;
import ch.interlis.generator.reader.ili2db.catalog.ColumnPropertyRow;
import ch.interlis.generator.reader.ili2db.catalog.EnumDomainRow;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogCapabilities;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogSnapshot;
import ch.interlis.generator.reader.ili2db.catalog.InheritanceRow;
import ch.interlis.generator.reader.ili2db.catalog.ModelRow;
import ch.interlis.generator.reader.ili2db.schema.ColumnSchema;
import ch.interlis.generator.reader.ili2db.schema.DatabaseDialect;
import ch.interlis.generator.reader.ili2db.schema.GeometryColumnSchema;
import ch.interlis.generator.reader.ili2db.schema.GeometrySchemaSnapshot;
import ch.interlis.generator.reader.ili2db.schema.JdbcSchemaSnapshot;
import ch.interlis.generator.reader.ili2db.schema.PrimaryKeySchema;
import ch.interlis.generator.reader.ili2db.schema.TableSchema;
import ch.interlis.generator.reader.sql.QualifiedSqlName;
import ch.interlis.generator.reader.sql.SqlIdentifier;
import ch.interlis.generator.reader.sql.SqlIdentifierRenderer;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Assembly-Schicht: einzige Stelle, die IR-Builder aus Snapshots erzeugt.
 * Keine SQL-Zugriffe; Enum-Werte kommen vom EnumReader.
 */
class Ili2dbMetadataAssemblerTest {

    @Test
    void assemblesBuilderFromSnapshots() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:assemble_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            try (var stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE sample_status ("
                    + "ilicode VARCHAR(100), dispname VARCHAR(100), seq INT)");
                stmt.execute("INSERT INTO sample_status VALUES ('a', 'A', 0)");
            }
            Ili2dbCatalogSnapshot catalog = catalogSnapshot();
            JdbcSchemaSnapshot schema = schemaSnapshot();
            GeometrySchemaSnapshot geometry = new GeometrySchemaSnapshot(List.of(
                new GeometryColumnSchema(
                    new QualifiedSqlName(null, SqlIdentifier.discovered("sample")),
                    "geom",
                    GeometryKind.POINT,
                    2056,
                    false,
                    false
                )
            ), true);
            List<Ili2dbDiagnostic> diagnostics = new ArrayList<>();

            ModelMetadata metadata = new ModelMetadataFactory().buildValidated(
                new Ili2dbMetadataAssembler().assemble(
                    context(connection), catalog, schema, geometry, diagnostics));

            assertThat(metadata.getModelName()).isEqualTo("AssembleModel");
            assertThat(metadata.getSchemaName()).isNull();
            assertThat(metadata.getIli2dbVersion()).isEqualTo("ili2pg-5.5.1");
            assertThat(metadata.getSettings())
                .containsEntry("ch.ehi.ili2db.sender", "ili2pg-5.5.1");

            ClassMetadata sample = metadata.getClass("AssembleModel.Topic.Sample");
            assertThat(sample.getKind()).isEqualTo(ClassMetadata.ClassKind.CLASS);
            assertThat(sample.getTableName()).isEqualTo("sample");

            ClassMetadata child = metadata.getClass("AssembleModel.Topic.Child");
            assertThat(child.getBaseClass()).isEqualTo("AssembleModel.Topic.Sample");

            AttributeMetadata name = sample.getAttribute("name");
            assertThat(name.getDbType()).isEqualTo("VARCHAR");
            assertThat(name.getMaxLength()).isEqualTo(100);
            assertThat(name.isMandatory()).isFalse();

            AttributeMetadata status = sample.getAttribute("status");
            assertThat(status.getEnumType()).isEqualTo("AssembleModel.Topic.Status");
            assertThat(status.getEnumValues())
                .extracting(EnumMetadata.EnumValue::getIliCode)
                .containsExactly("a");

            AttributeMetadata owner = sample.getAttribute("owner");
            assertThat(owner.isForeignKey()).isTrue();
            assertThat(owner.getReferencedClass()).isEqualTo("AssembleModel.Topic.Sample");
            assertThat(owner.getColumnName()).isEqualTo("owner_fk");

            AttributeMetadata tId = sample.getAttribute("t_id");
            assertThat(tId.isPrimaryKey()).isTrue();

            AttributeMetadata geom = sample.getAttribute("geom");
            assertThat(geom.isGeometry()).isTrue();
            assertThat(geom.getGeometryKindEnum()).isEqualTo(GeometryKind.POINT);
            assertThat(geom.getGeometrySrid()).isEqualTo(2056);
            assertThat(geom.getGeometryHasZ()).isFalse();

            assertThat(diagnostics)
                .filteredOn(Ili2dbDiagnostic::isBlocking)
                .isEmpty();
        }
    }

    @Test
    void appliesUnitColumnProperty() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:assemble_unit_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            Ili2dbCatalogSnapshot catalog = new Ili2dbCatalogSnapshot(
                Map.of(),
                List.of(),
                List.of(new ClassMappingRow("AssembleModel.Topic.Sample", "sample", "CLASS")),
                List.of(new AttributeMappingRow(
                    "AssembleModel.Topic.Sample.area", "area", "sample", null)),
                List.of(),
                List.of(new ColumnPropertyRow(
                    "sample", "area", "ch.ehi.ili2db.unit", "m2")),
                List.of(),
                new Ili2dbCatalogCapabilities(Set.of(), Map.of())
            );
            ModelMetadata metadata = new ModelMetadataFactory().buildValidated(
                new Ili2dbMetadataAssembler().assemble(
                    context(connection), catalog, new JdbcSchemaSnapshot(List.of()),
                    GeometrySchemaSnapshot.empty(), new ArrayList<>()));

            AttributeMetadata area =
                metadata.getClass("AssembleModel.Topic.Sample").getAttribute("area");
            assertThat(area.getUnit()).isEqualTo("m2");
        }
    }

    private Ili2dbReadContext context(Connection connection) throws Exception {
        return new Ili2dbReadContext(
            connection,
            ModelSelection.rootOnly("AssembleModel"),
            null,
            SqlIdentifierRenderer.from(connection.getMetaData()),
            DatabaseDialect.H2,
            Ili2dbFailurePolicy.STRICT
        );
    }

    private Ili2dbCatalogSnapshot catalogSnapshot() {
        List<ClassMappingRow> classes = List.of(
            new ClassMappingRow("AssembleModel.Topic.Sample", "sample", "CLASS"),
            new ClassMappingRow("AssembleModel.Topic.Child", "child", "CLASS")
        );
        List<AttributeMappingRow> attributes = List.of(
            new AttributeMappingRow("AssembleModel.Topic.Sample.name", "name", "sample", null),
            new AttributeMappingRow("AssembleModel.Topic.Sample.status", "status", "sample", null),
            new AttributeMappingRow("AssembleModel.Topic.Sample.owner", "owner_fk", "sample",
                "AssembleModel.Topic.Sample"),
            new AttributeMappingRow("AssembleModel.Topic.Sample.geom", "geom", "sample", null)
        );
        List<InheritanceRow> inheritance = List.of(
            new InheritanceRow("AssembleModel.Topic.Child", "AssembleModel.Topic.Sample")
        );
        List<EnumDomainRow> enumDomains = List.of(
            new EnumDomainRow("sample", "status", "AssembleModel.Topic.Status", "sample_status")
        );
        Map<String, String> settings = Map.of(
            "ch.ehi.ili2db.sender", "ili2pg-5.5.1"
        );
        return new Ili2dbCatalogSnapshot(settings,
            List.of(new ModelRow("AssembleModel", "MODEL")),
            classes, attributes, inheritance, List.of(), enumDomains,
            new Ili2dbCatalogCapabilities(Set.of(), Map.of()));
    }

    private JdbcSchemaSnapshot schemaSnapshot() {
        Map<String, ColumnSchema> sampleColumns = new LinkedHashMap<>();
        sampleColumns.put("t_id", new ColumnSchema("t_id", 4, "INTEGER", false, 32, 0, 1));
        sampleColumns.put("name", new ColumnSchema("name", 12, "VARCHAR", true, 100, 0, 2));
        sampleColumns.put("status", new ColumnSchema("status", 12, "VARCHAR", true, 10, 0, 3));
        sampleColumns.put("owner_fk", new ColumnSchema("owner_fk", 4, "INTEGER", true, 32, 0, 4));
        sampleColumns.put("geom", new ColumnSchema("geom", 1111, "GEOMETRY", true, null, null, 5));
        TableSchema sample = new TableSchema(
            new QualifiedSqlName(null, SqlIdentifier.discovered("sample")),
            sampleColumns,
            List.of(new PrimaryKeySchema("t_id", 1)),
            List.of()
        );
        Map<String, ColumnSchema> childColumns = new LinkedHashMap<>();
        childColumns.put("t_id", new ColumnSchema("t_id", 4, "INTEGER", false, 32, 0, 1));
        TableSchema child = new TableSchema(
            new QualifiedSqlName(null, SqlIdentifier.discovered("child")),
            childColumns,
            List.of(new PrimaryKeySchema("t_id", 1)),
            List.of()
        );
        return new JdbcSchemaSnapshot(List.of(sample, child));
    }
}

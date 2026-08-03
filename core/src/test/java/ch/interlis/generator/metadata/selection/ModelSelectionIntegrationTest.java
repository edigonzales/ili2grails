package ch.interlis.generator.metadata.selection;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.Ili2cModelReader;
import ch.interlis.generator.reader.Ili2dbMetadataReader;
import ch.interlis.generator.reader.ili2db.Ili2dbDiagnostic;
import ch.interlis.generator.reader.ili2db.Ili2dbDiagnosticCode;
import ch.interlis.generator.reader.ili2db.Ili2dbReadException;
import ch.interlis.generator.reader.ili2db.Ili2dbReadResult;
import ch.interlis.generator.reader.ili2db.Ili2dbSeverity;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-End: ili2c-Auswahl steuert den physischen Reader; unabhängige Modelle
 * desselben Schemas werden ausgeschlossen.
 */
class ModelSelectionIntegrationTest {

    private static final List<String> ALL_MODEL_FILES = List.of(
        "ModelSelectionRoot.ili",
        "ModelSelectionDependency.ili",
        "ModelSelectionTransitiveDependency.ili",
        "ModelSelectionUnrelated.ili"
    );

    @Test
    void physicalReaderReadsOnlyRootAndDependencies() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:selection_e2e_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            createIli2dbFixture(connection);

            Ili2cModelReader ili2cReader = new Ili2cModelReader(null, List.of("test-models"));
            Ili2cModelReader.Ili2cReadResult readResult = ili2cReader.read("ModelSelectionRoot");

            ModelSelection selection = readResult.modelSelection();
            assertThat(selection.includedModelNames())
                .contains("ModelSelectionRoot", "ModelSelectionDependency",
                    "ModelSelectionTransitiveDependency")
                .doesNotContain("ModelSelectionUnrelated");

            Ili2dbMetadataReader physicalReader =
                Ili2dbMetadataReader.create(connection, null);
            ModelMetadata metadata = physicalReader.readMetadata(selection);

            assertThat(metadata.getClasses().values())
                .extracting(ClassMetadata::getName)
                .contains(
                    "ModelSelectionRoot.RootTopic.RootClass",
                    "ModelSelectionDependency.DependencyTopic.DependencyClass",
                    "ModelSelectionTransitiveDependency.TransitiveTopic.TransitiveClass"
                )
                .doesNotContain("ModelSelectionUnrelated.UnrelatedTopic.UnrelatedClass");
        }
    }

    @Test
    void dbOnlyFallbackReadsRootOnlyEvenWithUnrelatedModelsInSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:selection_dbonly_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            createIli2dbFixture(connection);

            Ili2dbMetadataReader physicalReader =
                Ili2dbMetadataReader.create(connection, null);
            ModelMetadata metadata = physicalReader.readMetadata(
                ModelSelection.rootOnly("ModelSelectionRoot"));

            assertThat(metadata.getClasses().values())
                .extracting(ClassMetadata::getName)
                .contains("ModelSelectionRoot.RootTopic.RootClass")
                .doesNotContain(
                    "ModelSelectionDependency.DependencyTopic.DependencyClass",
                    "ModelSelectionUnrelated.UnrelatedTopic.UnrelatedClass");
        }
    }

    @Test
    void missingRootInDatabaseIsAnError() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:selection_missing_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            createIli2dbFixture(connection);

            Ili2dbMetadataReader physicalReader =
                Ili2dbMetadataReader.create(connection, null);
            assertThatThrownBy(() -> physicalReader.readMetadata(
                ModelSelection.rootOnly("ModelSelectionNotImported")))
                .isInstanceOf(Ili2dbReadException.class)
                .hasMessageContaining("ModelSelectionNotImported");
        }
    }

    @Test
    void missingRootProducesRequestedModelMissingDiagnostic() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:selection_missing_diag_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
            createIli2dbFixture(connection);

            Ili2dbMetadataReader physicalReader =
                Ili2dbMetadataReader.create(connection, null);
            Ili2dbReadResult result = physicalReader.read(
                ModelSelection.rootOnly("ModelSelectionNotImported"));
            assertThat(result.hasFatalDiagnostics()).isTrue();
            assertThat(result.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code() == Ili2dbDiagnosticCode.REQUESTED_MODEL_MISSING)
                .extracting(Ili2dbDiagnostic::severity)
                .containsExactly(Ili2dbSeverity.FATAL);
            assertThat(result.optionalMetadata()).isEmpty();
            assertThat(result.isUsable()).isFalse();
        }
    }

    private void createIli2dbFixture(Connection connection) throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE t_ili2db_classname ("
                + "iliname VARCHAR(1024) PRIMARY KEY, sqlname VARCHAR(1024))");
            stmt.execute("CREATE TABLE t_ili2db_attrname ("
                + "iliname VARCHAR(1024), sqlname VARCHAR(1024), colowner VARCHAR(1024), target VARCHAR(1024))");
            stmt.execute("CREATE TABLE t_ili2db_settings ("
                + "tag VARCHAR(1024), setting VARCHAR(1024))");
            stmt.execute("CREATE TABLE t_ili2db_inheritance ("
                + "thisclass VARCHAR(1024), baseclass VARCHAR(1024))");
            stmt.execute("CREATE TABLE t_ili2db_column_prop ("
                + "tablename VARCHAR(255), columnname VARCHAR(255), tag VARCHAR(1024), setting VARCHAR(1024))");
            stmt.execute("CREATE TABLE t_ili2db_table_prop ("
                + "tablename VARCHAR(255), setting VARCHAR(255))");
            stmt.execute("CREATE TABLE t_ili2db_model ("
                + "modelname VARCHAR(1024), content VARCHAR(1024))");

            stmt.execute("INSERT INTO t_ili2db_classname VALUES "
                + "('ModelSelectionRoot.RootTopic.RootClass', 'rootclass')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES "
                + "('ModelSelectionDependency.DependencyTopic.DependencyClass', 'dependencyclass')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES "
                + "('ModelSelectionTransitiveDependency.TransitiveTopic.TransitiveClass', 'transitiveclass')");
            stmt.execute("INSERT INTO t_ili2db_classname VALUES "
                + "('ModelSelectionUnrelated.UnrelatedTopic.UnrelatedClass', 'unrelatedclass')");

            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('rootclass', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('dependencyclass', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('transitiveclass', 'CLASS')");
            stmt.execute("INSERT INTO t_ili2db_table_prop VALUES ('unrelatedclass', 'CLASS')");

            stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                + "('Name', 'name', 'ModelSelectionRoot.RootTopic.RootClass', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                + "('Ref', 'ref', 'ModelSelectionRoot.RootTopic.RootClass', 'ModelSelectionDependency.DependencyTopic.DependencyClass')");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                + "('Name', 'name', 'ModelSelectionDependency.DependencyTopic.DependencyClass', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                + "('Name', 'name', 'ModelSelectionTransitiveDependency.TransitiveTopic.TransitiveClass', NULL)");
            stmt.execute("INSERT INTO t_ili2db_attrname VALUES "
                + "('Name', 'name', 'ModelSelectionUnrelated.UnrelatedTopic.UnrelatedClass', NULL)");

            stmt.execute("INSERT INTO t_ili2db_model VALUES "
                + "('ModelSelectionRoot', 'MODEL'), "
                + "('ModelSelectionDependency', 'MODEL'), "
                + "('ModelSelectionTransitiveDependency', 'MODEL'), "
                + "('ModelSelectionUnrelated', 'MODEL')");

            stmt.execute("CREATE TABLE rootclass ("
                + "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                + "name VARCHAR(100), ref INTEGER)");
            stmt.execute("CREATE TABLE dependencyclass ("
                + "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                + "name VARCHAR(100))");
            stmt.execute("CREATE TABLE transitiveclass ("
                + "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                + "name VARCHAR(100))");
            stmt.execute("CREATE TABLE unrelatedclass ("
                + "t_id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                + "name VARCHAR(100))");
        }
    }
}

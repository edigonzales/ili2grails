package ch.interlis.generator.metadata.selection;

import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.TransferDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests für die präzise Modellauswahl aus der ili2c-TransferDescription.
 */
class ModelSelectionResolverTest {

    private static final String ROOT = "ModelSelectionRoot";
    private static final String DEPENDENCY = "ModelSelectionDependency";
    private static final String TRANSITIVE = "ModelSelectionTransitiveDependency";
    private static final String UNRELATED = "ModelSelectionUnrelated";

    private final ModelSelectionResolver resolver = new ModelSelectionResolver();

    private TransferDescription compile(String... files) throws Exception {
        Configuration config = new Configuration();
        for (String file : files) {
            config.addFileEntry(new FileEntry(
                "test-models/" + file, FileEntryKind.ILIMODELFILE));
        }
        config.setAutoCompleteModelList(true);
        config.setGenerateWarnings(true);
        Ili2cSettings set = new Ili2cSettings();
        set.setIlidirs("test-models");
        return ch.interlis.ili2c.Main.runCompiler(config, set, null);
    }

    @Test
    void selectionContainsRootAndTransitiveDependencies() throws Exception {
        TransferDescription td = compile(
            "ModelSelectionRoot.ili",
            "ModelSelectionDependency.ili",
            "ModelSelectionTransitiveDependency.ili",
            "ModelSelectionUnrelated.ili"
        );

        ModelSelection selection = resolver.fromTransferDescription(td, ROOT);

        assertThat(selection.source()).isEqualTo(ModelSelectionSource.ILI2C_DEPENDENCY_GRAPH);
        assertThat(selection.includedModelNames())
            .contains(ROOT, DEPENDENCY, TRANSITIVE);
    }

    @Test
    void unrelatedModelInSameTransferDescriptionIsExcluded() throws Exception {
        TransferDescription td = compile(
            "ModelSelectionRoot.ili",
            "ModelSelectionDependency.ili",
            "ModelSelectionTransitiveDependency.ili",
            "ModelSelectionUnrelated.ili"
        );

        ModelSelection selection = resolver.fromTransferDescription(td, ROOT);

        assertThat(selection.includedModelNames()).doesNotContain(UNRELATED);
    }

    @Test
    void rootIsAlwaysFirstAndDependenciesAreSorted() throws Exception {
        TransferDescription td = compile(
            "ModelSelectionRoot.ili",
            "ModelSelectionDependency.ili",
            "ModelSelectionTransitiveDependency.ili",
            "ModelSelectionUnrelated.ili"
        );

        ModelSelection selection = resolver.fromTransferDescription(td, ROOT);

        List<String> names = List.copyOf(selection.includedModelNames());
        assertThat(names.get(0)).isEqualTo(ROOT);
        assertThat(names.subList(1, names.size())).isSorted();
    }

    @Test
    void cycleBetweenModelsIsHandledSafely(@TempDir Path tempDir) throws Exception {
        Path cycleFile = tempDir.resolve("CycleSelf.ili");
        Files.writeString(cycleFile, """
            INTERLIS 2.4;
            MODEL CycleSelf (en) AT "mailto:x" VERSION "2026-01-01" =
              IMPORTS CycleSelf;
              TOPIC TopicS =
                CLASS ClassS = Name: TEXT; END ClassS;
              END TopicS;
            END CycleSelf.
            """);

        Configuration config = new Configuration();
        config.addFileEntry(new FileEntry(cycleFile.toString(), FileEntryKind.ILIMODELFILE));
        config.setAutoCompleteModelList(true);
        config.setGenerateWarnings(true);
        Ili2cSettings set = new Ili2cSettings();
        set.setIlidirs(tempDir.toString());
        TransferDescription td = ch.interlis.ili2c.Main.runCompiler(config, set, null);
        assertThat(td).isNotNull();

        ModelSelection selection = resolver.fromTransferDescription(td, "CycleSelf");

        assertThat(selection.includedModelNames()).containsExactly("CycleSelf");
    }

    @Test
    void missingRootModelIsAnError() throws Exception {
        TransferDescription td = compile(
            "ModelSelectionRoot.ili",
            "ModelSelectionDependency.ili",
            "ModelSelectionTransitiveDependency.ili",
            "ModelSelectionUnrelated.ili"
        );

        assertThatThrownBy(() -> resolver.fromTransferDescription(td, "DoesNotExist"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DoesNotExist");
    }

    @Test
    void rootOnlyFallbackContainsOnlyRoot() {
        ModelSelection selection = ModelSelection.rootOnly("RootOnlyModel");

        assertThat(selection.source()).isEqualTo(ModelSelectionSource.ROOT_ONLY_FALLBACK);
        assertThat(selection.includedModelNames()).containsExactly("RootOnlyModel");
        assertThat(selection.includes("RootOnlyModel")).isTrue();
        assertThat(selection.includes("OtherModel")).isFalse();
    }

    @Test
    void selectionRequiresRootInIncludedNames() {
        assertThatThrownBy(() -> new ModelSelection(
            "Root", java.util.Set.of("Other"), ModelSelectionSource.ROOT_ONLY_FALLBACK))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

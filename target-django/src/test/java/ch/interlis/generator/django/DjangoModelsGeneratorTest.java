package ch.interlis.generator.django;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.Ili2cModelReader;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DjangoModelsGeneratorTest {

    private static final Path SNAPSHOT_ROOT = Path.of("target-django/src/test/resources/django-snapshots");

    @TempDir
    Path tempDir;

    @Test
    void simpleAddressMergedOutputMatchesSnapshot() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedSimpleAddressMetadata();
        Path outputDir = tempDir.resolve("simple-address-merged");
        DjangoGenerationConfig config = DjangoGenerationConfig.builder(outputDir, "simple_app").build();

        new DjangoModelsGenerator().generate(metadata, config);

        assertSnapshot("simple-address-merged", config.getModelsFile());
    }

    @Test
    void simpleAddressIli2cOutputMatchesSnapshot() throws Exception {
        ModelMetadata metadata = new Ili2cModelReader(new File("test-models/SimpleAddressModel.ili"))
            .readMetadata("SimpleAddressModel");
        Path outputDir = tempDir.resolve("simple-address-ili2c");
        DjangoGenerationConfig config = DjangoGenerationConfig.builder(outputDir, "simple_app").build();

        new DjangoModelsGenerator().generate(metadata, config);

        assertSnapshot("simple-address-ili2c", config.getModelsFile());
    }

    @Test
    void structureCompositionOutputMatchesSnapshot() throws Exception {
        ModelMetadata metadata = new Ili2cModelReader(new File("test-models/StructureCompositionCases.ili"))
            .readMetadata("StructureCompositionCases");
        Path outputDir = tempDir.resolve("structure-composition");
        DjangoGenerationConfig config = DjangoGenerationConfig.builder(outputDir, "structure_app").build();

        new DjangoModelsGenerator().generate(metadata, config);

        assertSnapshot("structure-composition", config.getModelsFile());
    }

    private void assertSnapshot(String snapshotCase, Path actualFile) throws Exception {
        Path expectedFile = SNAPSHOT_ROOT.resolve(snapshotCase)
            .resolve(actualFile.getParent().getFileName())
            .resolve(actualFile.getFileName());
        String actual = normalize(Files.readString(actualFile));
        if (Boolean.getBoolean("updateDjangoSnapshots") || "true".equals(System.getenv("UPDATE_DJANGO_SNAPSHOTS"))) {
            Files.createDirectories(expectedFile.getParent());
            Files.writeString(expectedFile, actual);
        }

        assertThat(expectedFile)
            .as("Snapshot should exist: %s", expectedFile)
            .exists();
        assertThat(actual)
            .as("Snapshot mismatch for %s", expectedFile)
            .isEqualTo(normalize(Files.readString(expectedFile)));
    }

    private String normalize(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }
}

package ch.interlis.generator.grails;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.Ili2cModelReader;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsGeneratedOutputSnapshotTest {

    private static final Path SNAPSHOT_ROOT = Path.of("src/test/resources/grails-snapshots");

    @TempDir
    Path tempDir;

    @Test
    void simpleAddressMergedOutputMatchesSnapshots() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedSimpleAddressMetadata();
        Path outputDir = tempDir.resolve("simple-address");
        GenerationConfig config = GenerationConfig.builder(outputDir, "ch.example.simple")
            .domainPackage("ch.example.simple.domain")
            .enumPackage("ch.example.simple.enums")
            .build();

        new GrailsCrudGenerator().generate(metadata, config);
        GeneratedGroovyCompiler.compileGeneratedSources(outputDir);

        assertSnapshots("simple-address", outputDir, List.of(
            "grails-app/domain/ch/example/simple/domain/Address.groovy",
            "grails-app/domain/ch/example/simple/domain/Person.groovy",
            "grails-app/domain/ch/example/simple/domain/PersonAddress.groovy",
            "src/main/groovy/ch/example/simple/enums/AddressStatus.groovy"
        ));
    }

    @Test
    void structureCompositionOutputMatchesSnapshots() throws Exception {
        ModelMetadata metadata = new Ili2cModelReader(new File("test-models/StructureCompositionCases.ili"))
            .readMetadata("StructureCompositionCases");
        Path outputDir = tempDir.resolve("structure-composition");
        GenerationConfig config = GenerationConfig.builder(outputDir, "ch.example.structure")
            .domainPackage("ch.example.structure.domain")
            .enumPackage("ch.example.structure.enums")
            .build();

        new GrailsCrudGenerator().generate(metadata, config);
        GeneratedGroovyCompiler.compileGeneratedSources(outputDir);

        assertSnapshots("structure-composition", outputDir, List.of(
            "grails-app/domain/ch/example/structure/domain/Asset.groovy",
            "grails-app/domain/ch/example/structure/domain/AssetDocument.groovy",
            "grails-app/domain/ch/example/structure/domain/Part.groovy"
        ));
    }

    private void assertSnapshots(String snapshotCase, Path outputDir, List<String> relativePaths)
        throws Exception {
        for (String relativePath : relativePaths) {
            Path actualFile = outputDir.resolve(relativePath);
            Path expectedFile = SNAPSHOT_ROOT.resolve(snapshotCase).resolve(relativePath);

            assertThat(actualFile)
                .as("Generated file should exist: %s", relativePath)
                .exists();
            assertThat(expectedFile)
                .as("Snapshot should exist: %s/%s", snapshotCase, relativePath)
                .exists();
            assertThat(normalize(Files.readString(actualFile)))
                .as("Snapshot mismatch for %s/%s", snapshotCase, relativePath)
                .isEqualTo(normalize(Files.readString(expectedFile)));
        }
    }

    private String normalize(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }
}

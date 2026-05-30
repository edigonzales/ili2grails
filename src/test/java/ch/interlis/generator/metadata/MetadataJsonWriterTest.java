package ch.interlis.generator.metadata;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.Ili2cModelReader;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataJsonWriterTest {

    @ParameterizedTest
    @MethodSource("ili2cGoldenCases")
    void writesDeterministicIli2cGoldenJson(String modelFile,
                                            String modelName,
                                            String goldenFile) throws Exception {
        ModelMetadata metadata = new Ili2cModelReader(new File(modelFile))
            .readMetadata(modelName);

        assertGoldenJson(metadata, goldenFile);
    }

    @ParameterizedTest
    @MethodSource("mergedGoldenCases")
    void writesDeterministicMergedGoldenJson(String goldenFile) throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedSimpleAddressMetadata();

        assertGoldenJson(metadata, goldenFile);
    }

    static Stream<Arguments> ili2cGoldenCases() {
        return Stream.of(
            Arguments.of(
                "test-models/CoreIrTestModel.ili",
                "CoreIrTestModel",
                "CoreIrTestModel.json"
            ),
            Arguments.of(
                "test-models/SimpleAddressModel.ili",
                "SimpleAddressModel",
                "SimpleAddressModel.ili2c.json"
            ),
            Arguments.of(
                "test-models/StructureCompositionCases.ili",
                "StructureCompositionCases",
                "StructureCompositionCases.ili2c.json"
            )
        );
    }

    static Stream<Arguments> mergedGoldenCases() {
        return Stream.of(
            Arguments.of("SimpleAddressModel.merged-h2.json")
        );
    }

    private void assertGoldenJson(ModelMetadata metadata, String goldenFile) throws Exception {
        String json = new MetadataJsonWriter().toJson(metadata);
        String expected = Files.readString(Path.of("src/test/resources/metadata-golden", goldenFile));

        assertThat(json).isEqualTo(expected);
    }
}

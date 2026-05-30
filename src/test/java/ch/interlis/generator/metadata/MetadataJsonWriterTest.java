package ch.interlis.generator.metadata;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.Ili2cModelReader;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataJsonWriterTest {

    @Test
    void writesDeterministicGoldenJson() throws Exception {
        ModelMetadata metadata = new Ili2cModelReader(new File("test-models/CoreIrTestModel.ili"))
            .readMetadata("CoreIrTestModel");

        String json = new MetadataJsonWriter().toJson(metadata);
        String expected = Files.readString(
            Path.of("src/test/resources/metadata-golden/CoreIrTestModel.json")
        );

        assertThat(json).isEqualTo(expected);
    }
}

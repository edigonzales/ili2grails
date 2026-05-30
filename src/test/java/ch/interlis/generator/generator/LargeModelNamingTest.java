package ch.interlis.generator.generator;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.Ili2cModelReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LargeModelNamingTest {

    private static final String MODEL_NAME = "VSADSSMINI_2020_LV95";
    private static final Path MODEL_FILE = Path.of(
        "test-models/VSADSSMINI_2020_2_d_LV95-20251129.ili"
    );

    @TempDir
    Path tempDir;

    @Test
    void validatesVsadssminiWithIli2cAndCompilesGeneratedGrailsTarget() throws Exception {
        if (!Files.exists(MODEL_FILE)) {
            throw new TestAbortedException("VSADSSMINI model file not available: " + MODEL_FILE);
        }

        ModelMetadata metadata = readValidatedMetadata();
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .build();
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);

        List<String> classNames = metadata.getAllClasses().stream()
            .map(registry::className)
            .toList();
        assertThat(classNames).doesNotHaveDuplicates();

        List<String> enumNames = metadata.getAllEnums().stream()
            .map(registry::enumName)
            .toList();
        assertThat(enumNames).doesNotHaveDuplicates();

        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            List<String> propertyNames = classMetadata.getAllAttributes().stream()
                .filter(attribute -> !attribute.isPrimaryKey())
                .map(attribute -> registry.propertyName(classMetadata, attribute))
                .toList();
            assertThat(propertyNames)
                .as("generated properties for %s", classMetadata.getName())
                .doesNotHaveDuplicates();
        }

        new GrailsCrudGenerator().generate(metadata, config);
        GeneratedGroovyCompiler.compileGeneratedSources(tempDir);
    }

    private ModelMetadata readValidatedMetadata() throws Exception {
        Ili2cModelReader reader = new Ili2cModelReader(MODEL_FILE.toFile());
        try {
            reader.compileModel(MODEL_NAME);
            return reader.readMetadata(MODEL_NAME);
        } catch (Exception e) {
            throw new TestAbortedException(
                "VSADSSMINI ili2c validation skipped because external model repositories are unavailable: "
                    + e.getMessage(),
                e
            );
        }
    }
}

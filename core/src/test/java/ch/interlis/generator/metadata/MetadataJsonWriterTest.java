package ch.interlis.generator.metadata;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataFactory;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.reader.Ili2cModelReader;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataJsonWriterTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

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

    @Test
    void writesDeterministicMergedAssociationCasesGoldenJson() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();

        assertGoldenJson(metadata, "AssociationCases.merged-h2.json");
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesCoreTypeAndJavaTargetHintWithoutTopLevelJavaType() throws Exception {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder classMetadata = builder.classBuilder("TestModel.Topic.Sample");
        classMetadata.attribute(new AttributeMetadataBuilder("Name").iliType("TEXT").javaType("String"));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(builder);

        Map<String, Object> root = JSON_MAPPER.readValue(new MetadataJsonWriter().toJson(metadata), Map.class);
        Map<String, Object> writtenAttribute = (Map<String, Object>) ((List<Object>) ((Map<String, Object>) ((List<Object>) root.get("classes"))
            .get(0)).get("attributes")).get(0);

        assertThat(writtenAttribute).containsEntry("coreType", "TEXT");
        assertThat(writtenAttribute).doesNotContainKey("javaType");
        assertThat((Map<String, Object>) writtenAttribute.get("targetHints"))
            .containsEntry("javaType", "String");
        assertThat((Map<String, Object>) writtenAttribute.get("constraints"))
            .containsEntry("required", false)
            .containsEntry("ordered", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesConstraintsObjectAndLegacyConstraintFields() throws Exception {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("TestModel");
        ClassMetadataBuilder classMetadata = builder.classBuilder("TestModel.Topic.Sample");
        classMetadata.attribute(new AttributeMetadataBuilder("Amount")
            .coreType(CoreType.NUMERIC)
            .javaType("java.math.BigDecimal")
            .mandatory(true)
            .minValue("0.00")
            .maxValue("999.99")
            .precision(5)
            .scale(2));
        ModelMetadata metadata = new ModelMetadataFactory().buildValidated(builder);

        Map<String, Object> root = JSON_MAPPER.readValue(new MetadataJsonWriter().toJson(metadata), Map.class);
        Map<String, Object> writtenAttribute = (Map<String, Object>) ((List<Object>) ((Map<String, Object>) ((List<Object>) root.get("classes"))
            .get(0)).get("attributes")).get(0);

        assertThat(writtenAttribute)
            .containsEntry("mandatory", true)
            .containsEntry("minValue", "0.00")
            .containsEntry("maxValue", "999.99")
            .containsEntry("precision", 5)
            .containsEntry("scale", 2);
        assertThat((Map<String, Object>) writtenAttribute.get("constraints"))
            .containsEntry("required", true)
            .containsEntry("minInclusive", "0.00")
            .containsEntry("maxInclusive", "999.99")
            .containsEntry("precision", 5)
            .containsEntry("scale", 2);
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
            ),
            Arguments.of(
                "test-models/AssociationCases.ili",
                "AssociationCases",
                "AssociationCases.ili2c.json"
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
        Path expectedPath = Path.of("core/src/test/resources/metadata-golden", goldenFile);
        if (Boolean.getBoolean("updateMetadataGolden")
            || "true".equals(System.getenv("UPDATE_METADATA_GOLDEN"))) {
            Files.writeString(expectedPath, json);
        }
        String expected = Files.readString(expectedPath);

        assertThat(json).isEqualTo(expected);
    }
}

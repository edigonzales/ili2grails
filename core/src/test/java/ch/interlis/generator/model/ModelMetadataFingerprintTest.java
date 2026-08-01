package ch.interlis.generator.model;

import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core-IR-Fingerprint-Invarianten (Spezifikation §62).
 */
class ModelMetadataFingerprintTest {

    @Test
    void immutableModelFingerprintIsStable() throws Exception {
        ModelMetadata metadata = fixture();
        String first = ModelMetadataFingerprint.of(metadata);
        String second = ModelMetadataFingerprint.of(metadata);
        assertThat(second).isEqualTo(first);
        assertThat(first).matches("[0-9a-f]{64}");
    }

    @Test
    void sameSemanticModelWithDifferentInputOrderProducesSameFingerprint() throws Exception {
        ModelMetadata forward = fixture();
        ModelMetadata reversed = fixtureReversedInsertionOrder();
        assertThat(ModelMetadataFingerprint.of(forward))
            .isEqualTo(ModelMetadataFingerprint.of(reversed));
    }

    @Test
    void differentModelsProduceDifferentFingerprints() throws Exception {
        ModelMetadata modelA = fixture();
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("OtherModel");
        builder.classBuilder("OtherModel.Topic.Thing")
            .tableName("thing")
            .attribute(new AttributeMetadataBuilder("name").javaType("String").maxLength(50));
        ModelMetadata modelB = new ModelMetadataFactory().buildValidated(builder);
        assertThat(ModelMetadataFingerprint.of(modelA))
            .isNotEqualTo(ModelMetadataFingerprint.of(modelB));
    }

    private ModelMetadata fixture() {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("FingerprintModel");
        builder.classBuilder("FingerprintModel.Topic.Alpha")
            .tableName("alpha")
            .attribute(new AttributeMetadataBuilder("name").javaType("String").maxLength(50).mandatory(true))
            .attribute(new AttributeMetadataBuilder("amount").javaType("Integer").minValue("0").maxValue("100"));
        builder.classBuilder("FingerprintModel.Topic.Beta")
            .tableName("beta")
            .attribute(new AttributeMetadataBuilder("label").javaType("String").maxLength(10));
        return new ModelMetadataFactory().buildValidated(builder);
    }

    private ModelMetadata fixtureReversedInsertionOrder() {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model("FingerprintModel");
        builder.classBuilder("FingerprintModel.Topic.Beta")
            .tableName("beta")
            .attribute(new AttributeMetadataBuilder("label").javaType("String").maxLength(10));
        builder.classBuilder("FingerprintModel.Topic.Alpha")
            .tableName("alpha")
            .attribute(new AttributeMetadataBuilder("amount").javaType("Integer").minValue("0").maxValue("100"))
            .attribute(new AttributeMetadataBuilder("name").javaType("String").maxLength(50).mandatory(true));
        return new ModelMetadataFactory().buildValidated(builder);
    }
}

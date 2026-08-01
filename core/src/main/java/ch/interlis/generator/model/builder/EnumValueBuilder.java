package ch.interlis.generator.model.builder;

import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.EnumMetadata.EnumValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable Builder für einen {@link EnumValue}.
 */
public final class EnumValueBuilder {

    private String iliCode;
    private String dispName;
    private int seq;
    private final Map<String, String> labels = new LinkedHashMap<>();

    public EnumValueBuilder(String iliCode, int seq) {
        this.iliCode = Objects.requireNonNull(iliCode, "iliCode");
        this.seq = seq;
    }

    public static EnumValueBuilder from(EnumValue value) {
        EnumValueBuilder builder = new EnumValueBuilder(value.getIliCode(), value.getSeq());
        builder.dispName = value.getDispName();
        builder.labels.putAll(value.getLabels());
        return builder;
    }

    public EnumValueBuilder iliCode(String iliCode) {
        this.iliCode = Objects.requireNonNull(iliCode, "iliCode");
        return this;
    }

    public EnumValueBuilder dispName(String dispName) {
        this.dispName = dispName;
        return this;
    }

    public EnumValueBuilder seq(int seq) {
        this.seq = seq;
        return this;
    }

    public EnumValueBuilder label(String language, String label) {
        this.labels.put(language, label);
        return this;
    }

    public String iliCode() {
        return iliCode;
    }

    public int seq() {
        return seq;
    }

    /** Unvalidierter Build; Abschluss über die ModelMetadataFactory. */
    public EnumValue buildUnchecked() {
        return new EnumValue(iliCode, dispName, seq, labels);
    }
}

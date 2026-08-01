package ch.interlis.generator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Metadaten einer INTERLIS-Enumeration.
 */
public final class EnumMetadata {

    private final String name;
    private final String simpleName;
    private final List<EnumValue> values;
    private final boolean extendable;
    private final String baseEnum;

    /**
     * Immutable Enum-Wert.
     */
    public static final class EnumValue {

        private final String iliCode;
        private final String dispName;
        private final int seq;
        private final Map<String, String> labels;

        public EnumValue(String iliCode, String dispName, int seq, Map<String, String> labels) {
            this.iliCode = Objects.requireNonNull(iliCode, "iliCode");
            this.dispName = dispName;
            this.seq = seq;
            this.labels = labels == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(labels));
        }

        public EnumValue(String iliCode, int seq) {
            this(iliCode, null, seq, Map.of());
        }

        public static ch.interlis.generator.model.builder.EnumValueBuilder builder(String iliCode, int seq) {
            return new ch.interlis.generator.model.builder.EnumValueBuilder(iliCode, seq);
        }

        public ch.interlis.generator.model.builder.EnumValueBuilder toBuilder() {
            return ch.interlis.generator.model.builder.EnumValueBuilder.from(this);
        }

        public String getIliCode() {
            return iliCode;
        }

        public String getDispName() {
            return dispName;
        }

        public int getSeq() {
            return seq;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        @Override
        public String toString() {
            return "EnumValue{" +
                "iliCode='" + iliCode + '\'' +
                ", dispName='" + dispName + '\'' +
                ", seq=" + seq +
                '}';
        }
    }

    public EnumMetadata(String name, String simpleName, List<EnumValue> values,
                 boolean extendable, String baseEnum) {
        this.name = Objects.requireNonNull(name, "name");
        this.simpleName = simpleName;
        this.values = values == null ? List.of() : List.copyOf(values);
        this.extendable = extendable;
        this.baseEnum = baseEnum;
    }

    public static ch.interlis.generator.model.builder.EnumMetadataBuilder builder(String name) {
        return new ch.interlis.generator.model.builder.EnumMetadataBuilder(name);
    }

    public ch.interlis.generator.model.builder.EnumMetadataBuilder toBuilder() {
        return ch.interlis.generator.model.builder.EnumMetadataBuilder.from(this);
    }

    public String getName() {
        return name;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public List<EnumValue> getValues() {
        return values;
    }

    public boolean isExtendable() {
        return extendable;
    }

    public String getBaseEnum() {
        return baseEnum;
    }

    @Override
    public String toString() {
        return "EnumMetadata{" +
            "name='" + name + '\'' +
            ", values=" + values.size() +
            ", extendable=" + extendable +
            '}';
    }
}

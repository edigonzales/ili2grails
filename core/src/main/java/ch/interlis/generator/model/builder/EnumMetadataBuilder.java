package ch.interlis.generator.model.builder;

import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.EnumMetadata.EnumValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mutable Builder für {@link EnumMetadata}.
 */
public final class EnumMetadataBuilder {

    private String name;
    private String simpleName;
    private final List<EnumValueBuilder> valueBuilders = new ArrayList<>();
    private boolean extendable;
    private String baseEnum;

    public EnumMetadataBuilder(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.simpleName = extractSimpleName(name);
    }

    public static EnumMetadataBuilder from(EnumMetadata enumMetadata) {
        EnumMetadataBuilder builder = new EnumMetadataBuilder(enumMetadata.getName());
        builder.simpleName = enumMetadata.getSimpleName();
        for (EnumValue value : enumMetadata.getValues()) {
            builder.valueBuilders.add(EnumValueBuilder.from(value));
        }
        builder.extendable = enumMetadata.isExtendable();
        builder.baseEnum = enumMetadata.getBaseEnum();
        return builder;
    }

    public String name() {
        return name;
    }

    public EnumMetadataBuilder name(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.simpleName = extractSimpleName(name);
        return this;
    }

    public EnumMetadataBuilder value(String iliCode, int seq) {
        this.valueBuilders.add(new EnumValueBuilder(iliCode, seq));
        return this;
    }

    public EnumMetadataBuilder value(EnumValueBuilder value) {
        this.valueBuilders.add(Objects.requireNonNull(value, "value"));
        return this;
    }

    public EnumMetadataBuilder extendable(boolean extendable) {
        this.extendable = extendable;
        return this;
    }

    public EnumMetadataBuilder baseEnum(String baseEnum) {
        this.baseEnum = baseEnum;
        return this;
    }

    public List<EnumValueBuilder> valueBuilders() {
        return java.util.Collections.unmodifiableList(valueBuilders);
    }

    private static String extractSimpleName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    /** Unvalidierter Build; Abschluss über die ModelMetadataFactory. */
    public EnumMetadata buildUnchecked() {
        List<EnumValue> values = new ArrayList<>();
        for (EnumValueBuilder valueBuilder : valueBuilders) {
            values.add(valueBuilder.buildUnchecked());
        }
        values.sort(java.util.Comparator
            .comparingInt(EnumValue::getSeq)
            .thenComparing(EnumValue::getIliCode));
        return new EnumMetadata(name, simpleName, values, extendable, baseEnum);
    }
}

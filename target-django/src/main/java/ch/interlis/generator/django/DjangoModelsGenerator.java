package ch.interlis.generator.django;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Experimental Django/GeoDjango target that consumes only the Core-IR.
 */
public final class DjangoModelsGenerator {

    public void generate(ModelMetadata metadata, DjangoGenerationConfig config) throws IOException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(config, "config");
        Files.createDirectories(config.getAppDir());
        Files.writeString(config.getModelsFile(), render(metadata), StandardCharsets.UTF_8);
    }

    private String render(ModelMetadata metadata) {
        DjangoNameRegistry registry = DjangoNameRegistry.forMetadata(metadata);
        DjangoModelMapper mapper = DjangoModelMapper.forMetadata(metadata, registry);
        List<DjangoModelMapper.DjangoModelMapping> mappings = mapper.mappings();
        boolean usesGeoDjango = mappings.stream().anyMatch(DjangoModelMapper.DjangoModelMapping::usesGeoDjango);

        StringBuilder sb = new StringBuilder();
        sb.append("# Generated from ili2grails Core-IR.\n");
        sb.append(usesGeoDjango
            ? "from django.contrib.gis.db import models\n\n"
            : "from django.db import models\n\n");

        for (EnumMetadata enumMetadata : metadata.getAllEnums().stream()
            .sorted(Comparator.comparing(EnumMetadata::getName, Comparator.nullsLast(String::compareTo)))
            .toList()) {
            renderEnum(sb, enumMetadata, registry);
        }

        for (DjangoModelMapper.DjangoModelMapping mapping : mappings) {
            renderModel(sb, mapping);
        }
        return sb.toString();
    }

    private void renderEnum(StringBuilder sb, EnumMetadata enumMetadata, DjangoNameRegistry registry) {
        sb.append("class ").append(registry.enumChoicesName(enumMetadata)).append("(models.TextChoices):\n");
        if (enumMetadata.getValues().isEmpty()) {
            sb.append("    pass\n\n\n");
            return;
        }
        for (EnumMetadata.EnumValue value : enumMetadata.getValues()) {
            sb.append("    ")
                .append(registry.enumConstantName(enumMetadata, value))
                .append(" = \"")
                .append(py(value.getIliCode()))
                .append("\", \"")
                .append(py(label(value)))
                .append("\"\n");
        }
        sb.append("\n\n");
    }

    private void renderModel(StringBuilder sb, DjangoModelMapper.DjangoModelMapping mapping) {
        ClassMetadata classMetadata = mapping.classMetadata();
        sb.append("class ").append(mapping.className()).append("(models.Model):\n");
        if (mapping.fields().isEmpty() && !hasPhysicalMapping(classMetadata)) {
            sb.append("    pass\n");
        } else {
            for (DjangoModelMapper.DjangoField field : mapping.fields()) {
                sb.append("    ").append(field.render()).append("\n");
            }
        }
        if (hasPhysicalMapping(classMetadata)) {
            sb.append("\n");
            sb.append("    class Meta:\n");
            sb.append("        db_table = \"").append(py(physicalName(classMetadata))).append("\"\n");
            sb.append("        managed = False\n");
        }
        sb.append("\n\n");
    }

    private boolean hasPhysicalMapping(ClassMetadata classMetadata) {
        return hasText(classMetadata.getTableName()) || hasText(classMetadata.getSqlName());
    }

    private String physicalName(ClassMetadata classMetadata) {
        if (hasText(classMetadata.getTableName())) {
            return classMetadata.getTableName();
        }
        return classMetadata.getSqlName();
    }

    private String label(EnumMetadata.EnumValue value) {
        if (value.getDispName() != null && !value.getDispName().isBlank()) {
            return value.getDispName();
        }
        return value.getIliCode();
    }

    private static String py(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

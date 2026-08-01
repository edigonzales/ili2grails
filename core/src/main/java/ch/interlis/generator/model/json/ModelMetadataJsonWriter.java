package ch.interlis.generator.model.json;

import ch.interlis.generator.model.ModelMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Schreibt die kanonische JSON-Repräsentation der immutable Core-IR.
 *
 * <p>Format-Version 2 (Marker {@code metadataFormatVersion: 2}). Für die
 * Legacy-Ausgabe ohne Marker siehe {@link LegacyModelMetadataJsonView}.</p>
 */
public final class ModelMetadataJsonWriter {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final ModelMetadataJsonView view;

    public ModelMetadataJsonWriter() {
        this(new ModelMetadataJsonView());
    }

    public ModelMetadataJsonWriter(ModelMetadataJsonView view) {
        this.view = view;
    }

    public void write(ModelMetadata metadata, Path outputPath) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, toJson(metadata), StandardCharsets.UTF_8);
    }

    public String toJson(ModelMetadata metadata) throws IOException {
        return JSON_MAPPER.writeValueAsString(toDto(metadata)) + System.lineSeparator();
    }

    public Map<String, Object> toDto(ModelMetadata metadata) {
        return view.toDto(metadata);
    }
}

package ch.interlis.generator.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes relationship merge diagnostics as deterministic JSON.
 */
public final class RelationshipMergeJsonWriter {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public void write(RelationshipMergeReport report, Path outputPath) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, toJson(report), StandardCharsets.UTF_8);
    }

    public String toJson(RelationshipMergeReport report) throws IOException {
        return JSON_MAPPER.writeValueAsString(report) + System.lineSeparator();
    }
}

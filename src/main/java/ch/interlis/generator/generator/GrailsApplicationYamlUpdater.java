package ch.interlis.generator.generator;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class GrailsApplicationYamlUpdater {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
        new YAMLFactory().enable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    );

    void ensureDevelopmentDataSourceUrl(Path applicationYamlPath, String jdbcUrl) throws IOException {
        if (!Files.exists(applicationYamlPath)) {
            return;
        }
        String resolvedJdbcUrl = jdbcUrl == null || jdbcUrl.isBlank() ? null : jdbcUrl;
        List<Object> documents = readDocuments(applicationYamlPath);
        boolean changed = updateDevelopmentDataSource(documents, resolvedJdbcUrl);
        if (changed) {
            writeDocuments(applicationYamlPath, documents);
        }
    }

    private List<Object> readDocuments(Path applicationYamlPath) throws IOException {
        List<Object> documents = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(applicationYamlPath, StandardCharsets.UTF_8)) {
            MappingIterator<Object> iterator = YAML_MAPPER.readerFor(Object.class).readValues(reader);
            while (iterator.hasNext()) {
                documents.add(iterator.next());
            }
        }
        return documents;
    }

    private void writeDocuments(Path applicationYamlPath, List<Object> documents) throws IOException {
        try (Writer writer = Files.newBufferedWriter(applicationYamlPath, StandardCharsets.UTF_8);
            SequenceWriter sequenceWriter = YAML_MAPPER.writer().writeValues(writer)) {
            for (Object document : documents) {
                sequenceWriter.write(document);
            }
        }
    }

    private boolean updateDevelopmentDataSource(List<Object> documents, String jdbcUrl) {
        boolean changed = false;
        for (Object document : documents) {
            Map<String, Object> root = asMap(document);
            if (root == null) {
                continue;
            }
            Map<String, Object> environments = asMap(root.get("environments"));
            if (environments == null) {
                continue;
            }
            Map<String, Object> development = asMap(environments.get("development"));
            if (development == null) {
                continue;
            }
            Map<String, Object> dataSource = asMap(development.get("dataSource"));
            if (dataSource == null) {
                continue;
            }
            if (jdbcUrl != null) {
                if (!Objects.equals(jdbcUrl, dataSource.get("url"))) {
                    dataSource.put("url", jdbcUrl);
                    changed = true;
                }
            }
            if (!Objects.equals("none", dataSource.get("dbCreate"))) {
                dataSource.put("dbCreate", "none");
                changed = true;
            }
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}

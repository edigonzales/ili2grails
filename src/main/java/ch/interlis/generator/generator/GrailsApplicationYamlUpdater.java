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
    private static final String POSTGRES_DIALECT = "org.hibernate.dialect.PostgreSQLDialect";
    private static final String H2_DRIVER = "org.h2.Driver";
    private static final String DEFAULT_USERNAME = "edit";
    private static final String DEFAULT_PASSWORD = "secret";

    void ensureDevelopmentDataSourceUrl(Path applicationYamlPath, String jdbcUrl) throws IOException {
        if (!Files.exists(applicationYamlPath)) {
            return;
        }
        String resolvedJdbcUrl = jdbcUrl == null || jdbcUrl.isBlank() ? null : jdbcUrl;
        List<Object> documents = readDocuments(applicationYamlPath);
        boolean changed = updateDevelopmentDataSource(documents, resolvedJdbcUrl);
        changed |= removeRootDataSourceDriver(documents);
        changed |= ensureRootDataSourceCredentials(documents);
        changed |= ensureHibernateDialect(documents);
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
            if (H2_DRIVER.equals(dataSource.get("driverClassName"))) {
                dataSource.remove("driverClassName");
                changed = true;
            }
            if (!Objects.equals("none", dataSource.get("dbCreate"))) {
                dataSource.put("dbCreate", "none");
                changed = true;
            }
        }
        return changed;
    }

    private boolean removeRootDataSourceDriver(List<Object> documents) {
        boolean changed = false;
        for (Object document : documents) {
            Map<String, Object> root = asMap(document);
            if (root == null) {
                continue;
            }
            Map<String, Object> dataSource = asMap(root.get("dataSource"));
            if (dataSource == null) {
                continue;
            }
            if (H2_DRIVER.equals(dataSource.get("driverClassName"))) {
                dataSource.remove("driverClassName");
                changed = true;
            }
        }
        return changed;
    }

    private boolean ensureHibernateDialect(List<Object> documents) {
        boolean changed = false;
        for (Object document : documents) {
            Map<String, Object> root = asMap(document);
            if (root == null) {
                continue;
            }
            Map<String, Object> hibernate = asMap(root.get("hibernate"));
            if (hibernate == null) {
                hibernate = new java.util.LinkedHashMap<>();
                root.put("hibernate", hibernate);
                changed = true;
            }
            if (!Objects.equals(POSTGRES_DIALECT, hibernate.get("dialect"))) {
                hibernate.put("dialect", POSTGRES_DIALECT);
                changed = true;
            }
        }
        return changed;
    }

    private boolean ensureRootDataSourceCredentials(List<Object> documents) {
        boolean changed = false;
        for (Object document : documents) {
            Map<String, Object> root = asMap(document);
            if (root == null) {
                continue;
            }
            Map<String, Object> dataSource = asMap(root.get("dataSource"));
            if (dataSource == null) {
                dataSource = new java.util.LinkedHashMap<>();
                root.put("dataSource", dataSource);
                changed = true;
            }
            if (!Objects.equals(DEFAULT_USERNAME, dataSource.get("username"))) {
                dataSource.put("username", DEFAULT_USERNAME);
                changed = true;
            }
            if (!Objects.equals(DEFAULT_PASSWORD, dataSource.get("password"))) {
                dataSource.put("password", DEFAULT_PASSWORD);
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

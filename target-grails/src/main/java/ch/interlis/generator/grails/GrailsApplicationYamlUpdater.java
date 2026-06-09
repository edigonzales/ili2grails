package ch.interlis.generator.grails;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class GrailsApplicationYamlUpdater {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
        new YAMLFactory().enable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    );
    private static final String POSTGRES_DIALECT = "org.hibernate.dialect.PostgreSQLDialect";
    private static final String POSTGIS_DIALECT = "org.hibernate.spatial.dialect.postgis.PostgisDialect";
    private static final String H2_DRIVER = "org.h2.Driver";

    void ensureDevelopmentDataSourceUrl(Path applicationYamlPath, String jdbcUrl, String schema) throws IOException {
        ensureDevelopmentDataSourceUrl(applicationYamlPath, jdbcUrl, schema, false, 2056);
    }

    void ensureDevelopmentDataSourceUrl(Path applicationYamlPath,
                                        String jdbcUrl,
                                        String schema,
                                        boolean geometryEnabled,
                                        Integer defaultSrid) throws IOException {
        if (!Files.exists(applicationYamlPath)) {
            return;
        }
        String resolvedJdbcUrl = jdbcUrl == null || jdbcUrl.isBlank() ? null : jdbcUrl;
        List<Object> documents = readDocuments(applicationYamlPath);
        boolean changed = updateDevelopmentDataSource(documents, resolvedJdbcUrl, schema);
        changed |= removeRootDataSourceDriver(documents);
        changed |= removeRootDataSourceCredentials(documents);
        changed |= ensureHibernateDialect(documents, geometryEnabled);
        if (geometryEnabled) {
            changed |= ensureGeometryDefaults(documents, defaultSrid);
        }
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

    private boolean updateDevelopmentDataSource(List<Object> documents, String jdbcUrl, String schema) {
        boolean changed = false;
        JdbcConnectionSettings connectionSettings = JdbcConnectionSettings.from(jdbcUrl);
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
            if (connectionSettings.url() != null) {
                String resolvedJdbcUrl = appendCurrentSchema(connectionSettings.url(), schema);
                if (!Objects.equals(resolvedJdbcUrl, dataSource.get("url"))) {
                    dataSource.put("url", resolvedJdbcUrl);
                    changed = true;
                }
            }
            if (connectionSettings.username() != null
                && !Objects.equals("${DB_USERNAME}", dataSource.get("username"))) {
                dataSource.put("username", "${DB_USERNAME}");
                changed = true;
            }
            if (connectionSettings.password() != null
                && !Objects.equals("${DB_PASSWORD}", dataSource.get("password"))) {
                dataSource.put("password", "${DB_PASSWORD}");
                changed = true;
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

    private boolean ensureHibernateDialect(List<Object> documents, boolean geometryEnabled) {
        boolean changed = false;
        String targetDialect = geometryEnabled ? POSTGIS_DIALECT : POSTGRES_DIALECT;
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
            if (!Objects.equals(targetDialect, hibernate.get("dialect"))) {
                hibernate.put("dialect", targetDialect);
                changed = true;
            }
        }
        return changed;
    }

    private boolean ensureGeometryDefaults(List<Object> documents, Integer defaultSrid) {
        boolean changed = false;
        for (Object document : documents) {
            Map<String, Object> root = asMap(document);
            if (root == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> interlis = (Map<String, Object>) root.computeIfAbsent(
                "interlis",
                key -> new java.util.LinkedHashMap<String, Object>()
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> geometry = (Map<String, Object>) interlis.computeIfAbsent(
                "geometry",
                key -> new java.util.LinkedHashMap<String, Object>()
            );
            int resolvedSrid = defaultSrid != null ? defaultSrid : 2056;
            if (!Objects.equals(resolvedSrid, geometry.get("defaultSrid"))) {
                geometry.put("defaultSrid", resolvedSrid);
                changed = true;
            }
            return changed;
        }
        return changed;
    }

    private boolean removeRootDataSourceCredentials(List<Object> documents) {
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
            if (dataSource.remove("username") != null) {
                changed = true;
            }
            if (dataSource.remove("password") != null) {
                changed = true;
            }
        }
        return changed;
    }

    private String appendCurrentSchema(String jdbcUrl, String schema) {
        if (jdbcUrl == null || schema == null || schema.isBlank()) {
            return jdbcUrl;
        }
        if (jdbcUrl.contains("currentSchema=")) {
            return jdbcUrl;
        }
        char separator = jdbcUrl.contains("?") ? '&' : '?';
        return jdbcUrl + separator + "currentSchema=" + schema;
    }

    private record JdbcConnectionSettings(String url, String username, String password) {

        static JdbcConnectionSettings from(String jdbcUrl) {
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                return new JdbcConnectionSettings(null, null, null);
            }
            int queryStart = jdbcUrl.indexOf('?');
            if (queryStart < 0) {
                return new JdbcConnectionSettings(jdbcUrl, null, null);
            }
            String baseUrl = jdbcUrl.substring(0, queryStart);
            String query = jdbcUrl.substring(queryStart + 1);
            String username = null;
            String password = null;
            Map<String, String> retained = new LinkedHashMap<>();
            for (String part : query.split("&")) {
                if (part.isBlank()) {
                    continue;
                }
                int separator = part.indexOf('=');
                String key = separator >= 0 ? part.substring(0, separator) : part;
                String value = separator >= 0 ? part.substring(separator + 1) : "";
                if ("user".equalsIgnoreCase(key) || "username".equalsIgnoreCase(key)) {
                    username = value;
                } else if ("password".equalsIgnoreCase(key)) {
                    password = value;
                } else if (!"dbSchema".equalsIgnoreCase(key)) {
                    retained.put(key, value);
                }
            }
            String sanitizedUrl = baseUrl;
            if (!retained.isEmpty()) {
                sanitizedUrl += "?" + retained.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining("&"));
            }
            return new JdbcConnectionSettings(sanitizedUrl, username, password);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}

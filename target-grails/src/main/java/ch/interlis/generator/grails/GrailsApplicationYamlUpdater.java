package ch.interlis.generator.grails;

import com.fasterxml.jackson.databind.MappingIterator;
import ch.interlis.generator.grails.project.plan.TextFileEdit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GrailsApplicationYamlUpdater {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
        new YAMLFactory().enable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    );
    private static final String POSTGRES_DIALECT = "org.hibernate.dialect.PostgreSQLDialect";
    private static final String POSTGIS_DIALECT = "org.hibernate.spatial.dialect.postgis.PostgisDialect";
    private static final String H2_DRIVER = "org.h2.Driver";

    /**
     * Reine Planungsfunktion (Spezifikation §41.5): kein Write.
     */
    public TextFileEdit plan(Path relativePath,
                             String existingContent,
                             String jdbcUrl,
                             String schema,
                             boolean geometryEnabled,
                             Integer defaultSrid,
                             String language) {
        if (existingContent == null) {
            return new TextFileEdit(relativePath, null, false, "application.yml missing");
        }
        String resolvedJdbcUrl = jdbcUrl == null || jdbcUrl.isBlank() ? null : jdbcUrl;
        List<Object> documents = readDocumentsFromString(existingContent);
        boolean changed = updateDevelopmentDataSource(documents, resolvedJdbcUrl, schema);
        changed |= removeRootDataSourceDriver(documents);
        changed |= removeRootDataSourceCredentials(documents);
        changed |= ensureHibernateDialect(documents, geometryEnabled);
        changed |= ensureUiLanguage(documents, language);
        changed |= ensureStandardLocale(documents, language);
        if (geometryEnabled) {
            changed |= ensureGeometryDefaults(documents, defaultSrid);
        }
        if (!changed) {
            return new TextFileEdit(relativePath, existingContent, false, "data source unchanged");
        }
        return new TextFileEdit(relativePath, renderDocumentsToString(documents), true,
            "data source and dialect");
    }

    private List<Object> readDocumentsFromString(String content) {
        try {
            List<Object> documents = new ArrayList<>();
            ObjectReader reader = YAML_MAPPER.readerFor(Object.class);
            try (com.fasterxml.jackson.databind.MappingIterator<Object> iterator =
                reader.readValues(content)) {
                iterator.forEachRemaining(documents::add);
            }
            return documents;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse application.yml: " + e.getMessage(), e);
        }
    }

    private String renderDocumentsToString(List<Object> documents) {
        try {
            java.io.StringWriter writer = new java.io.StringWriter();
            try (SequenceWriter sequenceWriter = YAML_MAPPER.writer().writeValues(writer)) {
                for (Object document : documents) {
                    sequenceWriter.write(document);
                }
            }
            return writer.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot render application.yml: " + e.getMessage(), e);
        }
    }

    private boolean ensureUiLanguage(List<Object> documents, String language) {
        Map<String, Object> root = firstRootDocument(documents);
        if (root == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> ili2grails = (Map<String, Object>) root.computeIfAbsent(
            "ili2grails",
            key -> new LinkedHashMap<String, Object>()
        );
        String resolvedLanguage = language == null || language.isBlank()
            ? GenerationConfig.LANGUAGE_DE_CH
            : language;
        if (Objects.equals(resolvedLanguage, ili2grails.get("language"))) {
            return false;
        }
        ili2grails.put("language", resolvedLanguage);
        return true;
    }

    private boolean ensureStandardLocale(List<Object> documents, String language) {
        Map<String, Object> root = firstRootDocument(documents);
        if (root == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> spring = (Map<String, Object>) root.computeIfAbsent(
            "spring",
            key -> new LinkedHashMap<String, Object>()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> web = (Map<String, Object>) spring.computeIfAbsent(
            "web",
            key -> new LinkedHashMap<String, Object>()
        );
        String resolvedLanguage = language == null || language.isBlank()
            ? GenerationConfig.LANGUAGE_DE_CH
            : language;
        boolean changed = !Objects.equals(resolvedLanguage, web.get("locale"));
        if (changed) {
            web.put("locale", resolvedLanguage);
        }
        if (!Objects.equals("fixed", web.get("locale-resolver"))) {
            web.put("locale-resolver", "fixed");
            changed = true;
        }
        return changed;
    }

    private boolean updateDevelopmentDataSource(List<Object> documents, String jdbcUrl, String schema) {
        boolean changed = false;
        JdbcConnectionSettings connectionSettings = JdbcConnectionSettings.from(jdbcUrl);
        boolean developmentUpdated = false;
        for (Object document : documents) {
            Map<String, Object> root = asMap(document);
            if (root == null) {
                continue;
            }
            Map<String, Object> environments = asMap(root.get("environments"));
            Map<String, Object> development = environments == null ? null : asMap(environments.get("development"));
            Map<String, Object> dataSource = development == null ? null : asMap(development.get("dataSource"));
            if (dataSource == null) {
                continue;
            }
            developmentUpdated = true;
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
        if (!developmentUpdated) {
            Map<String, Object> root = firstRootDocument(documents);
            if (root != null) {
                Map<String, Object> dataSource = environmentDataSource(root, "development");
                if (connectionSettings.url() != null) {
                    dataSource.put("url", appendCurrentSchema(connectionSettings.url(), schema));
                    changed = true;
                }
                if (connectionSettings.username() != null) {
                    dataSource.put("username", "${DB_USERNAME}");
                    changed = true;
                }
                if (connectionSettings.password() != null) {
                    dataSource.put("password", "${DB_PASSWORD}");
                    changed = true;
                }
                dataSource.put("dbCreate", "none");
            }
        }
        changed |= ensureProductionDataSource(documents);
        return changed;
    }

    private boolean ensureProductionDataSource(List<Object> documents) {
        boolean changed = false;
        Map<String, Object> root = firstRootDocument(documents);
        if (root == null) {
            return false;
        }
        Map<String, Object> dataSource = environmentDataSource(root, "production");
        if (!Objects.equals("${DB_URL}", dataSource.get("url"))) {
            dataSource.put("url", "${DB_URL}");
            changed = true;
        }
        if (!Objects.equals("${DB_USERNAME}", dataSource.get("username"))) {
            dataSource.put("username", "${DB_USERNAME}");
            changed = true;
        }
        if (!Objects.equals("${DB_PASSWORD}", dataSource.get("password"))) {
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
        Map<String, Object> root = firstRootDocument(documents);
        if (root != null) {
            Map<String, Object> production = environment(root, "production");
            Map<String, Object> productionHibernate = asMap(production.get("hibernate"));
            if (productionHibernate == null) {
                productionHibernate = new java.util.LinkedHashMap<>();
                production.put("hibernate", productionHibernate);
                changed = true;
            }
            if (!Objects.equals(targetDialect, productionHibernate.get("dialect"))) {
                productionHibernate.put("dialect", targetDialect);
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

    private Map<String, Object> environmentDataSource(Map<String, Object> root, String environmentName) {
        Map<String, Object> environment = environment(root, environmentName);
        Map<String, Object> dataSource = asMap(environment.get("dataSource"));
        if (dataSource == null) {
            dataSource = new java.util.LinkedHashMap<>();
            environment.put("dataSource", dataSource);
        }
        return dataSource;
    }

    private Map<String, Object> environment(Map<String, Object> root, String environmentName) {
        Map<String, Object> environments = asMap(root.get("environments"));
        if (environments == null) {
            environments = new java.util.LinkedHashMap<>();
            root.put("environments", environments);
        }
        Map<String, Object> environment = asMap(environments.get(environmentName));
        if (environment == null) {
            environment = new java.util.LinkedHashMap<>();
            environments.put(environmentName, environment);
        }
        return environment;
    }

    private Map<String, Object> firstRootDocument(List<Object> documents) {
        for (Object document : documents) {
            Map<String, Object> root = asMap(document);
            if (root != null) {
                return root;
            }
        }
        return null;
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

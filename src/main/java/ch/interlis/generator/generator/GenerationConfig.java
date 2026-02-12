package ch.interlis.generator.generator;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Konfiguration für die Grails CRUD-Generierung.
 */
public class GenerationConfig {

    public static final String UI_THEME_DEFAULT = "default";
    public static final String UI_THEME_CARBON = "carbon";
    public static final String MAP_EDITOR_NONE = "none";
    public static final String MAP_EDITOR_OPENLAYERS = "openlayers";

    private final Path outputDir;
    private final String basePackage;
    private final String domainPackage;
    private final String controllerPackage;
    private final String enumPackage;
    private final String jdbcUrl;
    private final String schema;
    private final String uiTheme;
    private final String mapEditor;
    private final Integer defaultSrid;
    private final boolean geometryEnabled;

    private GenerationConfig(Builder builder) {
        this.outputDir = builder.outputDir;
        this.basePackage = builder.basePackage;
        this.domainPackage = builder.domainPackage;
        this.controllerPackage = builder.controllerPackage;
        this.enumPackage = builder.enumPackage;
        this.jdbcUrl = builder.jdbcUrl;
        this.schema = builder.schema;
        this.uiTheme = builder.uiTheme;
        this.mapEditor = builder.mapEditor;
        this.defaultSrid = builder.defaultSrid;
        this.geometryEnabled = builder.geometryEnabled;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public String getDomainPackage() {
        return domainPackage;
    }

    public String getControllerPackage() {
        return controllerPackage;
    }

    public String getEnumPackage() {
        return enumPackage;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getSchema() {
        return schema;
    }

    public String getUiTheme() {
        return uiTheme;
    }

    public String getMapEditor() {
        return mapEditor;
    }

    public Integer getDefaultSrid() {
        return defaultSrid;
    }

    public boolean isGeometryEnabled() {
        return geometryEnabled;
    }

    public static Builder builder(Path outputDir, String basePackage) {
        return new Builder(outputDir, basePackage);
    }

    public static class Builder {
        private final Path outputDir;
        private final String basePackage;
        private String domainPackage;
        private String controllerPackage;
        private String enumPackage;
        private String jdbcUrl;
        private String schema;
        private String uiTheme;
        private String mapEditor;
        private Integer defaultSrid;
        private boolean geometryEnabled;

        public Builder(Path outputDir, String basePackage) {
            this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
            this.basePackage = Objects.requireNonNull(basePackage, "basePackage");
            this.domainPackage = basePackage;
            this.controllerPackage = basePackage;
            this.enumPackage = basePackage + ".enums";
            this.uiTheme = UI_THEME_DEFAULT;
            this.mapEditor = MAP_EDITOR_NONE;
            this.defaultSrid = 2056;
            this.geometryEnabled = false;
        }

        public Builder jdbcUrl(String jdbcUrl) {
            this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
            return this;
        }

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public Builder domainPackage(String domainPackage) {
            this.domainPackage = domainPackage;
            return this;
        }

        public Builder controllerPackage(String controllerPackage) {
            this.controllerPackage = controllerPackage;
            return this;
        }

        public Builder enumPackage(String enumPackage) {
            this.enumPackage = enumPackage;
            return this;
        }

        public Builder uiTheme(String uiTheme) {
            this.uiTheme = Objects.requireNonNull(uiTheme, "uiTheme");
            return this;
        }

        public Builder mapEditor(String mapEditor) {
            this.mapEditor = Objects.requireNonNull(mapEditor, "mapEditor");
            return this;
        }

        public Builder defaultSrid(Integer defaultSrid) {
            this.defaultSrid = Objects.requireNonNull(defaultSrid, "defaultSrid");
            return this;
        }

        public Builder geometryEnabled(boolean geometryEnabled) {
            this.geometryEnabled = geometryEnabled;
            return this;
        }

        public GenerationConfig build() {
            return new GenerationConfig(this);
        }
    }
}

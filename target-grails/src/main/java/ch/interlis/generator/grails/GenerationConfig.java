package ch.interlis.generator.grails;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Konfiguration für die Grails CRUD-Generierung.
 */
public class GenerationConfig {

    public static final String UI_THEME_DEFAULT = "default";
    public static final String UI_THEME_BOOTSTRAP = "bootstrap";
    public static final String MAP_EDITOR_NONE = "none";
    public static final String MAP_EDITOR_OPENLAYERS = "openlayers";

    public static final String ASSOCIATION_UI_OFF = "off";
    public static final String ASSOCIATION_UI_READ_ONLY = "read-only";
    public static final String ASSOCIATION_UI_EDITABLE = "editable";
    public static final String ASSOCIATION_UI_AUTO = "auto";

    public static final String ASSOCIATION_NAVIGATION_AUTO = "auto";
    public static final String ASSOCIATION_NAVIGATION_SHOW = "show";
    public static final String ASSOCIATION_NAVIGATION_HIDE = "hide";

    private static final int ASSOCIATION_PAGE_SIZE_MIN = 1;
    private static final int ASSOCIATION_PAGE_SIZE_MAX = 100;

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
    private final String associationUiMode;
    private final int associationPageSize;
    private final boolean hideContextualAssociationControllers;
    private final String associationNavigation;

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
        this.associationUiMode = builder.associationUiMode;
        this.associationPageSize = builder.associationPageSize;
        this.hideContextualAssociationControllers = builder.hideContextualAssociationControllers;
        this.associationNavigation = builder.associationNavigation;
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

    public String getAssociationUiMode() {
        return associationUiMode;
    }

    public int getAssociationPageSize() {
        return associationPageSize;
    }

    public boolean isHideContextualAssociationControllers() {
        return hideContextualAssociationControllers;
    }

    public String getAssociationNavigation() {
        return associationNavigation;
    }

    public boolean isAssociationUiEnabled() {
        return !ASSOCIATION_UI_OFF.equals(associationUiMode);
    }

    public boolean isAssociationUiEditable() {
        return ASSOCIATION_UI_EDITABLE.equals(associationUiMode)
            || ASSOCIATION_UI_AUTO.equals(associationUiMode);
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
        private String associationUiMode;
        private int associationPageSize;
        private boolean hideContextualAssociationControllers;
        private String associationNavigation;

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
            this.associationUiMode = ASSOCIATION_UI_AUTO;
            this.associationPageSize = 10;
            this.hideContextualAssociationControllers = true;
            this.associationNavigation = ASSOCIATION_NAVIGATION_AUTO;
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

        public Builder associationUiMode(String mode) {
            Objects.requireNonNull(mode, "associationUiMode");
            if (!ASSOCIATION_UI_OFF.equals(mode)
                && !ASSOCIATION_UI_READ_ONLY.equals(mode)
                && !ASSOCIATION_UI_EDITABLE.equals(mode)
                && !ASSOCIATION_UI_AUTO.equals(mode)) {
                throw new IllegalArgumentException("Unsupported associationUiMode: " + mode);
            }
            this.associationUiMode = mode;
            return this;
        }

        public Builder associationPageSize(int pageSize) {
            if (pageSize < ASSOCIATION_PAGE_SIZE_MIN || pageSize > ASSOCIATION_PAGE_SIZE_MAX) {
                throw new IllegalArgumentException(
                    "associationPageSize must be between " + ASSOCIATION_PAGE_SIZE_MIN
                        + " and " + ASSOCIATION_PAGE_SIZE_MAX + ": " + pageSize);
            }
            this.associationPageSize = pageSize;
            return this;
        }

        public Builder hideContextualAssociationControllers(boolean hide) {
            this.hideContextualAssociationControllers = hide;
            return this;
        }

        public Builder associationNavigation(String navigation) {
            Objects.requireNonNull(navigation, "associationNavigation");
            if (!ASSOCIATION_NAVIGATION_AUTO.equals(navigation)
                && !ASSOCIATION_NAVIGATION_SHOW.equals(navigation)
                && !ASSOCIATION_NAVIGATION_HIDE.equals(navigation)) {
                throw new IllegalArgumentException("Unsupported associationNavigation: " + navigation);
            }
            this.associationNavigation = navigation;
            return this;
        }

        public GenerationConfig build() {
            return new GenerationConfig(this);
        }
    }
}

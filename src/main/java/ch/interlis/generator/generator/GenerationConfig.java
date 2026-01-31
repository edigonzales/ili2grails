package ch.interlis.generator.generator;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Konfiguration für die Grails CRUD-Generierung.
 */
public class GenerationConfig {

    private final Path outputDir;
    private final String basePackage;
    private final String domainPackage;
    private final String controllerPackage;
    private final String enumPackage;
    private final String jdbcUrl;

    private GenerationConfig(Builder builder) {
        this.outputDir = builder.outputDir;
        this.basePackage = builder.basePackage;
        this.domainPackage = builder.domainPackage;
        this.controllerPackage = builder.controllerPackage;
        this.enumPackage = builder.enumPackage;
        this.jdbcUrl = builder.jdbcUrl;
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

        public Builder(Path outputDir, String basePackage) {
            this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
            this.basePackage = Objects.requireNonNull(basePackage, "basePackage");
            this.domainPackage = basePackage;
            this.controllerPackage = basePackage;
            this.enumPackage = basePackage + ".enums";
        }

        public Builder jdbcUrl(String jdbcUrl) {
            this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
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

        public GenerationConfig build() {
            return new GenerationConfig(this);
        }
    }
}

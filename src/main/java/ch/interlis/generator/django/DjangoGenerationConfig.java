package ch.interlis.generator.django;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for the experimental Django/GeoDjango model target.
 */
public final class DjangoGenerationConfig {

    private final Path outputDir;
    private final String appName;

    private DjangoGenerationConfig(Builder builder) {
        this.outputDir = builder.outputDir;
        this.appName = builder.appName;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public String getAppName() {
        return appName;
    }

    public Path getAppDir() {
        return outputDir.resolve(appName);
    }

    public Path getModelsFile() {
        return getAppDir().resolve("models.py");
    }

    public static Builder builder(Path outputDir, String appName) {
        return new Builder(outputDir, appName);
    }

    public static final class Builder {
        private final Path outputDir;
        private final String appName;

        private Builder(Path outputDir, String appName) {
            this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
            this.appName = requireNonBlank(appName, "appName");
        }

        public DjangoGenerationConfig build() {
            return new DjangoGenerationConfig(this);
        }

        private static String requireNonBlank(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}

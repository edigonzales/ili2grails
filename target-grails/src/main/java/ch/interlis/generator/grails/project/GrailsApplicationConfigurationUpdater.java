package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.GenerationConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;

/**
 * Appends the minimal application configuration (locale resolver) to the
 * Grails project. The locale {@code resources.groovy} block stays
 * generator-managed; application configuration outside marked blocks is never
 * touched.
 */
public final class GrailsApplicationConfigurationUpdater {

    private static final String OVERLAY_ROOT = "grails/overlays/bootstrap-openlayers/";

    public void update(Path grailsProjectDir, GenerationConfig config) throws IOException {
        Objects.requireNonNull(grailsProjectDir, "grailsProjectDir");
        Objects.requireNonNull(config, "config");
        copyLocaleConfiguration(grailsProjectDir, config);
    }

    private void copyLocaleConfiguration(Path grailsProjectDir, GenerationConfig config) throws IOException {
        String resourcePath = OVERLAY_ROOT + "grails-app/conf/spring/resources.groovy";
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing overlay resource: " + resourcePath);
            }
            String generated = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                .replace("Locale.forLanguageTag(\"de-CH\")",
                    "Locale.forLanguageTag(\"" + config.getLanguage() + "\")");
            Path target = grailsProjectDir.resolve("grails-app/conf/spring/resources.groovy");
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                Files.writeString(target, generated, StandardCharsets.UTF_8);
                return;
            }
            String existing = Files.readString(target, StandardCharsets.UTF_8);
            if (existing.contains("localeResolver(")) {
                String updated = existing.replaceAll(
                    "Locale\\.forLanguageTag\\(\\\"[^\\\"]+\\\"\\)",
                    Matcher.quoteReplacement("Locale.forLanguageTag(\"" + config.getLanguage() + "\")")
                );
                if (!updated.equals(existing)) {
                    Files.writeString(target, updated, StandardCharsets.UTF_8);
                }
                return;
            }
            int closingBrace = existing.lastIndexOf('}');
            if (closingBrace < 0) {
                throw new IOException("Cannot extend Spring resources configuration: missing closing brace");
            }
            String insertion = "    localeResolver(org.springframework.web.servlet.i18n.FixedLocaleResolver, "
                + "java.util.Locale.forLanguageTag(\"" + config.getLanguage() + "\"))\n";
            String updated = existing.substring(0, closingBrace) + insertion + existing.substring(closingBrace);
            Files.writeString(target, updated, StandardCharsets.UTF_8);
        }
    }
}

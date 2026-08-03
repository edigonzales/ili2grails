package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.GenerationConfig;
import ch.interlis.generator.grails.project.plan.TextFileEdit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Appends the minimal application configuration (locale resolver) to the
 * Grails project. The locale {@code resources.groovy} block stays
 * generator-managed; application configuration outside marked blocks is never
 * touched.
 */
public final class GrailsApplicationConfigurationUpdater {

    private static final String OVERLAY_ROOT = "grails/overlays/bootstrap-openlayers/";

    /**
     * Reine Planungsfunktion (Spezifikation §41.5): kein Write.
     */
    public TextFileEdit plan(Path relativePath, String existingContent, GenerationConfig config) {
        String resourcePath = OVERLAY_ROOT + "grails-app/conf/spring/resources.groovy";
        String overlayDefault;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing overlay resource: " + resourcePath);
            }
            overlayDefault = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                .replace("Locale.forLanguageTag(\"de-CH\")",
                    "Locale.forLanguageTag(\"" + config.getLanguage() + "\")");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read overlay resource " + resourcePath, e);
        }
        if (existingContent == null) {
            return new TextFileEdit(relativePath, overlayDefault, true, "locale configuration created");
        }
        String existing = existingContent;
        if (!existing.contains("localeResolver(")) {
            int closingBrace = existing.lastIndexOf('}');
            if (closingBrace < 0) {
                throw new IllegalStateException(
                    "Cannot extend Spring resources configuration: missing closing brace");
            }
            String insertion = "    localeResolver(org.springframework.web.servlet.i18n.FixedLocaleResolver, "
                + "java.util.Locale.forLanguageTag(\"" + config.getLanguage() + "\"))\n";
            String updated = existing.substring(0, closingBrace) + insertion + existing.substring(closingBrace);
            return new TextFileEdit(relativePath, updated, true, "locale resolver");
        }
        String updated = existing.replaceAll(
            "Locale\\.forLanguageTag\\(\\\"[^\\\"]+\\\"\\)",
            java.util.regex.Matcher.quoteReplacement(
                "Locale.forLanguageTag(\"" + config.getLanguage() + "\")"));
        return new TextFileEdit(relativePath, updated, !updated.equals(existing),
            "locale language");
    }

}

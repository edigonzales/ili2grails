package ch.interlis.generator.grails.project;

import java.util.ArrayList;
import java.util.List;

/**
 * Explicit ownership manifest of files in a generated Grails project.
 *
 * <p>The manifest is the single source of truth for the overlay migration:
 * runtime artefacts are classified {@link GrailsProjectFileOwner#RUNTIME_PLUGIN}
 * (never copied), templates are {@code GENERATOR_MANAGED}, and the pre-P1
 * copies are {@code LEGACY_RUNTIME} with {@code deleteWhenMigrating}.</p>
 */
public final class GrailsProjectFileOwnership {

    private static final String RUNTIME_PACKAGE = "ch/interlis/generator/grails/runtime/";

    private GrailsProjectFileOwnership() {
    }

    public static List<GrailsProjectFileRule> rules() {
        List<GrailsProjectFileRule> rules = new ArrayList<>();

        // Runtime plugin artefacts: never copied into the application.
        rules.add(pluginOwned("src/main/groovy/" + RUNTIME_PACKAGE));
        rules.add(pluginOwned("grails-app/services/" + RUNTIME_PACKAGE));
        rules.add(pluginOwned("grails-app/controllers/" + RUNTIME_PACKAGE + "InterlisUiController.groovy"));
        rules.add(pluginOwned("grails-app/taglib/" + RUNTIME_PACKAGE + "InterlisUiTagLib.groovy"));
        rules.add(pluginOwned("grails-app/views/interlisUi/"));
        rules.add(new GrailsProjectFileRule("grails-app/i18n/messages_de_CH.properties",
            GrailsProjectFileOwner.RUNTIME_PLUGIN, false, true));
        rules.add(new GrailsProjectFileRule("grails-app/i18n/messages_en.properties",
            GrailsProjectFileOwner.RUNTIME_PLUGIN, false, true));
        rules.addAll(List.of(
            pluginOwned("grails-app/assets/javascripts/"),
            pluginOwned("grails-app/assets/stylesheets/"),
            pluginOwned("grails-app/assets/fonts/")));

        // Generator-managed scaffolding templates.
        rules.add(new GrailsProjectFileRule("src/main/templates/scaffolding/",
            GrailsProjectFileOwner.GENERATOR_MANAGED, true, false));

        // Application-owned files (never overwritten). Ein lokales
        // grails-app/views/layouts/main.gsp ist immer APPLICATION_OWNED
        // (P2-D003): das Plugin liefert sein Default-Layout aus dem Plugin-JAR.
        // Nur ein per SHA-256 bekanntes pre-P1-Legacy-Exemplar darf über den
        // Legacy-Migrationspfad entfernt werden.
        rules.add(new GrailsProjectFileRule("grails-app/views/layouts/main.gsp",
            GrailsProjectFileOwner.APPLICATION_OWNED, false, false));

        // Legacy runtime copies (pre-P1): safe deletion after hash match.
        rules.add(new GrailsProjectFileRule(RUNTIME_PACKAGE, GrailsProjectFileOwner.LEGACY_RUNTIME, false, true));
        return List.copyOf(rules);
    }

    private static GrailsProjectFileRule pluginOwned(String pathPrefix) {
        return new GrailsProjectFileRule(pathPrefix, GrailsProjectFileOwner.RUNTIME_PLUGIN, false, true);
    }

    private static List<GrailsProjectFileRule> pluginOwned(String... pathPrefixes) {
        List<GrailsProjectFileRule> rules = new ArrayList<>();
        for (String prefix : pathPrefixes) {
            rules.add(pluginOwned(prefix));
        }
        return rules;
    }
}

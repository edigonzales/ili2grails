package ch.interlis.generator.grails;

import java.nio.file.Path;

/**
 * Central source path resolution for plugin-owned artefacts (views, assets,
 * i18n, runtime classes) in generator tests. The runtime moved out of the
 * generator overlay into the {@code grails-runtime} plugin module.
 */
final class PluginSourcePaths {

    private static final Path PLUGIN_ROOT = Path.of("grails-runtime/grails-app");
    private static final Path RUNTIME_ROOT = Path.of(
        "grails-runtime/src/main/groovy/ch/interlis/generator/grails/runtime");

    private PluginSourcePaths() {
    }

    static Path view(String relativePath) {
        return PLUGIN_ROOT.resolve("views").resolve(relativePath);
    }

    static Path asset(String relativePath) {
        return PLUGIN_ROOT.resolve("assets").resolve(relativePath);
    }

    static Path i18n(String fileName) {
        return PLUGIN_ROOT.resolve("i18n").resolve(fileName);
    }

    static Path tagLib(String className) {
        return PLUGIN_ROOT.resolve("taglib/ch/interlis/generator/grails/runtime")
            .resolve(className + ".groovy");
    }

    static Path controller(String className) {
        return PLUGIN_ROOT.resolve("controllers/ch/interlis/generator/grails/runtime")
            .resolve(className + ".groovy");
    }

    static Path service(String className) {
        return PLUGIN_ROOT.resolve("services/ch/interlis/generator/grails/runtime")
            .resolve(className + ".groovy");
    }

    static Path runtimeSource(String className) {
        return RUNTIME_ROOT.resolve(className + ".groovy");
    }

    static Path runtimeRoot() {
        return RUNTIME_ROOT;
    }
}

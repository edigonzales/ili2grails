package ch.interlis.generator.grails;

import java.nio.file.Path;

/**
 * Central source path resolution for the runtime plugin sources in unit
 * tests. The runtime lives in the {@code grails-runtime} module; tests must
 * not reference the generator overlay anymore.
 */
final class RuntimeSourcePaths {

    private static final Path RUNTIME_ROOT = Path.of(
        "grails-runtime/src/main/groovy/ch/interlis/generator/grails/runtime");
    private static final Path SERVICES_ROOT = Path.of(
        "grails-runtime/grails-app/services/ch/interlis/generator/grails/runtime");

    private RuntimeSourcePaths() {
    }

    static Path runtimeSource(String className) {
        return RUNTIME_ROOT.resolve(className + ".groovy");
    }

    static Path runtimeRoot() {
        return RUNTIME_ROOT;
    }

    static Path serviceSource(String className) {
        return SERVICES_ROOT.resolve(className + ".groovy");
    }

    /** Source of the reflection accessor used by the runtime for the generated registries. */
    static Path generatedRegistryAccessorSource() {
        return runtimeSource("GeneratedRegistryAccessor");
    }
}

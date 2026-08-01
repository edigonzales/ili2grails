package ch.interlis.generator.grails.runtime.api;

/**
 * Version contract between the generator, the generated application and the
 * runtime plugin.
 *
 * <p>The plugin verifies at startup that the runtime API on the classpath is
 * compatible; incompatible versions fail with a readable message instead of
 * silently using a possibly incompatible plugin.</p>
 */
public final class RuntimeVersionContract {

    /** Version of the runtime API contract. Bump on incompatible changes. */
    public static final String RUNTIME_API_VERSION = "1";

    /** Plugin artifact coordinates produced by the build. */
    public static final String RUNTIME_GROUP = "ch.interlis.generator";
    public static final String RUNTIME_ARTIFACT = "ili2grails-runtime";

    private RuntimeVersionContract() {
    }
}

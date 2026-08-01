package ch.interlis.generator.grails.project;

/**
 * Ownership classification of files inside a generated Grails project.
 *
 * <p>The ownership matrix decides what the generator may overwrite, what the
 * runtime plugin owns and what belongs to the application. No application-owned
 * file is ever overwritten by the generator.</p>
 */
public enum GrailsProjectFileOwner {
    /** Provided by the {@code ili2grails-runtime} plugin. */
    RUNTIME_PLUGIN,
    /** Written by the generator on every run. */
    GENERATOR_MANAGED,
    /** Belongs to the application; never overwritten automatically. */
    APPLICATION_OWNED,
    /** Copied by pre-P1 generators; subject to safe legacy migration. */
    LEGACY_RUNTIME
}

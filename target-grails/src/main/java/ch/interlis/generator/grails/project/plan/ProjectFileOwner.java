package ch.interlis.generator.grails.project.plan;

import ch.interlis.generator.grails.project.GrailsProjectFileOwner;

/**
 * Owner einer geplanten Projektdatei. Verwendet bewusst den vorhandenen
 * {@link GrailsProjectFileOwner} als einzige Owner-Wahrheit (P2-D010).
 */
public final class ProjectFileOwner {

    private ProjectFileOwner() {
    }

    public static final GrailsProjectFileOwner GENERATOR_MANAGED =
        GrailsProjectFileOwner.GENERATOR_MANAGED;
    public static final GrailsProjectFileOwner APPLICATION_OWNED =
        GrailsProjectFileOwner.APPLICATION_OWNED;
    public static final GrailsProjectFileOwner RUNTIME_PLUGIN =
        GrailsProjectFileOwner.RUNTIME_PLUGIN;
    public static final GrailsProjectFileOwner LEGACY_RUNTIME =
        GrailsProjectFileOwner.LEGACY_RUNTIME;
}

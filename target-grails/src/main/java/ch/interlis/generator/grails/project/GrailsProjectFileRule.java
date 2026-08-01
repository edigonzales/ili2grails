package ch.interlis.generator.grails.project;

/**
 * Single ownership rule for a generated project file.
 */
public record GrailsProjectFileRule(
    String relativePath,
    GrailsProjectFileOwner owner,
    boolean overwriteAllowed,
    boolean deleteWhenMigrating
) {
}

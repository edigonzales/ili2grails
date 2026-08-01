package ch.interlis.generator.grails.project;

/**
 * Legacy migration policy.
 */
public enum LegacyMigrationPolicy {
    /** Delete known-unmodified copies; block on modified files. */
    STRICT,
    /** Report only; never delete. */
    REPORT_ONLY
}

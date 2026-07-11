package ch.interlis.generator.grails;

/**
 * Describes how new association instances can be created from a fixed participant perspective.
 */
public enum AssociationCreateMode {

    /** No safe create strategy. */
    NONE,

    /** Inline quick-link creation for safe binary link associations. */
    QUICK,

    /** Contextual creation via the association domain scaffold form. */
    CONTEXTUAL_FORM
}

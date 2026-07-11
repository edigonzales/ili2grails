package ch.interlis.generator.grails;

/**
 * Describes how an association is presented from the perspective of a fixed participant role.
 */
public enum AssociationPresentationKind {

    /** Binary, no own attributes, safely writable inline. */
    QUICK_LINK,

    /** From the current perspective at most one counterpart is reachable. */
    RELATED_TO_ONE,

    /** Several counterparts are reachable. */
    RELATED_LIST,

    /** Association domain with own attributes or special semantics. */
    CONTEXTUAL_FORM,

    /** Three or more roles. */
    NARY_CONTEXTUAL_FORM,

    /** Display only, no safe mutation. */
    READ_ONLY
}

package ch.interlis.generator.grails;

/**
 * Describes how an INTERLIS association is physically stored in the ili2db schema.
 */
public enum AssociationStorageKind {

    /** A physically mapped association domain owns the role foreign keys. */
    LINK_ENTITY,

    /** The association is physically embedded as a foreign key in a participating class. */
    EMBEDDED_FOREIGN_KEY,

    /** No sufficiently safe physical write mapping is available. */
    UNMAPPED
}

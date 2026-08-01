package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * How a physical association is stored in the database schema.
 */
public enum AssociationStorageKind {
    LINK_ENTITY,
    EMBEDDED_FOREIGN_KEY,
    UNMAPPED
}

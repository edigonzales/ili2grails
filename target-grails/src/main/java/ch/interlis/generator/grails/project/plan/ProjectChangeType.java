package ch.interlis.generator.grails.project.plan;

/**
 * Art einer geplanten Projektänderung (Spezifikation §38.1).
 */
public enum ProjectChangeType {
    CREATE,
    UPDATE,
    DELETE,
    UNCHANGED,
    BLOCKED
}

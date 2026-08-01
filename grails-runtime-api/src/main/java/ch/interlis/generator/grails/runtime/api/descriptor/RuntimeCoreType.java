package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * Dependency-neutral runtime view of the core attribute type.
 *
 * <p>Defined locally in the runtime API so that the runtime contract does not
 * depend on the generator core module. Generators map the core
 * {@code CoreType} explicitly onto these values.</p>
 */
public enum RuntimeCoreType {
    TEXT,
    MTEXT,
    NUMERIC,
    BOOLEAN,
    DATE,
    DATETIME,
    TIME,
    ENUM,
    COORD,
    POLYLINE,
    SURFACE,
    REFERENCE,
    COMPOSITION,
    OBJECT,
    UNKNOWN
}

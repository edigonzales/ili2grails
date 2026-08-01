package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * Mode of an inverse relationship section.
 *
 * <p>The mode is the result of the generated capability plus the optional
 * runtime configuration downgrade. Runtime configuration may restrict
 * generated behavior but can never upgrade a read-only relationship.</p>
 */
public enum InverseRelationshipMode {
    AUTO,
    EDITABLE,
    READ_ONLY,
    OFF
}

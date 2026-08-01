package ch.interlis.generator.grails;

/**
 * Persistenz-Diagnostic des {@link GrailsRelationshipMapper}.
 */
public record PersistenceDiagnostic(
    Severity severity,
    Code code,
    String ownerClass,
    String relationshipName,
    String message
) {

    public enum Severity {
        WARNING,
        ERROR
    }

    public enum Code {
        COMPOSITION_COLLECTION_UNRESOLVED,
        COMPOSITION_MAPPED_BY_AMBIGUOUS,
        RELATIONSHIP_PROPERTY_AMBIGUOUS,
        DUPLICATE_PHYSICAL_COLUMN_MAPPING
    }
}

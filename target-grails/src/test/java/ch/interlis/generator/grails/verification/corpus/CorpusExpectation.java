package ch.interlis.generator.grails.verification.corpus;

import java.util.List;

/**
 * Erwartungen eines Corpus-Szenarios (Spezifikation §23.4).
 */
public record CorpusExpectation(
    int blockingDiagnostics,
    Integer exactClasses,
    Integer minimumClasses,
    Integer exactRelationships,
    Integer minimumRelationships,
    Integer exactAssociations,
    boolean generatedGrails,
    boolean compileGeneratedGrails,
    boolean mappingContract,
    List<ExpectedDiagnostic> diagnostics,
    List<AllowedDifference> allowedDifferences
) {

    public CorpusExpectation {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        allowedDifferences = allowedDifferences == null ? List.of() : List.copyOf(allowedDifferences);
    }
}

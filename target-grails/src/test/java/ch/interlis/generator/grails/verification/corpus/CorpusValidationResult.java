package ch.interlis.generator.grails.verification.corpus;

import java.util.List;

/**
 * Ergebnis der Corpus-Validierung.
 */
public record CorpusValidationResult(
    List<CorpusValidationDiagnostic> diagnostics
) {

    public CorpusValidationResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean isValid() {
        return diagnostics.stream().noneMatch(CorpusValidationDiagnostic::blocking);
    }

    public List<CorpusValidationDiagnostic> blockingDiagnostics() {
        return diagnostics.stream().filter(CorpusValidationDiagnostic::blocking).toList();
    }

    public void throwIfInvalid() {
        if (!isValid()) {
            throw new IllegalStateException("Model corpus validation failed:\n  - "
                + blockingDiagnostics().stream()
                    .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
                    .reduce((left, right) -> left + "\n  - " + right)
                    .orElse("invalid corpus"));
        }
    }
}

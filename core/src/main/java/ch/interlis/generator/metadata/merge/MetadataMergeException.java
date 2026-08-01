package ch.interlis.generator.metadata.merge;

import java.util.List;

/**
 * Wird geworfen, wenn der Merge blockierende Diagnostics enthält.
 * Die Message ist kompakt; die vollständige Diagnose bleibt strukturiert
 * über {@link #diagnostics()} zugänglich.
 */
public final class MetadataMergeException extends RuntimeException {

    private final List<MergeDiagnostic> diagnostics;

    public MetadataMergeException(List<MergeDiagnostic> diagnostics) {
        super(buildMessage(diagnostics));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<MergeDiagnostic> diagnostics() {
        return diagnostics;
    }

    private static String buildMessage(List<MergeDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "Metadata merge failed";
        }
        return "Metadata merge failed with " + diagnostics.size()
            + " blocking diagnostic(s)";
    }
}

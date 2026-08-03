package ch.interlis.generator.grails.project.plan;

import java.nio.file.Path;
import java.util.List;

/**
 * Ergebnis der Plan-Ausführung (Spezifikation §45.1).
 */
public record GenerationExecutionResult(
    GenerationPlan plan,
    List<Path> writtenFiles,
    List<Path> deletedFiles,
    boolean manifestWritten
) {

    public GenerationExecutionResult {
        writtenFiles = writtenFiles == null ? List.of() : List.copyOf(writtenFiles);
        deletedFiles = deletedFiles == null ? List.of() : List.copyOf(deletedFiles);
    }

    public boolean successful() {
        return manifestWritten && !plan.hasBlockingDiagnostics();
    }
}

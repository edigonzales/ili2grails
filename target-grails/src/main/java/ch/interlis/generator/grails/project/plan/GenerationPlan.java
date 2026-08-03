package ch.interlis.generator.grails.project.plan;

import java.util.List;

/**
 * Vollständiger Generationsplan: alle geplanten Projektänderungen und alle
 * Diagnostics (Spezifikation §38.7). Vor dem ersten Write müssen alle
 * Änderungen und alle Blocker bekannt sein.
 */
public record GenerationPlan(
    int schemaVersion,
    String modelName,
    String modelFingerprint,
    String configFingerprint,
    List<ProjectChange> changes,
    List<GenerationDiagnostic> diagnostics
) {

    public GenerationPlan {
        changes = changes == null ? List.of() : List.copyOf(changes);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean hasBlockingDiagnostics() {
        return diagnostics.stream().anyMatch(GenerationDiagnostic::blocking);
    }

    public List<ProjectChange> mutatingChanges() {
        return changes.stream().filter(ProjectChange::mutating).toList();
    }

    public List<ProjectChange> blockedChanges() {
        return changes.stream().filter(ProjectChange::blocked).toList();
    }

    public GenerationPlanSummary summary() {
        int create = 0;
        int update = 0;
        int delete = 0;
        int unchanged = 0;
        int blocked = 0;
        for (ProjectChange change : changes) {
            switch (change.type()) {
                case CREATE -> create++;
                case UPDATE -> update++;
                case DELETE -> delete++;
                case UNCHANGED -> unchanged++;
                case BLOCKED -> blocked++;
            }
        }
        long blockingDiagnostics = diagnostics.stream().filter(GenerationDiagnostic::blocking).count();
        return new GenerationPlanSummary(create, update, delete, unchanged, blocked,
            (int) blockingDiagnostics);
    }
}

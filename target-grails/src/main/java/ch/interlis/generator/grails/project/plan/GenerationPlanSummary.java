package ch.interlis.generator.grails.project.plan;

/**
 * Kompakte Zusammenfassung eines {@link GenerationPlan}.
 */
public record GenerationPlanSummary(
    int create,
    int update,
    int delete,
    int unchanged,
    int blocked,
    int blockingDiagnostics
) {

    public boolean hasBlockers() {
        return blocked > 0 || blockingDiagnostics > 0;
    }
}

package ch.interlis.generator.grails;

import ch.interlis.generator.grails.project.plan.GenerationDiagnostic;
import ch.interlis.generator.grails.project.plan.GenerationPlan;

import java.util.List;

/**
 * Wird geworfen, wenn der Generationsplan blockierende Diagnostics enthält.
 * Es wurde keine Projektdatei verändert (Spezifikation §46).
 */
public final class GrailsGenerationBlockedException extends IllegalStateException {

    private final GenerationPlan plan;

    public GrailsGenerationBlockedException(GenerationPlan plan, String message) {
        super(message);
        this.plan = plan;
    }

    public GenerationPlan getPlan() {
        return plan;
    }

    public List<GenerationDiagnostic> blockingDiagnostics() {
        return plan.diagnostics().stream().filter(GenerationDiagnostic::blocking).toList();
    }
}

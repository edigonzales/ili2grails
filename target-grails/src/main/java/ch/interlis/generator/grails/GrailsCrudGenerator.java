package ch.interlis.generator.grails;

import ch.interlis.generator.grails.project.RuntimeCoordinates;
import ch.interlis.generator.grails.project.plan.GenerationExecutionResult;
import ch.interlis.generator.grails.project.plan.GenerationPlan;
import ch.interlis.generator.grails.project.plan.GenerationPlanSummary;
import ch.interlis.generator.grails.project.plan.GeneratedProjectManifest;
import ch.interlis.generator.grails.project.plan.GeneratedProjectManifestStore;
import ch.interlis.generator.grails.project.plan.GrailsGenerationExecutor;
import ch.interlis.generator.grails.project.plan.GrailsGenerationPlanner;
import ch.interlis.generator.model.ModelMetadata;

import java.io.IOException;
import java.util.Optional;

/**
 * Orchestriert die Generierung nach dem Plan-before-write-Prinzip
 * (Spezifikation §46):
 *
 * <pre>
 * plan(metadata, config)   - vollständiger Plan, keine Dateiänderung
 * apply(plan, config)      - Plan ausführen, Manifest zuletzt
 * generate(metadata, config) - plan + Gate + apply
 * </pre>
 *
 * <p>Keine Generator-Komponente schreibt vor Abschluss von
 * {@code plan(...)}. Bei blockierenden Diagnostics wird keine Projektdatei
 * verändert.</p>
 */
public final class GrailsCrudGenerator {

    private final GrailsGenerationPlanner planner = new GrailsGenerationPlanner();
    private final GrailsGenerationExecutor executor = new GrailsGenerationExecutor();
    private final GeneratedProjectManifestStore manifestStore = new GeneratedProjectManifestStore();

    public GenerationPlan plan(ModelMetadata metadata, GenerationConfig config) throws IOException {
        RuntimeCoordinates runtimeCoordinates = RuntimeCoordinates.ili2grailsRuntime();
        Optional<GeneratedProjectManifest> previousManifest =
            manifestStore.read(config.getOutputDir());
        return planner.plan(metadata, config, runtimeCoordinates, previousManifest);
    }

    public GenerationExecutionResult apply(GenerationPlan plan, GenerationConfig config)
        throws IOException {
        return executor.apply(config.getOutputDir(), plan);
    }

    public GenerationExecutionResult generate(ModelMetadata metadata, GenerationConfig config)
        throws IOException {
        GenerationPlan plan = plan(metadata, config);
        if (plan.hasBlockingDiagnostics()) {
            GenerationPlanSummary summary = plan.summary();
            throw new GrailsGenerationBlockedException(plan,
                "Generation blocked; no project files were changed.\n"
                    + "  CREATE: " + summary.create() + "\n"
                    + "  UPDATE: " + summary.update() + "\n"
                    + "  DELETE: " + summary.delete() + "\n"
                    + "  UNCHANGED: " + summary.unchanged() + "\n"
                    + "  BLOCKED: " + summary.blocked());
        }
        return apply(plan, config);
    }
}

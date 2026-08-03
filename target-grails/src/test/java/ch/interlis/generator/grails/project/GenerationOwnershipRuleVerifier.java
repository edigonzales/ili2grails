package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.project.plan.GenerationDiagnostic;
import ch.interlis.generator.grails.project.plan.GenerationOwnershipValidator;

import java.util.List;

/**
 * Entry point für den Gradle-Task :target-grails:verifyGenerationOwnershipRules
 * (Spezifikation §42.3): prüft die Ownership-Regeln maschinell.
 */
public final class GenerationOwnershipRuleVerifier {

    private GenerationOwnershipRuleVerifier() {
    }

    public static void main(String[] args) {
        List<GenerationDiagnostic> diagnostics =
            new GenerationOwnershipValidator().validateLegacyRules(
                GrailsProjectFileOwnership.rules());
        if (!diagnostics.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            builder.append("Generation ownership rule violations:\n");
            for (GenerationDiagnostic diagnostic : diagnostics) {
                builder.append("  - ").append(diagnostic.code()).append(": ")
                    .append(diagnostic.message()).append("\n");
            }
            throw new IllegalStateException(builder.toString());
        }
        System.out.println("verifyGenerationOwnershipRules: no ownership violations ("
            + GrailsProjectFileOwnership.rules().size() + " rules)");
    }
}

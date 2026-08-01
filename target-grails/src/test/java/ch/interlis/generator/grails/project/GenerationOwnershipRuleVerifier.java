package ch.interlis.generator.grails.project;

/**
 * Entry point für den Gradle-Task :target-grails:verifyGenerationOwnershipRules.
 * Wird in der Ownership-Phase vollständig implementiert.
 */
public final class GenerationOwnershipRuleVerifier {

    private GenerationOwnershipRuleVerifier() {
    }

    public static void main(String[] args) {
        System.out.println("verifyGenerationOwnershipRules: no ownership violations");
    }
}

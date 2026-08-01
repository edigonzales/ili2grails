package ch.interlis.generator.grails.verification.corpus;

import ch.interlis.generator.grails.verification.environment.VerificationEnvironment;

import java.nio.file.Path;

/**
 * Ausführungskontext für Corpus-Szenarien (Spezifikation §26.2).
 */
public record CorpusExecutionContext(
    Path repositoryRoot,
    Path workDirectory,
    VerificationEnvironment environment,
    boolean requiredInfrastructure
) {
}

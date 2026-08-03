package ch.interlis.generator.grails.verification.corpus;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Ein Corpus-Szenario: ein Modell, seine Repositories, Features und
 * Erwartungen (Spezifikation §23.2).
 */
public record CorpusScenario(
    String id,
    String modelName,
    Path modelFile,
    List<String> repositories,
    CorpusDatabaseRequirement database,
    Set<String> features,
    CorpusExpectation expected
) {
}

package ch.interlis.generator.grails.verification.corpus;

import java.util.List;

/**
 * Der versionierte Modellkorpus (Spezifikation §23.6).
 */
public record ModelCorpus(
    int schemaVersion,
    List<CorpusFeature> features,
    List<CorpusScenario> scenarios
) {

    public ModelCorpus {
        features = features == null ? List.of() : List.copyOf(features);
        scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
    }
}

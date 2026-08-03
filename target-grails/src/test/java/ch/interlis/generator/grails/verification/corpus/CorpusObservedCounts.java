package ch.interlis.generator.grails.verification.corpus;

/**
 * Beobachtete Zählwerte eines Corpus-Laufs (Spezifikation §26.4).
 */
public record CorpusObservedCounts(
    int selectedModels,
    int classes,
    int attributes,
    int relationships,
    int associations,
    int enums,
    int generatedDomains,
    int generatedRegistries
) {
}

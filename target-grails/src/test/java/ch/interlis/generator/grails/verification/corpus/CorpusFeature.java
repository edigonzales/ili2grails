package ch.interlis.generator.grails.verification.corpus;

/**
 * Ein INTERLIS-Persistenzfeature, das der Korpus belegt (Spezifikation §23.1).
 */
public record CorpusFeature(
    String id,
    String description
) {
}

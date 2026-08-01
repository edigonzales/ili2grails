package ch.interlis.generator.grails.verification.corpus;

/**
 * Datenbank-Anforderung eines Corpus-Szenarios (Spezifikation §23.3).
 */
public record CorpusDatabaseRequirement(
    boolean required,
    String importProfile
) {

    public CorpusDatabaseRequirement {
        importProfile = importProfile == null || importProfile.isBlank() ? null : importProfile;
    }

    public static CorpusDatabaseRequirement none() {
        return new CorpusDatabaseRequirement(false, null);
    }
}

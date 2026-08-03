package ch.interlis.generator.grails.verification.corpus;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Lädt den versionierten Modellkorpus (Spezifikation §24.1). Verwendet den
 * vorhandenen Jackson-YAML-Stack des Moduls; keine neue YAML-Bibliothek.
 */
public final class ModelCorpusLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ModelCorpus load(Path yamlFile) throws IOException {
        ModelCorpus corpus = YAML_MAPPER.readValue(yamlFile.toFile(), ModelCorpus.class);
        if (corpus == null) {
            throw new IOException("Corpus file is empty: " + yamlFile);
        }
        return corpus;
    }
}

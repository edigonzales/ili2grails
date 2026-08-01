package ch.interlis.generator.grails.verification.mapping;

import java.util.Map;

/**
 * Erwartete Entity-Abbildung (Spezifikation §31.2).
 */
public record ExpectedEntityMapping(
    String iliClassName,
    String domainClassName,
    String tableName,
    ExpectedIdMapping id,
    Map<String, ExpectedPropertyMapping> properties,
    Map<String, ExpectedCollectionMapping> collections,
    boolean versioned
) {

    public ExpectedEntityMapping {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        collections = collections == null ? Map.of() : Map.copyOf(collections);
    }
}

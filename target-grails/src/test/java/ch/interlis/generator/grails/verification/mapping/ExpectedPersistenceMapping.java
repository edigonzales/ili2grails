package ch.interlis.generator.grails.verification.mapping;

import java.util.List;
import java.util.Map;

/**
 * Erwartetes Persistenz-Mapping aus Core-IR und Grails-Planern
 * (Spezifikation §31.1).
 */
public record ExpectedPersistenceMapping(
    Map<String, ExpectedEntityMapping> entities,
    List<ExpectedAssociationMapping> associations
) {

    public ExpectedPersistenceMapping {
        entities = entities == null ? Map.of() : Map.copyOf(entities);
        associations = associations == null ? List.of() : List.copyOf(associations);
    }
}

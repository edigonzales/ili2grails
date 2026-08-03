package ch.interlis.generator.grails.verification.mapping;

import java.util.Map;

/**
 * Hibernate-Mapping-Snapshot, gesammelt aus der gestarteten App
 * (Spezifikation §32.3).
 */
public record HibernateMappingSnapshot(
    Map<String, HibernateEntityMapping> entities
) {

    public HibernateMappingSnapshot {
        entities = entities == null ? Map.of() : Map.copyOf(entities);
    }
}

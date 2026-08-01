package ch.interlis.generator.model.json;

import ch.interlis.generator.model.ModelMetadata;

import java.util.Map;

/**
 * Legacy-JSON-View: entspricht dem Pre-P1-Format ohne Format-Marker.
 *
 * <p>Der P0-Writer enthielt keine Relationships unter Klassen; die kanonische
 * Liste war bereits die einzige Quelle. Die Kompatibilität wird daher über die
 * View hergestellt, nicht durch doppelte Speicherung.</p>
 */
public final class LegacyModelMetadataJsonView extends ModelMetadataJsonView {

    @Override
    public Map<String, Object> toDto(ModelMetadata metadata) {
        return super.toDto(metadata, false);
    }
}

package ch.interlis.generator.reader.ili2db.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable Snapshot der ili2db-Metatabellen eines Lesedurchgangs.
 *
 * <p>Der CatalogReader erzeugt ausschliesslich typed Rows/Snapshots; die
 * IR-Erzeugung ist Aufgabe des Assemblers.</p>
 */
public record Ili2dbCatalogSnapshot(
    Map<String, String> settings,
    List<ModelRow> models,
    List<ClassMappingRow> classes,
    List<AttributeMappingRow> attributes,
    List<InheritanceRow> inheritance,
    List<ColumnPropertyRow> columnProperties,
    List<EnumDomainRow> enumDomains,
    Ili2dbCatalogCapabilities capabilities
) {

    public Ili2dbCatalogSnapshot {
        settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        models = models == null ? List.of() : List.copyOf(models);
        classes = classes == null ? List.of() : List.copyOf(classes);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        inheritance = inheritance == null ? List.of() : List.copyOf(inheritance);
        columnProperties = columnProperties == null ? List.of() : List.copyOf(columnProperties);
        enumDomains = enumDomains == null ? List.of() : List.copyOf(enumDomains);
    }

    public static Ili2dbCatalogSnapshot empty(Ili2dbCatalogCapabilities capabilities) {
        return new Ili2dbCatalogSnapshot(Map.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), capabilities);
    }
}

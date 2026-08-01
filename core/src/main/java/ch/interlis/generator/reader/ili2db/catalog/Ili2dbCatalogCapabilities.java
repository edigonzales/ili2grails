package ch.interlis.generator.reader.ili2db.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Einmalig ermittelte Katalog-Fähigkeiten: verfügbare Metatabellen und deren
 * Spalten.
 */
public record Ili2dbCatalogCapabilities(
    Set<String> availableTables,
    Map<String, Set<String>> columnsByTable
) {

    public Ili2dbCatalogCapabilities {
        availableTables = Collections.unmodifiableSet(new LinkedHashSet<>(availableTables));
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        columnsByTable.forEach((table, columns) ->
            normalized.put(table.toLowerCase(Locale.ROOT),
                Collections.unmodifiableSet(new LinkedHashSet<>(columns))));
        columnsByTable = Collections.unmodifiableMap(normalized);
    }

    public boolean hasTable(String name) {
        return name != null && availableTables.contains(name.toLowerCase(Locale.ROOT));
    }

    public boolean hasColumn(String table, String column) {
        if (table == null || column == null) {
            return false;
        }
        Set<String> columns = columnsByTable.get(table.toLowerCase(Locale.ROOT));
        if (columns == null) {
            return false;
        }
        return columns.stream().anyMatch(existing -> existing.equalsIgnoreCase(column));
    }
}

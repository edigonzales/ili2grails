package ch.interlis.generator.reader.ili2db.catalog;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Verbindliche Einordnung der ili2db-Metatabellen. Einzige Wahrheit für die
 * Required-/Optional-Klassifikation (Spezifikation §15.3); keine separate
 * hartcodierte Liste im {@link Ili2dbCatalogReader}.
 */
public final class Ili2dbTableRequirementResolver {

    private static final Map<String, Ili2dbTableRequirement> REQUIREMENTS = Map.of(
        "t_ili2db_classname", Ili2dbTableRequirement.REQUIRED,
        "t_ili2db_attrname", Ili2dbTableRequirement.REQUIRED,
        "t_ili2db_table_prop", Ili2dbTableRequirement.REQUIRED,
        "t_ili2db_settings", Ili2dbTableRequirement.OPTIONAL,
        "t_ili2db_model", Ili2dbTableRequirement.OPTIONAL,
        "t_ili2db_inheritance", Ili2dbTableRequirement.OPTIONAL,
        "t_ili2db_column_prop", Ili2dbTableRequirement.OPTIONAL,
        "t_ili2db_trafo", Ili2dbTableRequirement.OPTIONAL
    );

    public Ili2dbTableRequirement requirement(String tableName) {
        return tableName == null
            ? Ili2dbTableRequirement.OPTIONAL
            : REQUIREMENTS.getOrDefault(tableName.toLowerCase(java.util.Locale.ROOT),
                Ili2dbTableRequirement.OPTIONAL);
    }

    public Set<String> requiredTables() {
        Set<String> required = new TreeSet<>();
        REQUIREMENTS.forEach((table, requirement) -> {
            if (requirement == Ili2dbTableRequirement.REQUIRED) {
                required.add(table);
            }
        });
        return required;
    }

    public Set<String> optionalTables() {
        Set<String> optional = new TreeSet<>();
        REQUIREMENTS.forEach((table, requirement) -> {
            if (requirement == Ili2dbTableRequirement.OPTIONAL) {
                optional.add(table);
            }
        });
        return optional;
    }
}

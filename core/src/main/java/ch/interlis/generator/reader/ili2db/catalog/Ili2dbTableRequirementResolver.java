package ch.interlis.generator.reader.ili2db.catalog;

import java.util.Map;

/**
 * Verbindliche Einordnung der ili2db-Metatabellen.
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

    public Ili2dbTableRequirement requirementOf(String tableName) {
        return REQUIREMENTS.getOrDefault(tableName, Ili2dbTableRequirement.OPTIONAL);
    }
}

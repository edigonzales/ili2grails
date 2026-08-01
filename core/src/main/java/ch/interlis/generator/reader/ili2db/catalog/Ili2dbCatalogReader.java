package ch.interlis.generator.reader.ili2db.catalog;

import ch.interlis.generator.reader.ili2db.Ili2dbDiagnostic;
import ch.interlis.generator.reader.ili2db.Ili2dbDiagnosticCode;
import ch.interlis.generator.reader.ili2db.Ili2dbReadContext;
import ch.interlis.generator.reader.ili2db.Ili2dbSeverity;
import ch.interlis.generator.reader.sql.SqlIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Liest die {@code t_ili2db_*} Metatabellen und erzeugt ausschliesslich typed
 * Rows/Snapshots. Keine IR-Erzeugung.
 */
public final class Ili2dbCatalogReader {

    private static final Logger logger = LoggerFactory.getLogger(Ili2dbCatalogReader.class);

    public static final String ENUM_DOMAIN_TAG = "ch.ehi.ili2db.enumDomain";

    private static final String ATTR_ILINAME_COLUMN = "iliname";
    private static final String ATTR_OWNER_COLUMN = "colowner";
    private static final String ATTR_TARGET_COLUMN = "target";
    private static final String ATTR_ILINAME_REF = "a." + ATTR_ILINAME_COLUMN;
    private static final String ATTR_OWNER_REF = "a." + ATTR_OWNER_COLUMN;

    private static final List<String> META_TABLES = List.of(
        "t_ili2db_classname",
        "t_ili2db_attrname",
        "t_ili2db_table_prop",
        "t_ili2db_settings",
        "t_ili2db_model",
        "t_ili2db_inheritance",
        "t_ili2db_column_prop",
        "t_ili2db_trafo"
    );

    public Ili2dbCatalogSnapshot read(Ili2dbReadContext context,
                                      List<Ili2dbDiagnostic> diagnostics) throws SQLException {
        Ili2dbCatalogCapabilities capabilities = detectCapabilities(context, diagnostics);
        return read(context, capabilities, diagnostics);
    }

    public Ili2dbCatalogSnapshot read(Ili2dbReadContext context,
                                      Ili2dbCatalogCapabilities capabilities,
                                      List<Ili2dbDiagnostic> diagnostics) throws SQLException {
        return read(context, capabilities, diagnostics, List.of(), List.of());
    }

    public Ili2dbCatalogSnapshot read(Ili2dbReadContext context,
                                      Ili2dbCatalogCapabilities capabilities,
                                      List<Ili2dbDiagnostic> diagnostics,
                                      Collection<String> modelNames,
                                      Collection<String> tableNames) throws SQLException {
        Map<String, String> settings = readSettings(context, capabilities, diagnostics);
        List<ModelRow> models = readModels(context, capabilities, diagnostics);
        List<ClassMappingRow> classes = readClasses(context, capabilities, diagnostics, modelNames);
        List<AttributeMappingRow> attributes = readAttributes(context, capabilities, diagnostics,
            modelNames, tableNames);
        List<InheritanceRow> inheritance = readInheritance(context, capabilities, diagnostics, modelNames);
        List<ColumnPropertyRow> columnProperties =
            readColumnProperties(context, capabilities, diagnostics);
        List<EnumDomainRow> enumDomains = readEnumDomains(context, capabilities, diagnostics);
        return new Ili2dbCatalogSnapshot(settings, models, classes, attributes,
            inheritance, columnProperties, enumDomains, capabilities);
    }

    /**
     * Ermittelt einmalig, welche Metatabellen vorhanden und lesbar sind.
     */
    public Ili2dbCatalogCapabilities detectCapabilities(Ili2dbReadContext context,
                                                        List<Ili2dbDiagnostic> diagnostics)
        throws SQLException {
        DatabaseMetaData metadata = context.connection().getMetaData();
        Set<String> availableTables = new LinkedHashSet<>();
        Map<String, Set<String>> columnsByTable = new LinkedHashMap<>();

        for (String metaTable : META_TABLES) {
            Set<String> columns = new LinkedHashSet<>();
            boolean available = false;
            try (ResultSet rs = metadata.getTables(null, schemaPattern(context), null, null)) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName != null && tableName.equalsIgnoreCase(metaTable)) {
                        available = true;
                        break;
                    }
                }
            } catch (SQLException e) {
                diagnostics.add(new Ili2dbDiagnostic(
                    Ili2dbSeverity.WARNING,
                    Ili2dbDiagnosticCode.META_TABLE_COLUMNS_UNSUPPORTED,
                    "Could not inspect meta table " + metaTable + ": " + e.getMessage(),
                    null, metaTable, Map.of()));
            }
            if (available) {
                availableTables.add(metaTable.toLowerCase(Locale.ROOT));
                try (ResultSet rs = metadata.getColumns(null, schemaPattern(context), null, null)) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        if (tableName != null && tableName.equalsIgnoreCase(metaTable)) {
                            String column = rs.getString("COLUMN_NAME");
                            if (column != null) {
                                columns.add(column);
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Could not inspect columns of {}: {}", metaTable, e.getMessage());
                }
            }
            columnsByTable.put(metaTable.toLowerCase(Locale.ROOT), columns);
        }

        for (String requiredTable : new String[] {"t_ili2db_classname", "t_ili2db_attrname"}) {
            if (!availableTables.contains(requiredTable)) {
                diagnostics.add(new Ili2dbDiagnostic(
                    Ili2dbSeverity.FATAL,
                    Ili2dbDiagnosticCode.REQUIRED_META_TABLE_MISSING,
                    "Required meta table '" + requiredTable + "' is missing or unreadable",
                    null, requiredTable, Map.of()));
            }
        }
        return new Ili2dbCatalogCapabilities(availableTables, columnsByTable);
    }

    public Map<String, String> readSettings(Ili2dbReadContext context,
                                            Ili2dbCatalogCapabilities capabilities,
                                            List<Ili2dbDiagnostic> diagnostics) {
        Map<String, String> settings = new LinkedHashMap<>();
        if (!capabilities.hasTable("t_ili2db_settings")) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.OPTIONAL_META_TABLE_MISSING,
                "Optional meta table 't_ili2db_settings' is missing; settings are empty",
                null, "t_ili2db_settings", Map.of()));
            return settings;
        }
        String sql = "SELECT tag, setting FROM " + metaTable(context, "t_ili2db_settings");
        try (Statement stmt = context.connection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                settings.put(rs.getString("tag"), rs.getString("setting"));
            }
        } catch (SQLException e) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.OPTIONAL_META_TABLE_MISSING,
                "Could not read settings: " + e.getMessage(),
                null, "t_ili2db_settings", Map.of()));
        }
        return settings;
    }

    public List<ModelRow> readModels(Ili2dbReadContext context,
                                     Ili2dbCatalogCapabilities capabilities,
                                     List<Ili2dbDiagnostic> diagnostics) {
        List<ModelRow> models = new ArrayList<>();
        if (!capabilities.hasTable("t_ili2db_model")) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.OPTIONAL_META_TABLE_MISSING,
                "Optional meta table 't_ili2db_model' is missing; model validation skipped",
                null, "t_ili2db_model", Map.of()));
            return models;
        }
        String sql = "SELECT * FROM " + metaTable(context, "t_ili2db_model");
        try (Statement stmt = context.connection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            String modelColumn = findColumn(meta, "modelname", "model", "name");
            if (modelColumn == null) {
                diagnostics.add(new Ili2dbDiagnostic(
                    Ili2dbSeverity.WARNING,
                    Ili2dbDiagnosticCode.META_TABLE_COLUMNS_UNSUPPORTED,
                    "Could not detect model name column in t_ili2db_model",
                    null, "t_ili2db_model", Map.of()));
                return models;
            }
            String contentColumn = findColumn(meta, "content");
            while (rs.next()) {
                String modelName = rs.getString(modelColumn);
                if (modelName == null || modelName.isBlank()) {
                    continue;
                }
                String content = contentColumn != null ? rs.getString(contentColumn) : null;
                if (contentColumn != null && isTypeModel(content)) {
                    continue;
                }
                models.add(new ModelRow(canonicalModelName(modelName), content));
            }
        } catch (SQLException e) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.OPTIONAL_META_TABLE_MISSING,
                "Could not read model list: " + e.getMessage(),
                null, "t_ili2db_model", Map.of()));
        }
        return models;
    }

    public List<ClassMappingRow> readClasses(Ili2dbReadContext context,
                                             Ili2dbCatalogCapabilities capabilities,
                                             List<Ili2dbDiagnostic> diagnostics,
                                             Collection<String> modelNames) throws SQLException {
        List<ClassMappingRow> rows = new ArrayList<>();
        if (!capabilities.hasTable("t_ili2db_classname")
            || !capabilities.hasTable("t_ili2db_table_prop")) {
            return rows;
        }
        List<String> prefixes = buildModelPrefixes(context, modelNames);
        if (prefixes.isEmpty()) {
            return rows;
        }
        String sql =
            "SELECT tp.tablename, tp.setting, c.iliname " +
            "FROM " + metaTable(context, "t_ili2db_table_prop") + " tp " +
            "LEFT JOIN " + metaTable(context, "t_ili2db_classname") + " c " +
            "  ON upper(tp.tablename) = upper(c.sqlname) " +
            "WHERE " + buildLikeClause("c.iliname", prefixes.size()) + " " +
            "ORDER BY c.iliname"
        ;
        try (PreparedStatement pstmt = context.connection().prepareStatement(sql)) {
            bindLikePrefixes(pstmt, prefixes);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("tablename");
                    String setting = rs.getString("setting");
                    String iliName = rs.getString("iliname");
                    if (iliName == null || iliName.isBlank()) {
                        continue;
                    }
                    rows.add(new ClassMappingRow(iliName, tableName, setting));
                }
            }
        }
        return rows;
    }

    public List<AttributeMappingRow> readAttributes(Ili2dbReadContext context,
                                                    Ili2dbCatalogCapabilities capabilities,
                                                    List<Ili2dbDiagnostic> diagnostics,
                                                    Collection<String> modelNames,
                                                    Collection<String> tableNames)
        throws SQLException {
        List<AttributeMappingRow> rows = new ArrayList<>();
        if (!capabilities.hasTable("t_ili2db_attrname")) {
            return rows;
        }
        List<String> prefixes = buildModelPrefixes(context, modelNames);
        if (prefixes.isEmpty()) {
            return rows;
        }
        List<String> distinctTableNames = tableNames.stream()
            .filter(Objects::nonNull)
            .filter(name -> !name.isBlank())
            .distinct()
            .toList();
        String whereClause = buildAttributeWhereClause(prefixes.size(), distinctTableNames.size());
        String sql =
            "SELECT " + ATTR_ILINAME_REF + ", a.sqlname, a." + ATTR_OWNER_COLUMN + " AS owner, a."
                + ATTR_TARGET_COLUMN + " AS target " +
            "FROM " + metaTable(context, "t_ili2db_attrname") + " a " +
            "WHERE " + whereClause + " " +
            "ORDER BY a." + ATTR_OWNER_COLUMN + ", a.sqlname"
        ;
        try (PreparedStatement pstmt = context.connection().prepareStatement(sql)) {
            bindAttributeFilters(pstmt, prefixes, distinctTableNames);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AttributeMappingRow(
                        rs.getString("iliname"),
                        rs.getString("sqlname"),
                        rs.getString("owner"),
                        rs.getString("target")
                    ));
                }
            }
        }
        return rows;
    }

    public List<InheritanceRow> readInheritance(Ili2dbReadContext context,
                                                Ili2dbCatalogCapabilities capabilities,
                                                List<Ili2dbDiagnostic> diagnostics,
                                                Collection<String> modelNames) {
        List<InheritanceRow> rows = new ArrayList<>();
        if (!capabilities.hasTable("t_ili2db_inheritance")) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.OPTIONAL_META_TABLE_MISSING,
                "Optional meta table 't_ili2db_inheritance' is missing; inheritance skipped",
                null, "t_ili2db_inheritance", Map.of()));
            return rows;
        }
        List<String> prefixes = buildModelPrefixes(context, modelNames);
        if (prefixes.isEmpty()) {
            return rows;
        }
        String sql = "SELECT thisclass, baseclass FROM "
            + metaTable(context, "t_ili2db_inheritance") + " WHERE "
            + buildLikeClause("thisclass", prefixes.size());
        try (PreparedStatement pstmt = context.connection().prepareStatement(sql)) {
            bindLikePrefixes(pstmt, prefixes);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new InheritanceRow(rs.getString("thisclass"), rs.getString("baseclass")));
                }
            }
        } catch (SQLException e) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.INHERITANCE_UNRESOLVED,
                "Could not read inheritance information: " + e.getMessage(),
                null, "t_ili2db_inheritance", Map.of()));
        }
        return rows;
    }

    public List<ColumnPropertyRow> readColumnProperties(Ili2dbReadContext context,
                                                        Ili2dbCatalogCapabilities capabilities,
                                                        List<Ili2dbDiagnostic> diagnostics) {
        List<ColumnPropertyRow> rows = new ArrayList<>();
        if (!capabilities.hasTable("t_ili2db_column_prop")) {
            return rows;
        }
        String sql = "SELECT tablename, columnname, tag, setting FROM "
            + metaTable(context, "t_ili2db_column_prop") + " ORDER BY tablename, columnname";
        try (Statement stmt = context.connection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new ColumnPropertyRow(
                    rs.getString("tablename"),
                    rs.getString("columnname"),
                    rs.getString("tag"),
                    rs.getString("setting")
                ));
            }
        } catch (SQLException e) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.OPTIONAL_META_TABLE_MISSING,
                "Could not read column properties: " + e.getMessage(),
                null, "t_ili2db_column_prop", Map.of()));
        }
        return rows;
    }

    public List<EnumDomainRow> readEnumDomains(Ili2dbReadContext context,
                                               Ili2dbCatalogCapabilities capabilities,
                                               List<Ili2dbDiagnostic> diagnostics) {
        List<EnumDomainRow> rows = new ArrayList<>();
        if (!capabilities.hasTable("t_ili2db_column_prop")
            || !capabilities.hasTable("t_ili2db_classname")) {
            return rows;
        }
        String ownerColumn = discoveredColumn(capabilities, "t_ili2db_column_prop",
            "colowner", "tablename");
        String columnColumn = discoveredColumn(capabilities, "t_ili2db_column_prop",
            "columnname", "sqlname");
        if (ownerColumn == null || columnColumn == null) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.META_TABLE_COLUMNS_UNSUPPORTED,
                "Could not determine column owner/column name fields in t_ili2db_column_prop",
                null, "t_ili2db_column_prop", Map.of()));
            return rows;
        }
        String sql =
            "SELECT cp." + quoteDiscovered(context, ownerColumn) + " AS owner, "
                + "cp." + quoteDiscovered(context, columnColumn) + " AS columnname, "
                + "cp.setting AS enumIliName, cn.sqlname AS enumTable " +
            "FROM " + metaTable(context, "t_ili2db_column_prop") + " cp " +
            "LEFT JOIN " + metaTable(context, "t_ili2db_classname") + " cn ON cp.setting = cn.iliname " +
            "WHERE cp.tag = ?"
        ;
        try (PreparedStatement pstmt = context.connection().prepareStatement(sql)) {
            pstmt.setString(1, ENUM_DOMAIN_TAG);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String owner = rs.getString("owner");
                    String columnName = rs.getString("columnname");
                    String enumIliName = rs.getString("enumIliName");
                    String enumTable = rs.getString("enumTable");
                    if (owner == null || columnName == null || enumIliName == null || enumTable == null) {
                        continue;
                    }
                    rows.add(new EnumDomainRow(owner, columnName, enumIliName, enumTable));
                }
            }
        } catch (SQLException e) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.ENUM_DOMAIN_UNRESOLVED,
                "Could not read enum domains from column properties: " + e.getMessage(),
                null, "t_ili2db_column_prop", Map.of()));
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String schemaPattern(Ili2dbReadContext context) {
        if (context.schema() == null) {
            return null;
        }
        return context.schema().value();
    }

    private String metaTable(Ili2dbReadContext context, String tableName) {
        return context.identifiers().qualify(context.schema(), SqlIdentifier.internal(tableName));
    }

    private String quoteDiscovered(Ili2dbReadContext context, String columnName) {
        return context.identifiers().quote(SqlIdentifier.discovered(columnName));
    }

    private String discoveredColumn(Ili2dbCatalogCapabilities capabilities,
                                    String table,
                                    String... candidates) {
        Set<String> columns = capabilities.columnsByTable()
            .getOrDefault(table.toLowerCase(Locale.ROOT), Set.of());
        for (String candidate : candidates) {
            for (String column : columns) {
                if (column.equalsIgnoreCase(candidate)) {
                    return column;
                }
            }
        }
        return null;
    }

    private String findColumn(ResultSetMetaData meta, String... candidates) throws SQLException {
        Map<String, String> available = new HashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = meta.getColumnLabel(i);
            String name = meta.getColumnName(i);
            if (label != null) {
                available.putIfAbsent(label.toLowerCase(Locale.ROOT), label);
            }
            if (name != null) {
                available.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
        }
        for (String candidate : candidates) {
            String matched = available.get(candidate.toLowerCase(Locale.ROOT));
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private List<String> buildModelPrefixes(Ili2dbReadContext context, Collection<String> modelNames) {
        Set<String> uniqueNames = new LinkedHashSet<>();
        if (modelNames != null) {
            for (String name : modelNames) {
                if (name != null && !name.isBlank()) {
                    uniqueNames.add(name);
                }
            }
        }
        if (uniqueNames.isEmpty() && context.modelSelection().rootModelName() != null
            && !context.modelSelection().rootModelName().isBlank()) {
            uniqueNames.add(context.modelSelection().rootModelName());
        }
        List<String> prefixes = new ArrayList<>();
        if (uniqueNames.isEmpty()) {
            prefixes.add("%");
        } else {
            for (String name : uniqueNames) {
                prefixes.add(name + ".%");
            }
        }
        return prefixes;
    }

    private String buildLikeClause(String columnName, int paramCount) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < paramCount; i++) {
            if (i > 0) {
                builder.append(" OR ");
            }
            builder.append(columnName).append(" LIKE ?");
        }
        builder.append(")");
        return builder.toString();
    }

    private void bindLikePrefixes(PreparedStatement pstmt, List<String> prefixes) throws SQLException {
        for (int i = 0; i < prefixes.size(); i++) {
            pstmt.setString(i + 1, prefixes.get(i));
        }
    }

    private String buildAttributeWhereClause(int prefixCount, int tableCount) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < prefixCount; i++) {
            if (i > 0) {
                builder.append(" OR ");
            }
            builder.append("(")
                .append(ATTR_ILINAME_REF)
                .append(" LIKE ? OR ")
                .append(ATTR_OWNER_REF)
                .append(" LIKE ?)");
        }
        builder.append(")");
        if (tableCount > 0) {
            builder.append(" OR (");
            for (int i = 0; i < tableCount; i++) {
                if (i > 0) {
                    builder.append(" OR ");
                }
                builder.append(ATTR_OWNER_REF).append(" = ?");
            }
            builder.append(")");
        }
        return builder.toString();
    }

    private void bindAttributeFilters(PreparedStatement pstmt, List<String> prefixes,
                                      List<String> tableNames) throws SQLException {
        int index = 1;
        for (String prefix : prefixes) {
            pstmt.setString(index++, prefix);
            pstmt.setString(index++, prefix);
        }
        for (String tableName : tableNames) {
            pstmt.setString(index++, tableName);
        }
    }

    private boolean isTypeModel(String content) {
        if (content == null) {
            return false;
        }
        return content.matches("(?s).*TYPE\\s+MODEL.*");
    }

    /**
     * ili2db schreibt für Modelle mit Imports den Namen als
     * {@code Name{imports}} in {@code t_ili2db_model}. Der kanonische
     * Modellname ist der Teil vor der geschweiften Klammer.
     */
    private String canonicalModelName(String modelName) {
        int braceIndex = modelName.indexOf('{');
        if (braceIndex < 0) {
            return modelName;
        }
        String canonical = modelName.substring(0, braceIndex).trim();
        return canonical.isBlank() ? modelName : canonical;
    }
}

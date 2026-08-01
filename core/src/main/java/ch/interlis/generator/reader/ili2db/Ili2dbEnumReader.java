package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.reader.sql.SqlIdentifier;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Liest Enum-Tabellenwerte (ilicode/dispname/seq) einmal pro Tabelle und
 * Lesedurchgang. SQL-Zugriff und Wertemapping sind hier getrennt von der
 * IR-Erzeugung; der Assembler konsumiert lediglich die Werte.
 */
public final class Ili2dbEnumReader {

    private final Map<String, List<EnumMetadata.EnumValue>> valuesByTable = new LinkedHashMap<>();

    /**
     * Liefert die Werte der Enum-Tabelle, gecacht pro Lauf. Bei nicht
     * lesbarer Tabelle wird eine WARN-Diagnostik erzeugt (non-blocking).
     */
    public List<EnumMetadata.EnumValue> valuesOf(Ili2dbReadContext context,
                                                 String enumTableName,
                                                 List<Ili2dbDiagnostic> diagnostics) {
        if (enumTableName == null || enumTableName.isBlank()) {
            return List.of();
        }
        List<EnumMetadata.EnumValue> cached = valuesByTable.get(enumTableName);
        if (cached != null) {
            return cached;
        }
        List<EnumMetadata.EnumValue> values = readTableValues(context, enumTableName, diagnostics);
        List<EnumMetadata.EnumValue> immutable = Collections.unmodifiableList(values);
        valuesByTable.put(enumTableName, immutable);
        return immutable;
    }

    public Collection<Map.Entry<String, List<EnumMetadata.EnumValue>>> cachedValues() {
        return valuesByTable.entrySet();
    }

    private List<EnumMetadata.EnumValue> readTableValues(Ili2dbReadContext context,
                                                         String enumTableName,
                                                         List<Ili2dbDiagnostic> diagnostics) {
        List<EnumMetadata.EnumValue> values = new ArrayList<>();
        String sql = "SELECT * FROM " + context.identifiers()
            .qualify(context.schema(), SqlIdentifier.discovered(enumTableName));
        try (Statement stmt = context.connection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            String iliCodeColumn = findColumn(meta, "ilicode");
            String dispNameColumn = findColumn(meta, "dispname");
            String seqColumn = findColumn(meta, "seq");
            while (rs.next()) {
                String iliCode = iliCodeColumn != null ? rs.getString(iliCodeColumn) : null;
                if (iliCode == null) {
                    continue;
                }
                int seq = seqColumn != null ? rs.getInt(seqColumn) : values.size();
                String dispName = dispNameColumn != null ? rs.getString(dispNameColumn) : null;
                if (dispName == null || dispName.isBlank()) {
                    dispName = iliCode;
                }
                values.add(new EnumMetadata.EnumValue(iliCode, dispName, seq, Map.of()));
            }
        } catch (SQLException e) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.ENUM_TABLE_UNREADABLE,
                "Could not read enum table values from " + enumTableName + ": " + e.getMessage(),
                null, enumTableName, Map.of()));
        }
        return values;
    }

    private String findColumn(ResultSetMetaData meta, String... candidates) throws SQLException {
        Map<String, String> available = new LinkedHashMap<>();
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
}

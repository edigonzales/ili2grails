package ch.interlis.generator.grails.verification.mapping;

import java.util.List;

/**
 * Physischer Fremdschlüssel (Spezifikation §33).
 */
public record DatabaseForeignKeyMapping(
    List<String> sourceColumns,
    String targetTable,
    List<String> targetColumns
) {

    public DatabaseForeignKeyMapping {
        sourceColumns = sourceColumns == null ? List.of() : List.copyOf(sourceColumns);
        targetColumns = targetColumns == null ? List.of() : List.copyOf(targetColumns);
    }
}

package ch.interlis.generator.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class GrailsApplicationYamlUpdater {

    void ensureDevelopmentDataSourceUrl(Path applicationYamlPath, String jdbcUrl) throws IOException {
        if (!Files.exists(applicationYamlPath)) {
            return;
        }
        String resolvedJdbcUrl = jdbcUrl == null || jdbcUrl.isBlank() ? null : jdbcUrl;
        List<String> lines = Files.readAllLines(applicationYamlPath, StandardCharsets.UTF_8);
        List<String> updated = updateDevelopmentDataSource(lines, resolvedJdbcUrl);
        if (updated != lines) {
            Files.write(applicationYamlPath, updated, StandardCharsets.UTF_8);
        }
    }

    private List<String> updateDevelopmentDataSource(List<String> lines, String jdbcUrl) {
        int envIndex = findBlockStart(lines, "environments");
        if (envIndex < 0) {
            return lines;
        }
        int devIndex = findChildBlockStart(lines, envIndex, "development");
        if (devIndex < 0) {
            return lines;
        }
        int dataSourceIndex = findChildBlockStart(lines, devIndex, "dataSource");
        if (dataSourceIndex < 0) {
            return lines;
        }
        List<String> updated = new ArrayList<>(lines);
        boolean changed = false;
        if (jdbcUrl != null) {
            changed |= upsertDataSourceKey(updated, dataSourceIndex, "url", quoteYaml(jdbcUrl), null);
        }
        changed |= upsertDataSourceKey(updated, dataSourceIndex, "dbCreate", "none", "url");
        if (!changed) {
            return lines;
        }
        return updated;
    }

    private boolean upsertDataSourceKey(
        List<String> lines,
        int dataSourceIndex,
        String key,
        String value,
        String insertAfterKey
    ) {
        int keyIndex = findChildKeyLine(lines, dataSourceIndex, key);
        String indent = keyIndex >= 0
            ? leadingWhitespace(lines.get(keyIndex))
            : leadingWhitespace(lines.get(dataSourceIndex)) + "  ";
        String updatedLine = indent + key + ": " + value;
        if (keyIndex >= 0) {
            if (lines.get(keyIndex).equals(updatedLine)) {
                return false;
            }
            lines.set(keyIndex, updatedLine);
            return true;
        }
        int insertionIndex = dataSourceIndex + 1;
        if (insertAfterKey != null) {
            int afterIndex = findChildKeyLine(lines, dataSourceIndex, insertAfterKey);
            if (afterIndex >= 0) {
                insertionIndex = afterIndex + 1;
            }
        }
        lines.add(insertionIndex, updatedLine);
        return true;
    }

    private int findBlockStart(List<String> lines, String key) {
        for (int i = 0; i < lines.size(); i++) {
            if (isBlockLine(lines.get(i), key)) {
                return i;
            }
        }
        return -1;
    }

    private int findChildBlockStart(List<String> lines, int parentIndex, String key) {
        int parentIndent = indentationLevel(lines.get(parentIndex));
        for (int i = parentIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = indentationLevel(line);
            if (indent <= parentIndent) {
                return -1;
            }
            if (isBlockLine(line, key)) {
                return i;
            }
        }
        return -1;
    }

    private int findChildKeyLine(List<String> lines, int parentIndex, String key) {
        int parentIndent = indentationLevel(lines.get(parentIndex));
        for (int i = parentIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = indentationLevel(line);
            if (indent <= parentIndent) {
                return -1;
            }
            if (isKeyLine(line, key)) {
                return i;
            }
        }
        return -1;
    }

    private int indentationLevel(String line) {
        return line.length() - line.stripLeading().length();
    }

    private String leadingWhitespace(String line) {
        return line.substring(0, indentationLevel(line));
    }

    private String quoteYaml(String value) {
        String escaped = value.replace("'", "''");
        return "'" + escaped + "'";
    }

    private boolean isBlockLine(String line, String key) {
        String trimmed = line.trim();
        if (!trimmed.startsWith(key)) {
            return false;
        }
        String remainder = trimmed.substring(key.length()).stripLeading();
        if (!remainder.startsWith(":")) {
            return false;
        }
        String afterColon = remainder.substring(1).stripLeading();
        return afterColon.isEmpty() || afterColon.startsWith("#");
    }

    private boolean isKeyLine(String line, String key) {
        String trimmed = line.trim();
        if (!trimmed.startsWith(key)) {
            return false;
        }
        String remainder = trimmed.substring(key.length()).stripLeading();
        return remainder.startsWith(":");
    }
}
